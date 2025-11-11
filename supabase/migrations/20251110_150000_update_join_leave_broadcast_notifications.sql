-- Broadcast notifications to store members when a user joins or leaves
-- Update join/invite handlers to send 'member_joined' to existing members
-- Update leave_store to send 'member_left' to remaining members

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
    v_member_email text;
    m RECORD;
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
    SELECT email INTO v_member_email FROM auth.users WHERE id = v_notification.sender_user_id;

    IF p_action = 'accept' THEN
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, v_notification.sender_user_id, p_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Notify requester
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

        -- Broadcast to existing members that a new member joined
        FOR m IN SELECT user_id FROM public.store_members WHERE store_id = v_notification.store_id LOOP
            IF m.user_id <> v_notification.sender_user_id THEN
                INSERT INTO public.notifications (
                    receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
                ) VALUES (
                    m.user_id,
                    v_notification.sender_user_id,
                    v_notification.store_id,
                    'member_joined',
                    'Member Joined',
                    COALESCE(v_member_email, 'A user') || ' joined ' || v_store_name,
                    false,
                    now()
                );
            END IF;
        END LOOP;
    ELSE
        -- Notify requester of rejection
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
    v_member_email text;
    m RECORD;
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

    SELECT email INTO v_member_email FROM auth.users WHERE id = auth.uid();

    IF p_action = 'accept' THEN
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, auth.uid(), v_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Notify inviter: accepted
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

        -- Broadcast to existing members
        FOR m IN SELECT user_id FROM public.store_members WHERE store_id = v_notification.store_id LOOP
            IF m.user_id <> auth.uid() THEN
                INSERT INTO public.notifications (
                    receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
                ) VALUES (
                    m.user_id,
                    auth.uid(),
                    v_notification.store_id,
                    'member_joined',
                    'Member Joined',
                    COALESCE(v_member_email, 'A user') || ' joined ' || v_store_name,
                    false,
                    now()
                );
            END IF;
        END LOOP;
    ELSE
        -- Notify inviter: rejected
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'invitation_rejected',
            'Invitation Rejected',
            'Your invitation to join ' || v_store_name || ' has been declined',
            false,
            now()
        );
    END IF;

    UPDATE public.notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;

-- Update leave_store to broadcast member_left
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

  -- Remove membership
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
      COALESCE(v_user_email, 'A user') || ' left ' || v_store_name,
      false,
      now()
    );
  END LOOP;

  RETURN TRUE;
END;
$$;

GRANT EXECUTE ON FUNCTION public.handle_join_request(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.handle_store_invitation(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.leave_store(UUID) TO authenticated;