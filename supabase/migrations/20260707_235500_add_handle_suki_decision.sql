-- Migration: Add handle_suki_decision RPC to securely accept or reject Suki requests bypassing RLS for related updates.

CREATE OR REPLACE FUNCTION public.handle_suki_decision(p_notification_id uuid, p_action text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_notification public.notifications%ROWTYPE;
    v_handler_name text;
    v_sender_name text;
    v_store_name text;
BEGIN
    -- 1. Get notification info
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Notification not found %', p_notification_id;
    END IF;

    -- 2. Verify handler role
    IF NOT EXISTS (
        SELECT 1 FROM public.store_members
        WHERE store_id = v_notification.store_id 
          AND user_id = auth.uid() 
          AND role IN ('owner', 'manager')
    ) THEN
        RAISE EXCEPTION 'Only store owners or managers can make Suki decisions.';
    END IF;

    -- 3. Resolve handler and sender names
    SELECT COALESCE(name, 'A manager') INTO v_handler_name 
    FROM public.app_users WHERE id = auth.uid();
    
    SELECT COALESCE(name, 'A user') INTO v_sender_name 
    FROM public.app_users WHERE id = v_notification.sender_user_id;

    SELECT name INTO v_store_name 
    FROM public.stores WHERE id = v_notification.store_id;

    -- 4. Process action
    IF p_action = 'accept' THEN
        -- Activate relationship
        UPDATE public.suki_relationships
        SET status = 'active'
        WHERE user_id = v_notification.sender_user_id AND store_id = v_notification.store_id;

        -- Update notification of handler
        UPDATE public.notifications
        SET type = 'suki_accepted',
            message = 'You accepted ' || v_sender_name || '''s request. They can now view your public prices as a Suki.'
        WHERE id = p_notification_id;

        -- Update other managers' notifications
        UPDATE public.notifications
        SET type = 'suki_accepted',
            message = v_handler_name || ' accepted ' || v_sender_name || '''s request. They can now view public prices as a Suki.'
        WHERE store_id = v_notification.store_id
          AND type = 'suki_request_received'
          AND sender_user_id = v_notification.sender_user_id
          AND id <> p_notification_id;

        -- Update requester's notification
        UPDATE public.notifications
        SET type = 'suki_accepted',
            message = 'You are now partnered with ' || COALESCE(v_store_name, 'the store') || '. You can now view all their public prices.'
        WHERE store_id = v_notification.store_id
          AND type = 'suki_request_sent'
          AND receiver_user_id = v_notification.sender_user_id;

    ELSIF p_action = 'reject' THEN
        -- Delete relationship
        DELETE FROM public.suki_relationships
        WHERE user_id = v_notification.sender_user_id AND store_id = v_notification.store_id;

        -- Update notification of handler
        UPDATE public.notifications
        SET type = 'suki_rejected',
            message = 'You declined the Suking Tindahan request from ' || v_sender_name || '.'
        WHERE id = p_notification_id;

        -- Update other managers' notifications
        UPDATE public.notifications
        SET type = 'suki_rejected',
            message = v_handler_name || ' declined the Suking Tindahan request from ' || v_sender_name || '.'
        WHERE store_id = v_notification.store_id
          AND type = 'suki_request_received'
          AND sender_user_id = v_notification.sender_user_id
          AND id <> p_notification_id;

        -- Update requester's notification
        UPDATE public.notifications
        SET type = 'suki_rejected',
            message = COALESCE(v_store_name, 'The store') || ' declined your request to partner as a Suking Tindahan. You can try again later.'
        WHERE store_id = v_notification.store_id
          AND type = 'suki_request_sent'
          AND receiver_user_id = v_notification.sender_user_id;

    ELSE
        RAISE EXCEPTION 'Unknown action %', p_action;
    END IF;

END;
$$;

GRANT EXECUTE ON FUNCTION public.handle_suki_decision(uuid, text) TO authenticated;
