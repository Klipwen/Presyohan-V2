-- Notification Management RPCs
-- Provides secure functions for mobile clients to manage notifications and invites

-- Function to mark notifications as read
CREATE OR REPLACE FUNCTION mark_notifications_read(p_notification_ids uuid[])
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Update notifications to read=true and set read_at timestamp
    -- Only allow users to mark their own notifications as read
    UPDATE notifications 
    SET 
        read = true,
        read_at = now()
    WHERE 
        id = ANY(p_notification_ids)
        AND receiver_user_id = auth.uid()::text;
END;
$$;

-- Function to get store by invite code (for non-members)
CREATE OR REPLACE FUNCTION get_store_by_invite_code(p_invite_code text)
RETURNS TABLE(
    store_id uuid,
    name text,
    branch text,
    type text,
    invite_code_created_at timestamp
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Check if invite code exists and is not expired (24 hours)
    RETURN QUERY
    SELECT 
        s.id,
        s.name,
        s.branch,
        s.type,
        s.invite_code_created_at
    FROM stores s
    WHERE 
        s.invite_code = p_invite_code
        AND s.invite_code_created_at IS NOT NULL
        AND s.invite_code_created_at > (now() - interval '24 hours');
END;
$$;

-- Function to send join request notification
CREATE OR REPLACE FUNCTION send_join_request(p_store_id uuid, p_message text DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_email text;
    v_user_name text;
    v_store_name text;
    v_owner_id text;
BEGIN
    -- Get current user info
    SELECT email INTO v_user_email FROM auth.users WHERE id = auth.uid();
    SELECT email INTO v_user_name FROM app_users WHERE id = auth.uid()::text;
    
    -- Get store info and owner
    SELECT s.name, sm.user_id INTO v_store_name, v_owner_id
    FROM stores s
    JOIN store_members sm ON s.id = sm.store_id
    WHERE s.id = p_store_id AND sm.role = 'owner'
    LIMIT 1;
    
    -- Check if user is already a member
    IF EXISTS (
        SELECT 1 FROM store_members 
        WHERE store_id = p_store_id AND user_id = auth.uid()::text
    ) THEN
        RAISE EXCEPTION 'User is already a member of this store';
    END IF;
    
    -- Insert notification for store owner
    INSERT INTO notifications (
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
        auth.uid()::text,
        p_store_id,
        'join_request',
        'New Join Request',
        COALESCE(p_message, v_user_email || ' wants to join ' || v_store_name),
        false,
        now()
    );
END;
$$;

-- Function to accept/reject join request
CREATE OR REPLACE FUNCTION handle_join_request(
    p_notification_id uuid,
    p_action text, -- 'accept' or 'reject'
    p_role text DEFAULT 'employee'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_notification notifications%ROWTYPE;
    v_store_name text;
BEGIN
    -- Get notification details (must be for current user)
    SELECT * INTO v_notification
    FROM notifications
    WHERE id = p_notification_id 
    AND receiver_user_id = auth.uid()::text
    AND type = 'join_request';
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Join request notification not found';
    END IF;
    
    -- Get store name
    SELECT name INTO v_store_name FROM stores WHERE id = v_notification.store_id;
    
    IF p_action = 'accept' THEN
        -- Add user to store_members
        INSERT INTO store_members (store_id, user_id, role, created_at)
        VALUES (v_notification.store_id, v_notification.sender_user_id, p_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;
        
        -- Send acceptance notification to requester
        INSERT INTO notifications (
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
            auth.uid()::text,
            v_notification.store_id,
            'join_accepted',
            'Join Request Accepted',
            'Your request to join ' || v_store_name || ' has been accepted',
            false,
            now()
        );
    ELSE
        -- Send rejection notification to requester
        INSERT INTO notifications (
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
            auth.uid()::text,
            v_notification.store_id,
            'join_rejected',
            'Join Request Rejected',
            'Your request to join ' || v_store_name || ' has been rejected',
            false,
            now()
        );
    END IF;
    
    -- Mark original notification as read
    UPDATE notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;

-- Function to send store invitation
CREATE OR REPLACE FUNCTION send_store_invitation(
    p_store_id uuid,
    p_email text,
    p_role text DEFAULT 'employee'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_target_user_id text;
    v_store_name text;
    v_sender_email text;
BEGIN
    -- Check if current user is owner/manager of the store
    IF NOT EXISTS (
        SELECT 1 FROM store_members 
        WHERE store_id = p_store_id 
        AND user_id = auth.uid()::text 
        AND role IN ('owner', 'manager')
    ) THEN
        RAISE EXCEPTION 'Only owners and managers can send invitations';
    END IF;
    
    -- Get target user ID by email
    SELECT id INTO v_target_user_id FROM app_users WHERE email = p_email;
    
    IF v_target_user_id IS NULL THEN
        RAISE EXCEPTION 'User with email % not found', p_email;
    END IF;
    
    -- Check if user is already a member
    IF EXISTS (
        SELECT 1 FROM store_members 
        WHERE store_id = p_store_id AND user_id = v_target_user_id
    ) THEN
        RAISE EXCEPTION 'User is already a member of this store';
    END IF;
    
    -- Get store and sender info
    SELECT name INTO v_store_name FROM stores WHERE id = p_store_id;
    SELECT email INTO v_sender_email FROM auth.users WHERE id = auth.uid();
    
    -- Insert invitation notification
    INSERT INTO notifications (
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
        auth.uid()::text,
        p_store_id,
        'store_invitation',
        'Store Invitation',
        v_sender_email || ' invited you to join ' || v_store_name || ' as ' || p_role,
        false,
        now()
    );
END;
$$;

-- Function to accept/reject store invitation
CREATE OR REPLACE FUNCTION handle_store_invitation(
    p_notification_id uuid,
    p_action text -- 'accept' or 'reject'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_notification notifications%ROWTYPE;
    v_store_name text;
    v_role text;
BEGIN
    -- Get notification details (must be for current user)
    SELECT * INTO v_notification
    FROM notifications
    WHERE id = p_notification_id 
    AND receiver_user_id = auth.uid()::text
    AND type = 'store_invitation';
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Store invitation notification not found';
    END IF;
    
    -- Get store name
    SELECT name INTO v_store_name FROM stores WHERE id = v_notification.store_id;
    
    -- Extract role from message (simple parsing)
    v_role := 'employee'; -- default
    IF v_notification.message LIKE '%as manager%' THEN
        v_role := 'manager';
    END IF;
    
    IF p_action = 'accept' THEN
        -- Add user to store_members
        INSERT INTO store_members (store_id, user_id, role, created_at)
        VALUES (v_notification.store_id, auth.uid()::text, v_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;
        
        -- Send acceptance notification to inviter
        INSERT INTO notifications (
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
            auth.uid()::text,
            v_notification.store_id,
            'invitation_accepted',
            'Invitation Accepted',
            'Your invitation to join ' || v_store_name || ' has been accepted',
            false,
            now()
        );
    END IF;
    
    -- Mark original notification as read regardless of action
    UPDATE notifications 
    SET read = true, read_at = now()
    WHERE id = p_notification_id;
END;
$$;

-- Grant execute permissions to authenticated users
GRANT EXECUTE ON FUNCTION mark_notifications_read(uuid[]) TO authenticated;
GRANT EXECUTE ON FUNCTION get_store_by_invite_code(text) TO authenticated;
GRANT EXECUTE ON FUNCTION send_join_request(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION handle_join_request(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION send_store_invitation(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION handle_store_invitation(uuid, text) TO authenticated;