-- Fix notification RPCs to correctly use UUID types and avoid type mismatches
-- Ensures join requests and invitations work reliably for mobile clients

-- Function: send_join_request
CREATE OR REPLACE FUNCTION public.send_join_request(p_store_id uuid, p_message text DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_email text;
    v_store_name text;
    v_owner_id uuid;
BEGIN
    -- Get current user email (for message content)
    SELECT email INTO v_user_email FROM auth.users WHERE id = auth.uid();

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
        COALESCE(p_message, COALESCE(v_user_email, 'A user') || ' wants to join ' || v_store_name),
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

-- Function: handle_join_request
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
    -- Get notification details (must be for current user and of correct type)
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id 
      AND receiver_user_id = auth.uid()
      AND type = 'join_request';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Join request notification not found';
    END IF;

    -- Get store name
    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    IF p_action = 'accept' THEN
        -- Add user to store_members
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, v_notification.sender_user_id, p_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Send acceptance notification to requester
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
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'join_accepted',
            'Join Request Accepted',
            'Your request to join ' || v_store_name || ' has been accepted',
            false,
            now()
        );
    ELSE
        -- Send rejection notification to requester
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

    -- Mark original notification as read
    UPDATE public.notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;

-- Function: send_store_invitation
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
BEGIN
    -- Check if current user is owner/manager of the store
    IF NOT EXISTS (
        SELECT 1 FROM public.store_members 
        WHERE store_id = p_store_id 
          AND user_id = auth.uid() 
          AND role IN ('owner', 'manager')
    ) THEN
        RAISE EXCEPTION 'Only owners and managers can send invitations';
    END IF;

    -- Get target user ID by email
    SELECT id INTO v_target_user_id FROM public.app_users WHERE email = p_email;

    IF v_target_user_id IS NULL THEN
        RAISE EXCEPTION 'User with email % not found', p_email;
    END IF;

    -- Check if user is already a member
    IF EXISTS (
        SELECT 1 FROM public.store_members 
        WHERE store_id = p_store_id AND user_id = v_target_user_id
    ) THEN
        RAISE EXCEPTION 'User is already a member of this store';
    END IF;

    -- Get store and sender info
    SELECT name INTO v_store_name FROM public.stores WHERE id = p_store_id;
    SELECT email INTO v_sender_email FROM auth.users WHERE id = auth.uid();

    -- Insert invitation notification
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
        v_sender_email || ' invited you to join ' || v_store_name || ' as ' || p_role,
        false,
        now()
    );
END;
$$;

-- Function: handle_store_invitation
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
    -- Get notification details (must be for current user)
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id 
      AND receiver_user_id = auth.uid()
      AND type = 'store_invitation';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Store invitation notification not found';
    END IF;

    -- Get store name
    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    -- Extract role from message (simple parsing)
    v_role := 'employee'; -- default
    IF v_notification.message LIKE '%as manager%' THEN
        v_role := 'manager';
    END IF;

    IF p_action = 'accept' THEN
        -- Add user to store_members
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, auth.uid(), v_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Send acceptance notification to inviter
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
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'invitation_accepted',
            'Invitation Accepted',
            'Your invitation to join ' || v_store_name || ' has been accepted',
            false,
            now()
        );
    END IF;

    -- Mark original notification as read regardless of action
    UPDATE public.notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;

-- Re-grant execute permissions to authenticated users (idempotent)
GRANT EXECUTE ON FUNCTION public.send_join_request(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.handle_join_request(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.send_store_invitation(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.handle_store_invitation(uuid, text) TO authenticated;