-- Migration: Update send_join_request and handle_join_request for multiple owners
-- Date: 2026-07-03

-- 1. Redefine send_join_request (notify owners only, not managers)
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
    has_auth_uid BOOLEAN;
BEGIN
    -- Determine identity model and resolve sender name dynamically
    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    SELECT email INTO v_user_email FROM auth.users WHERE id = auth.uid();

    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_sender_name USING auth.uid();
    ELSE
      SELECT name INTO v_sender_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    -- Get store info (check if store exists)
    SELECT s.name INTO v_store_name
    FROM public.stores s
    WHERE s.id = p_store_id;

    IF v_store_name IS NULL THEN
        RAISE EXCEPTION 'Store not found %', p_store_id;
    END IF;

    -- Check if user is already a member
    IF EXISTS (
        SELECT 1 FROM public.store_members 
        WHERE store_id = p_store_id AND user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'User is already a member of this store';
    END IF;

    -- Insert notification for all store owners (role = 'owner' only)
    INSERT INTO public.notifications (
        receiver_user_id,
        sender_user_id,
        store_id,
        type,
        title,
        message,
        read,
        created_at
    )
    SELECT 
        sm.user_id,
        auth.uid(),
        p_store_id,
        'join_request',
        'Join Request',
        COALESCE(v_sender_name, split_part(COALESCE(v_user_email,''), '@', 1), 'A user') || ' requested to join your store, ' || v_store_name || '.',
        false,
        now()
    FROM public.store_members sm
    WHERE sm.store_id = p_store_id AND sm.role = 'owner';

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
        auth.uid(),
        p_store_id,
        'join_pending',
        'Join Request',
        'You requested to join store, ' || v_store_name || '. Please wait for the owner to respond to your request.',
        false,
        now()
    );
END;
$$;


-- 2. Redefine handle_join_request (notify other owners of handler)
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
    v_handler_name text;
    has_auth_uid BOOLEAN;
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

    -- Resolve identity model
    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    -- Resolve member display name dynamically
    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_member_name USING v_notification.sender_user_id;
    ELSE
      SELECT name INTO v_member_name FROM public.app_users WHERE id = v_notification.sender_user_id LIMIT 1;
    END IF;

    IF v_member_name IS NULL AND has_auth_uid THEN
      SELECT name INTO v_member_name FROM public.app_users WHERE id = v_notification.sender_user_id LIMIT 1;
    END IF;

    IF v_member_name IS NULL THEN
        v_member_name := split_part(COALESCE(v_member_email, 'a user'), '@', 1);
    END IF;

    -- Resolve handling owner's name dynamically
    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_handler_name USING auth.uid();
    ELSE
      SELECT name INTO v_handler_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    IF v_handler_name IS NULL THEN
        v_handler_name := 'An owner';
    END IF;

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
            'Join Request',
            CASE WHEN p_role = 'manager' THEN
                COALESCE(v_store_name, 'Store') || ' accepted your request. You are now a store Manager and can manage their prices.'
            ELSE
                COALESCE(v_store_name, 'Store') || ' accepted your request. You are now a Sales Staff member and can collaborate with their team.'
            END,
            false,
            now()
        );

        -- Update the clicked notification for the handler
        UPDATE public.notifications 
        SET read = true, 
            read_at = now(), 
            type = 'join_accepted',
            message = CASE WHEN p_role = 'manager' THEN
                'You accepted ' || COALESCE(v_member_name, 'a user') || '''s request to join ' || COALESCE(v_store_name, 'Store') || ' as a Manager to manage and update their store prices.'
            ELSE
                'You accepted ' || COALESCE(v_member_name, 'a user') || '''s request to join ' || COALESCE(v_store_name, 'Store') || ' as Sales-Staff to collaborate with your team.'
            END
        WHERE id = p_notification_id;

        -- Update other owners' notifications for this request
        UPDATE public.notifications
        SET read = true,
            read_at = now(),
            type = 'join_accepted',
            message = CASE WHEN p_role = 'manager' THEN
                v_handler_name || ' accepted ' || COALESCE(v_member_name, 'a user') || '''s request to join ' || COALESCE(v_store_name, 'Store') || ' as a Manager to manage and update their store prices.'
            ELSE
                v_handler_name || ' accepted ' || COALESCE(v_member_name, 'a user') || '''s request to join ' || COALESCE(v_store_name, 'Store') || ' as Sales-Staff to collaborate with your team.'
            END
        WHERE store_id = v_notification.store_id 
          AND sender_user_id = v_notification.sender_user_id
          AND type = 'join_request'
          AND id <> p_notification_id;

    ELSE
        -- Notify requester of rejection
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        ) VALUES (
            v_notification.sender_user_id,
            auth.uid(),
            v_notification.store_id,
            'join_rejected',
            'Join Request',
            COALESCE(v_store_name, 'Store') || ' declined your request to join their store team. Feel free to try again later or contact the owner.',
            false,
            now()
        );

        -- Update the clicked notification for the handler
        UPDATE public.notifications 
        SET read = true, 
            read_at = now(), 
            type = 'join_rejected',
            message = 'You declined ' || COALESCE(v_member_name, 'a user') || '''s request to join store, ' || COALESCE(v_store_name, 'Store') || '. You can still invite them anytime'
        WHERE id = p_notification_id;

        -- Update other owners' notifications for this request
        UPDATE public.notifications
        SET read = true,
            read_at = now(),
            type = 'join_rejected',
            message = v_handler_name || ' declined ' || COALESCE(v_member_name, 'a user') || '''s request to join store, ' || COALESCE(v_store_name, 'Store') || '. You can still invite them anytime'
        WHERE store_id = v_notification.store_id 
          AND sender_user_id = v_notification.sender_user_id
          AND type = 'join_request'
          AND id <> p_notification_id;
    END IF;
END;
$$;


-- 3. Redefine send_store_invitation (notify owners on invite sending, and invited user)
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
    v_target_name text;
    v_store_name text;
    v_sender_email text;
    v_sender_name text;
    has_auth_uid BOOLEAN;
    v_owner_rec RECORD;
    v_role_display text;
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
    SELECT id, name INTO v_target_user_id, v_target_name FROM public.app_users WHERE email = p_email;
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
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_sender_name USING auth.uid();
    ELSE
      SELECT name INTO v_sender_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    IF v_sender_name IS NULL THEN
      v_sender_name := split_part(COALESCE(v_sender_email, ''), '@', 1);
    END IF;

    v_role_display := CASE WHEN p_role = 'manager' THEN 'Manager' ELSE 'Sales Staff' END;

    -- Invitation to target user (invitee)
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
        COALESCE(v_sender_name, 'An owner') || ' invited you to join ' || COALESCE(v_store_name, 'Store') || ' as ' || v_role_display || '.',
        false,
        now()
    );

    -- Pending notification to all owners of the store (inviter & other owners)
    FOR v_owner_rec IN 
        SELECT user_id FROM public.store_members WHERE store_id = p_store_id AND role = 'owner'
    LOOP
        IF v_owner_rec.user_id = auth.uid() THEN
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
                v_owner_rec.user_id,
                v_target_user_id,
                p_store_id,
                'invite_pending',
                'Store Invitation Sent',
                'You invited ' || COALESCE(v_target_name, split_part(p_email, '@', 1)) || ' to join on your store ' || COALESCE(v_store_name, 'Store') || ' as a ' || v_role_display || '. Please wait for their response',
                false,
                now()
            );
        ELSE
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
                v_owner_rec.user_id,
                v_target_user_id,
                p_store_id,
                'invite_pending',
                'Store Invitation Sent',
                COALESCE(v_sender_name, 'An owner') || ' invited ' || COALESCE(v_target_name, split_part(p_email, '@', 1)) || ' to join on your store ' || COALESCE(v_store_name, 'Store') || ' as a ' || v_role_display || '. Please wait for their response',
                false,
                now()
            );
        END IF;
    END LOOP;
END;
$$;


-- 4. RPC to cancel join requests securely and bypass RLS
CREATE OR REPLACE FUNCTION public.cancel_join_request(p_notification_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_notification public.notifications%ROWTYPE;
    v_sender_name text;
    v_store_name text;
    has_auth_uid BOOLEAN;
BEGIN
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id
      AND receiver_user_id = auth.uid()
      AND type = 'join_pending';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Pending join request notification not found';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_sender_name USING auth.uid();
    ELSE
      SELECT name INTO v_sender_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    IF v_sender_name IS NULL THEN
        v_sender_name := 'A user';
    END IF;

    -- Update requester's notification
    UPDATE public.notifications
    SET type = 'join_canceled',
        message = 'You canceled your request to join store, ' || COALESCE(v_store_name, 'Store') || '.'
    WHERE id = p_notification_id;

    -- Update owners' notifications
    UPDATE public.notifications
    SET type = 'join_canceled',
        message = v_sender_name || ' canceled the request to join store, ' || COALESCE(v_store_name, 'Store') || '.'
    WHERE store_id = v_notification.store_id
      AND type = 'join_request'
      AND sender_user_id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.cancel_join_request(uuid) TO authenticated;


-- 5. RPC to cancel Suki requests securely and bypass RLS
CREATE OR REPLACE FUNCTION public.cancel_suki_request(p_notification_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_notification public.notifications%ROWTYPE;
    v_sender_name text;
    v_store_name text;
    has_auth_uid BOOLEAN;
BEGIN
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id
      AND receiver_user_id = auth.uid()
      AND type = 'suki_request_sent';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Suki request notification not found';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_sender_name USING auth.uid();
    ELSE
      SELECT name INTO v_sender_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    IF v_sender_name IS NULL THEN
        v_sender_name := 'A user';
    END IF;

    -- Delete the pending suki relationship
    DELETE FROM public.suki_relationships
    WHERE user_id = auth.uid()
      AND store_id = v_notification.store_id
      AND status = 'pending';

    -- Update requester's notification
    UPDATE public.notifications
    SET type = 'suki_request_canceled',
        message = 'You canceled the partnership request to ' || COALESCE(v_store_name, 'QSOS') || '.'
    WHERE id = p_notification_id;

    -- Update manager's notification
    UPDATE public.notifications
    SET type = 'suki_request_canceled',
        message = 'The Suking Tindahan request from ' || v_sender_name || ' was canceled by the user.'
    WHERE store_id = v_notification.store_id
      AND type = 'suki_request_received'
      AND sender_user_id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.cancel_suki_request(uuid) TO authenticated;


-- 6. RPC to cancel store invitations securely and bypass RLS
CREATE OR REPLACE FUNCTION public.cancel_store_invitation(p_notification_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_notification public.notifications%ROWTYPE;
    v_store_name text;
    v_target_name text;
    has_auth_uid BOOLEAN;
    v_role text;
    v_handler_name text;
BEGIN
    SELECT * INTO v_notification
    FROM public.notifications
    WHERE id = p_notification_id
      AND receiver_user_id = auth.uid()
      AND type = 'invite_pending';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Pending invitation notification not found';
    END IF;

    SELECT name INTO v_store_name FROM public.stores WHERE id = v_notification.store_id;

    -- Resolve identity model
    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    -- Resolve the invitee's name dynamically from app_users
    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_target_name USING v_notification.sender_user_id;
    END IF;

    IF v_target_name IS NULL THEN
      SELECT name INTO v_target_name FROM public.app_users WHERE id = v_notification.sender_user_id LIMIT 1;
    END IF;

    IF v_target_name IS NULL THEN
        v_target_name := 'a user';
    END IF;

    -- Resolve canceling owner's name
    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_handler_name USING auth.uid();
    ELSE
      SELECT name INTO v_handler_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    IF v_handler_name IS NULL THEN
        v_handler_name := 'An owner';
    END IF;

    -- Resolve role from pending message
    v_role := 'employee';
    IF v_notification.message LIKE '%as a manager%' OR v_notification.message LIKE '%as manager%' THEN
        v_role := 'manager';
    END IF;

    -- Update inviter's (canceling owner's) notification
    UPDATE public.notifications
    SET type = 'invite_canceled',
        message = 'You canceled the invitation to ' || v_target_name || ' to join your store ' || COALESCE(v_store_name, 'QSOS') || ' as a ' || 
            CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff' END || '.'
    WHERE id = p_notification_id;

    -- Update other owners' pending notifications for this invitation
    UPDATE public.notifications
    SET type = 'invite_canceled',
        message = COALESCE(v_handler_name, 'An owner') || ' canceled the invitation to ' || v_target_name || ' to join your store ' || COALESCE(v_store_name, 'QSOS') || ' as a ' || 
            CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff' END || '.'
    WHERE store_id = v_notification.store_id
      AND sender_user_id = v_notification.sender_user_id
      AND type = 'invite_pending'
      AND id <> p_notification_id;

    -- Update invitee's notification
    UPDATE public.notifications
    SET type = 'invite_canceled',
        message = 'The invitation from ' || COALESCE(v_store_name, 'QSOS') || ' to join their store team was canceled.'
    WHERE store_id = v_notification.store_id
      AND type = 'store_invitation'
      AND receiver_user_id = v_notification.sender_user_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.cancel_store_invitation(uuid) TO authenticated;


-- 7. RPC to delete notifications securely and bypass RLS
CREATE OR REPLACE FUNCTION public.delete_notification(p_notification_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM public.notifications
    WHERE id = p_notification_id
      AND receiver_user_id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_notification(uuid) TO authenticated;

-- 8. RPC to create export notification (bypasses RLS which blocks direct inserts)
CREATE OR REPLACE FUNCTION public.create_export_notification(
    p_user_id uuid,
    p_store_id text,
    p_filename text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_store_uuid uuid;
BEGIN
    -- Only allow a user to create a notification for themselves
    IF auth.uid() != p_user_id THEN
        RAISE EXCEPTION 'Not authorized';
    END IF;

    -- Safely cast store_id (empty string becomes NULL)
    v_store_uuid := CASE WHEN p_store_id = '' THEN NULL ELSE p_store_id::uuid END;

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
        p_user_id,
        p_user_id,
        v_store_uuid,
        'excel_export',
        'Export Complete',
        'Your pricelist Excel file (' || p_filename || ') was successfully generated and exported to your Downloads folder.',
        false,
        now()
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_export_notification(uuid, text, text) TO authenticated;


-- 9. Redefine handle_store_invitation (remove join_confirmed, update states correctly on accept/reject)
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
    v_invitee_name text;
    has_auth_uid BOOLEAN;
    v_inviter_name text;
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

    -- Resolve identity model support
    SELECT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
    ) INTO has_auth_uid;

    -- Resolve invitee's (current user's) display name dynamically
    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_invitee_name USING auth.uid();
    ELSE
      SELECT name INTO v_invitee_name FROM public.app_users WHERE id = auth.uid() LIMIT 1;
    END IF;

    IF v_invitee_name IS NULL THEN
        v_invitee_name := 'A user';
    END IF;

    -- Resolve original inviter's display name dynamically
    IF has_auth_uid THEN
      EXECUTE 'SELECT name FROM public.app_users WHERE auth_uid = $1 LIMIT 1' INTO v_inviter_name USING v_notification.sender_user_id;
    ELSE
      SELECT name INTO v_inviter_name FROM public.app_users WHERE id = v_notification.sender_user_id LIMIT 1;
    END IF;

    IF v_inviter_name IS NULL THEN
        v_inviter_name := 'An owner';
    END IF;

    IF p_action = 'accept' THEN
        INSERT INTO public.store_members (store_id, user_id, role, joined_at)
        VALUES (v_notification.store_id, auth.uid(), v_role, now())
        ON CONFLICT (store_id, user_id) DO NOTHING;

        -- Notify all owners of acceptance
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        )
        SELECT 
            sm.user_id,
            auth.uid(),
            v_notification.store_id,
            'invitation_accepted',
            'Store Invitation',
            CASE 
                WHEN sm.user_id = v_notification.sender_user_id THEN
                    COALESCE(v_invitee_name, 'A user') || ' accepted your invitation as a ' || 
                    CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff member' END || 
                    '. They are now part of your ' || COALESCE(v_store_name, 'Store') || ' team.'
                ELSE
                    COALESCE(v_invitee_name, 'A user') || ' accepted ' || v_inviter_name || '''s invitation as a ' || 
                    CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff member' END || 
                    '. They are now part of your ' || COALESCE(v_store_name, 'Store') || ' team.'
            END,
            false,
            now()
        FROM public.store_members sm
        WHERE sm.store_id = v_notification.store_id AND sm.role = 'owner';

        -- Update the invitee's (current user's) notification to accepted so it persists
        UPDATE public.notifications 
        SET read = true, 
            read_at = now(), 
            type = 'invitation_accepted',
            message = CASE 
                WHEN v_role = 'manager' THEN 'You accepted the invitation as a store Manager for ' || COALESCE(v_store_name, 'Store') || '. You can now manage and update store prices.'
                ELSE 'You accepted the invitation as a Sales Staff for ' || COALESCE(v_store_name, 'Store') || '. You are now part of their team.'
            END
        WHERE id = p_notification_id;

        -- Delete the corresponding pending notifications of this invite for all owners to prevent duplication
        DELETE FROM public.notifications
        WHERE store_id = v_notification.store_id
          AND sender_user_id = auth.uid()
          AND type = 'invite_pending';

    ELSE
        -- Notify all owners of rejection
        INSERT INTO public.notifications (
            receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
        )
        SELECT 
            sm.user_id,
            auth.uid(),
            v_notification.store_id,
            'invitation_rejected',
            'Store Invitation',
            CASE 
                WHEN sm.user_id = v_notification.sender_user_id THEN
                    COALESCE(v_invitee_name, 'A user') || ' declined your invitation as a ' || 
                    CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff' END || 
                    '. You may invite them again anytime.'
                ELSE
                    COALESCE(v_invitee_name, 'A user') || ' declined ' || v_inviter_name || '''s invitation as a ' || 
                    CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff' END || 
                    '. You may invite them again anytime.'
            END,
            false,
            now()
        FROM public.store_members sm
        WHERE sm.store_id = v_notification.store_id AND sm.role = 'owner';

        -- Update invitee's notification to rejected
        UPDATE public.notifications 
        SET read = true, 
            read_at = now(), 
            type = 'invitation_rejected',
            message = 'You declined the invitation from ' || COALESCE(v_store_name, 'Store') || ' to join their team as a ' || 
                CASE WHEN v_role = 'manager' THEN 'Manager' ELSE 'Sales Staff' END || '.'
        WHERE id = p_notification_id;

        -- Delete the corresponding pending notifications of this invite for all owners to prevent duplication
        DELETE FROM public.notifications
        WHERE store_id = v_notification.store_id
          AND sender_user_id = auth.uid()
          AND type = 'invite_pending';
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.handle_store_invitation(uuid, text) TO authenticated;

