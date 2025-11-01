-- Migration: Create get_user_stores RPC to list stores for current user
-- Schema-aware (supports app_users.id = auth.uid OR app_users.auth_uid mapping)

CREATE OR REPLACE FUNCTION public.get_user_stores()
RETURNS TABLE(store_id UUID, name TEXT, branch TEXT, role TEXT)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  has_auth_uid BOOLEAN;
  v_user_id UUID;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;
  

  IF has_auth_uid THEN
    SELECT id INTO v_user_id FROM public.app_users WHERE auth_uid = auth.uid();
  ELSE
    SELECT auth.uid() INTO v_user_id;
  END IF;

  RETURN QUERY
  SELECT s.id AS store_id, s.name, s.branch, m.role
  FROM public.store_members m
  JOIN public.stores s ON s.id = m.store_id
  WHERE m.user_id = v_user_id
  ORDER BY CASE m.role WHEN 'owner' THEN 0 WHEN 'manager' THEN 1 ELSE 2 END, lower(s.name);
END;
$$;

COMMENT ON FUNCTION public.get_user_stores()
IS 'Returns all stores for the current user with role; schema-aware user mapping.';

GRANT EXECUTE ON FUNCTION public.get_user_stores() TO authenticated;
ALTER FUNCTION public.get_user_stores() OWNER TO postgres;