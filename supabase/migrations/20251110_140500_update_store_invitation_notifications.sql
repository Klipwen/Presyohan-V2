-- Update invitation RPCs to notify inviter on pending and rejected outcomes
-- Also keep acceptance notifications intact

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
        v_sender_email || ' invited you to join ' || v_store_name || ' as ' || p_role,
        false,
        now()
    );

    -- Pending notification to inviter (sender)
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
        v_target_user_id,
        p_store_id,
        'invitation_pending',
        'Invitation Sent',
        'Your invitation to join ' || v_store_name || ' was sent and is awaiting response',
        false,
        now()
    );
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
BEGIN
    -- Load invitation for current user
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id 
      AND receiver_user_id = auth.uid()
      AND type = 'store_invitation';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Store invitation notification not found';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    -- naive role parse from message
    v_role := 'employee';
    IF v_notification.message LIKE '%as manager%' THEN
        v_role := 'manager';
    END IF;

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

GRANT EXECUTE ON FUNCTION public.send_store_invitation(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.handle_store_invitation(uuid, text) TO authenticated;