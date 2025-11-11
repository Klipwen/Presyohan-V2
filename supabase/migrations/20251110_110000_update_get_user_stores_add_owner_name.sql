-- Migration: Update get_user_stores RPC to include owner_name

-- Drop existing function to allow changing the return type
DROP FUNCTION IF EXISTS public.get_user_stores();

CREATE OR REPLACE FUNCTION public.get_user_stores()
RETURNS TABLE(store_id UUID, name TEXT, branch TEXT, type TEXT, role TEXT, owner_name TEXT)
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
  WITH first_owner AS (
    SELECT
      sm.store_id,
      u.name AS owner_name,
      u.email AS owner_email,
      sm.joined_at,
      ROW_NUMBER() OVER (PARTITION BY sm.store_id ORDER BY sm.joined_at ASC, u.name NULLS LAST) AS rn
    FROM public.store_members sm
    JOIN public.app_users u ON u.id = sm.user_id
    WHERE sm.role = 'owner'
  )
  SELECT s.id AS store_id, s.name, s.branch, s.type, m.role,
         COALESCE(fo.owner_name, split_part(fo.owner_email, '@', 1)) AS owner_name
  FROM public.store_members m
  JOIN public.stores s ON s.id = m.store_id
  LEFT JOIN first_owner fo ON fo.store_id = s.id AND fo.rn = 1
  WHERE m.user_id = v_user_id
  ORDER BY CASE m.role WHEN 'owner' THEN 0 WHEN 'manager' THEN 1 ELSE 2 END, lower(s.name);
END;
$$;

COMMENT ON FUNCTION public.get_user_stores()
IS 'Returns all stores for the current user with branch, type, role, and the first owner name; schema-aware user mapping.';

GRANT EXECUTE ON FUNCTION public.get_user_stores() TO authenticated;
ALTER FUNCTION public.get_user_stores() OWNER TO postgres;