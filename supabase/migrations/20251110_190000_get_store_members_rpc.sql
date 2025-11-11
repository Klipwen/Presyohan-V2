-- RPC: get_store_members(store_id) -> list all members with names/emails for authorized users
-- Schema-aware identity resolution; allows any store member to view the full roster.

BEGIN;

CREATE OR REPLACE FUNCTION public.get_store_members(p_store_id UUID)
RETURNS TABLE(
  user_id UUID,
  role TEXT,
  name TEXT,
  email TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  has_auth_uid BOOLEAN;
  v_user_id UUID;
  user_has_access BOOLEAN := FALSE;
BEGIN
  -- Detect app_users identity model
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- Resolve current user id in app_users
  IF has_auth_uid THEN
    SELECT id INTO v_user_id FROM public.app_users WHERE auth_uid = auth.uid();
  ELSE
    SELECT auth.uid() INTO v_user_id;
  END IF;

  -- Check the caller is a member of the target store
  SELECT EXISTS (
    SELECT 1 FROM public.store_members sm
    WHERE sm.store_id = p_store_id AND sm.user_id = v_user_id
  ) INTO user_has_access;

  IF NOT user_has_access THEN
    RAISE EXCEPTION 'Access denied: not a member of this store' USING ERRCODE = 'insufficient_privilege';
  END IF;

  -- Return all members with display name fallback
  RETURN QUERY
  SELECT m.user_id,
         m.role,
         COALESCE(u.name, split_part(u.email, '@', 1)) AS name,
         COALESCE(u.email, '') AS email
  FROM public.store_members m
  LEFT JOIN public.app_users u ON u.id = m.user_id
  WHERE m.store_id = p_store_id
  ORDER BY CASE m.role WHEN 'owner' THEN 0 WHEN 'manager' THEN 1 ELSE 2 END,
           lower(COALESCE(u.name, split_part(u.email, '@', 1)));
END;
$$;

COMMENT ON FUNCTION public.get_store_members(UUID)
IS 'Returns all members for a store (role, display name, email) for authorized members; schema-aware.';

GRANT EXECUTE ON FUNCTION public.get_store_members(UUID) TO authenticated;

COMMIT;