-- Fix leave_store name lookup to avoid referencing non-existent app_users.auth_uid
-- Makes the function schema-aware and safe across both identity models

BEGIN;

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
  has_auth_uid BOOLEAN;
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

  -- Determine if app_users has an auth_uid column (legacy mapping)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  IF has_auth_uid THEN
    SELECT COALESCE(u.name, split_part(u.email, '@', 1)) INTO v_user_name
    FROM public.app_users u
    WHERE u.auth_uid = v_user_id
    LIMIT 1;
  ELSE
    SELECT COALESCE(u.name, split_part(u.email, '@', 1)) INTO v_user_name
    FROM public.app_users u
    WHERE u.id = v_user_id
    LIMIT 1;
  END IF;

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

GRANT EXECUTE ON FUNCTION public.leave_store(UUID) TO authenticated;

COMMIT;