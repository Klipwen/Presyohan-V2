-- Update notification RPCs to use sender display names instead of email addresses
-- Applies to join requests, invitations, member joined/left broadcasts, and leave_store

BEGIN;

-- Function: send_join_request (use sender name)
CREATE OR REPLACE FUNCTION public.send_join_request(p_store_id uuid, p_message text DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_email text;
    v_sender_name text;
    v_store_name text;
    v_owner_id uuid;
    has_auth_uid BOOLEAN;
BEGIN
    -- Determine identity model and resolve sender name
    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    SELECT email INTO v_user_email FROM auth.users WHERE id = auth.uid();

    IF has_auth_uid THEN
      SELECT name INTO v_sender_name FROM public.app_users WHERE auth_uid = auth.uid();
    ELSE
      SELECT name INTO v_sender_name FROM public.app_users WHERE id = auth.uid();
    END IF;

    -- Get store info and owner (owner must exist)
    SELECT s.name, sm.user_id INTO v_store_name, v_owner_id
    FROM public.stores s
    JOIN public.store_members sm ON s.id = sm.store_id
    WHERE s.id = p_store_id AND sm.role = 'owner'
    LIMIT 1;

    IF v_owner_id IS NULL THEN
        RAISE EXCEPTION 'Store owner not found for store %', p_store_id;
    END IF;

    -- Check if user is already a member
    IF EXISTS (
        SELECT 1 FROM public.store_members 
        WHERE store_id = p_store_id AND user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'User is already a member of this store';
    END IF;

    -- Insert notification for store owner
    INSERT INTO public.notifications (
        receiver_user_id,
        sender_user_id,
        store_id,
        type,
        title,
        message,
        read,
        created_at
    ) VALUES (
        v_owner_id,
        auth.uid(),
        p_store_id,
        'join_request',
        'New Join Request',
        COALESCE(p_message, COALESCE(v_sender_name, split_part(COALESCE(v_user_email,''), '@', 1), 'A user') || ' wants to join ' || v_store_name),
        false,
        now()
    );

    -- Also notify the requester that the join request is pending
    INSERT INTO public.notifications (
        receiver_user_id,
        sender_user_id,
        store_id,
        type,
        title,
        message,
        read,
        created_at
    ) VALUES (
        auth.uid(),
        v_owner_id,
        p_store_id,
        'join_pending',
        'Join Request Sent',
        'Your request to join ' || v_store_name || ' is pending owner approval',
        false,
        now()
    );
END;
$$;

-- Function: send_store_invitation (use inviter name)
CREATE OR REPLACE FUNCTION public.send_store_invitation(
    p_store_id uuid,
    p_email text,
    p_role text DEFAULT 'employee'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_target_user_id uuid;
    v_store_name text;
    v_sender_email text;
    v_sender_name text;
    has_auth_uid BOOLEAN;
BEGIN
    -- Ensure inviter has privileges
    IF NOT EXISTS (
        SELECT 1 FROM public.store_members 
        WHERE store_id = p_store_id 
          AND user_id = auth.uid() 
          AND role IN ('owner', 'manager')
    ) THEN
        RAISE EXCEPTION 'Only owners and managers can send invitations';
    END IF;

    -- Resolve target user
    SELECT id INTO v_target_user_id FROM public.app_users WHERE email = p_email;
    IF v_target_user_id IS NULL THEN
        RAISE EXCEPTION 'User with email % not found', p_email;
    END IF;

    -- Prevent inviting existing members
    IF EXISTS (
        SELECT 1 FROM public.store_members 
        WHERE store_id = p_store_id AND user_id = v_target_user_id
    ) THEN
        RAISE EXCEPTION 'User is already a member of this store';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = p_store_id;
    SELECT email INTO v_sender_email FROM auth.users WHERE id = auth.uid();

    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;
    IF has_auth_uid THEN
      SELECT name INTO v_sender_name FROM public.app_users WHERE auth_uid = auth.uid();
    ELSE
      SELECT name INTO v_sender_name FROM public.app_users WHERE id = auth.uid();
    END IF;

    -- Invitation to target user
    INSERT INTO public.notifications (
        receiver_user_id,
        sender_user_id,
        store_id,
        type,
        title,
        message,
        read,
        created_at
    ) VALUES (
        v_target_user_id,
        auth.uid(),
        p_store_id,
        'store_invitation',
        'Store Invitation',
        COALESCE(v_sender_name, split_part(COALESCE(v_sender_email,''), '@', 1)) || ' invited you to join ' || v_store_name || ' as ' || p_role,
        false,
        now()
    );
END;
$$;

-- Function: handle_join_request (broadcast names)
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
    v_member_name text;
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

    -- Resolve member display name from app_users (supports both identity models)
    SELECT COALESCE(
        (SELECT name FROM public.app_users WHERE id = v_notification.sender_user_id LIMIT 1),
        (SELECT name FROM public.app_users WHERE auth_uid = v_notification.sender_user_id LIMIT 1)
    ) INTO v_member_name;

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
                    COALESCE(v_member_name, split_part(COALESCE(v_member_email,''), '@', 1), 'A user') || ' joined ' || v_store_name,
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

-- Function: handle_store_invitation (broadcast names)
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
    v_member_name text;
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

    -- Resolve member display name (the user accepting the invite)
    SELECT COALESCE(
        (SELECT name FROM public.app_users WHERE id = auth.uid() LIMIT 1),
        (SELECT name FROM public.app_users WHERE auth_uid = auth.uid() LIMIT 1)
    ) INTO v_member_name;

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
                    COALESCE(v_member_name, split_part(COALESCE(v_member_email,''), '@', 1), 'A user') || ' joined ' || v_store_name,
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

-- Function: leave_store (broadcast names)
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

  -- Resolve leaving user's display name
  SELECT COALESCE(
    (SELECT name FROM public.app_users WHERE id = v_user_id LIMIT 1),
    (SELECT name FROM public.app_users WHERE auth_uid = v_user_id LIMIT 1)
  ) INTO v_user_name;

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
      COALESCE(v_user_name, split_part(COALESCE(v_user_email,''), '@', 1), 'A user') || ' left ' || v_store_name,
      false,
      now()
    );
  END LOOP;

  RETURN TRUE;
END;
$$;

COMMIT;