-- Migration: Add send_suki_request RPC to securely request Suki partnership and bypass RLS constraints for notifications.

CREATE OR REPLACE FUNCTION public.send_suki_request(p_store_id uuid)
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

    IF v_sender_name IS NULL OR v_sender_name = '' THEN
        v_sender_name := 'A user';
    END IF;

    -- Get store info
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
        RAISE EXCEPTION 'Staff members cannot partner with their own stores.';
    END IF;

    -- Check if suki_relationship already exists
    IF EXISTS (
        SELECT 1 FROM public.suki_relationships
        WHERE store_id = p_store_id AND user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'A request for this store is already pending or active.';
    END IF;

    -- Insert suki_relationship
    INSERT INTO public.suki_relationships (user_id, store_id, status)
    VALUES (auth.uid(), p_store_id, 'pending');

    -- Insert notifications for store owners and managers (role in 'owner', 'manager')
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
        'suki_request_received',
        'Suki Request',
        v_sender_name || ' requested to connect with your store as a Suki.',
        false,
        NOW()
    FROM public.store_members sm
    WHERE sm.store_id = p_store_id AND sm.role IN ('owner', 'manager');

    -- Insert notification for the requester themselves
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
        'suki_request_sent',
        'Suki Request',
        'You requested to partner with ' || v_store_name || ' as your Suking Tindahan. Please wait for the owner to respond.',
        false,
        NOW()
    );

END;
$$;

GRANT EXECUTE ON FUNCTION public.send_suki_request(uuid) TO authenticated;
