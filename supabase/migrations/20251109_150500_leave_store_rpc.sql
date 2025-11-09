-- Migration: Create leave_store RPC to allow users to leave a store
-- Owners can only leave if another owner exists

BEGIN;

CREATE OR REPLACE FUNCTION public.leave_store(p_store_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  has_auth_uid BOOLEAN;
  v_user_id UUID;
  v_role TEXT;
  owner_count INTEGER;
BEGIN
  -- Detect schema mapping style for app_users
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  IF has_auth_uid THEN
    SELECT id INTO v_user_id FROM public.app_users WHERE auth_uid = auth.uid();
  ELSE
    SELECT auth.uid() INTO v_user_id;
  END IF;

  -- Confirm membership and role
  SELECT role INTO v_role
  FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_user_id;

  IF v_role IS NULL THEN
    -- Not a member; nothing to do
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

  -- Remove membership
  DELETE FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_user_id;

  RETURN TRUE;
END;
$$;

GRANT EXECUTE ON FUNCTION public.leave_store(UUID) TO authenticated;

ALTER FUNCTION public.leave_store(UUID) OWNER TO postgres;

COMMIT;