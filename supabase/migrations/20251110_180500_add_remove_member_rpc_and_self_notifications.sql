-- Add remove_store_member RPC and add self notifications on join/leave
-- Also update join/invite handlers to notify the joining user

BEGIN;

-- Function: remove_store_member
CREATE OR REPLACE FUNCTION public.remove_store_member(
    p_store_id uuid,
    p_member_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_actor_id uuid;
  v_actor_role text;
  v_target_role text;
  v_store_name text;
  v_actor_name text;
  m RECORD;
BEGIN
  SELECT auth.uid() INTO v_actor_id;

  SELECT role INTO v_actor_role FROM public.store_members WHERE store_id = p_store_id AND user_id = v_actor_id;
  IF v_actor_role IS NULL OR v_actor_role NOT IN ('owner','manager') THEN
    RAISE EXCEPTION 'Insufficient privileges to remove members.';
  END IF;

  SELECT role INTO v_target_role FROM public.store_members WHERE store_id = p_store_id AND user_id = p_member_id;
  IF v_target_role IS NULL THEN
    RAISE EXCEPTION 'Target user is not a member of this store.';
  END IF;
  IF v_target_role = 'owner' THEN
    RAISE EXCEPTION 'Cannot remove an owner from the store.';
  END IF;

  SELECT s.name INTO v_store_name FROM public.stores s WHERE s.id = p_store_id;
  SELECT COALESCE(u.name, split_part(u.email, '@', 1)) INTO v_actor_name FROM public.app_users u WHERE u.id = v_actor_id;

  DELETE FROM public.store_members WHERE store_id = p_store_id AND user_id = p_member_id;

  -- Notify removed user
  INSERT INTO public.notifications (
    receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
  ) VALUES (
    p_member_id,
    v_actor_id,
    p_store_id,
    'member_removed',
    'Removed from Store',
    v_actor_name || ' removed you from ' || v_store_name,
    false,
    now()
  );

  -- Broadcast removal to remaining members
  FOR m IN SELECT user_id FROM public.store_members WHERE store_id = p_store_id LOOP
    INSERT INTO public.notifications (
      receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
    ) VALUES (
      m.user_id,
      v_actor_id,
      p_store_id,
      'member_removed',
      'Member Removed',
      v_actor_name || ' removed a member from ' || v_store_name,
      false,
      now()
    );
  END LOOP;
END;
$$;

GRANT EXECUTE ON FUNCTION public.remove_store_member(uuid, uuid) TO authenticated;


-- Update handle_join_request to notify the joining user of success
CREATE OR REPLACE FUNCTION public.handle_join_request(
    p_notification_id uuid,
    p_action text,
    p_role text DEFAULT 'employee'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_notification public.notifications%ROWTYPE;
    v_store_name text;
BEGIN
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id 
      AND receiver_user_id = auth.uid()
      AND type = 'join_request';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Join request notification not found';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    IF p_action = 'accept' THEN
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, v_notification.sender_user_id, p_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Notify requester (accepted)
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'join_accepted',
            'Join Request Accepted',
            'Your request to join ' || v_store_name || ' has been accepted',
            false,
            now()
        );

        -- Self notification for joining user
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            v_notification.sender_user_id,
            v_notification.sender_user_id,
            v_notification.store_id,
            'join_confirmed',
            'Joined Store',
            'You joined ' || v_store_name || ' as ' || p_role,
            false,
            now()
        );
    ELSE
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'join_rejected',
            'Join Request Rejected',
            'Your request to join ' || v_store_name || ' has been rejected',
            false,
            now()
        );
    END IF;

    UPDATE public.notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;


-- Update handle_store_invitation to add self notification on acceptance
CREATE OR REPLACE FUNCTION public.handle_store_invitation(
    p_notification_id uuid,
    p_action text -- 'accept' or 'reject'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_notification public.notifications%ROWTYPE;
    v_store_name text;
    v_role text;
BEGIN
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id 
      AND receiver_user_id = auth.uid()
      AND type = 'store_invitation';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Store invitation notification not found';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    v_role := 'employee';
    IF v_notification.message LIKE '%as manager%' THEN
        v_role := 'manager';
    END IF;

    IF p_action = 'accept' THEN
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, auth.uid(), v_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Notify inviter
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'invitation_accepted',
            'Invitation Accepted',
            'Your invitation to join ' || v_store_name || ' has been accepted',
            false,
            now()
        );

        -- Self notification for joining user
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            auth.uid(),
            auth.uid(),
            v_notification.store_id,
            'join_confirmed',
            'Joined Store',
            'You joined ' || v_store_name || ' as ' || v_role,
            false,
            now()
        );
    END IF;

    UPDATE public.notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;


-- Update leave_store to add self notification
CREATE OR REPLACE FUNCTION public.leave_store(p_store_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_user_id UUID;
  v_role TEXT;
  owner_count INTEGER;
  v_store_name TEXT;
  v_user_email TEXT;
  v_user_name TEXT;
  m RECORD;
BEGIN
  SELECT auth.uid() INTO v_user_id;

  SELECT role INTO v_role
  FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_user_id;

  IF v_role IS NULL THEN
    RETURN FALSE;
  END IF;

  IF v_role = 'owner' THEN
    SELECT COUNT(*) INTO owner_count
    FROM public.store_members
    WHERE store_id = p_store_id AND role = 'owner';

    IF owner_count <= 1 THEN
      RAISE EXCEPTION 'Cannot leave as sole owner. Transfer ownership first.' USING ERRCODE = 'insufficient_privilege';
    END IF;
  END IF;

  SELECT name INTO v_store_name FROM public.stores WHERE id = p_store_id;
  SELECT email INTO v_user_email FROM auth.users WHERE id = v_user_id;

  SELECT COALESCE(
    (SELECT name FROM public.app_users WHERE id = v_user_id LIMIT 1),
    (SELECT name FROM public.app_users WHERE auth_uid = v_user_id LIMIT 1)
  ) INTO v_user_name;

  DELETE FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_user_id;

  -- Broadcast to remaining members
  FOR m IN SELECT user_id FROM public.store_members WHERE store_id = p_store_id LOOP
    INSERT INTO public.notifications (
      receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
    ) VALUES (
      m.user_id,
      v_user_id,
      p_store_id,
      'member_left',
      'Member Left',
      COALESCE(v_user_name, split_part(COALESCE(v_user_email,''),'@',1), 'A user') || ' left ' || v_store_name,
      false,
      now()
    );
  END LOOP;

  -- Self notification
  INSERT INTO public.notifications (
    receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
  ) VALUES (
    v_user_id,
    v_user_id,
    p_store_id,
    'leave_confirmed',
    'Left Store',
    'You left ' || v_store_name,
    false,
    now()
  );

  RETURN TRUE;
END;
$$;

GRANT EXECUTE ON FUNCTION public.handle_join_request(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.handle_store_invitation(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.leave_store(UUID) TO authenticated;

COMMIT;