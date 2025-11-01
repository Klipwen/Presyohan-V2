-- RPC: get_user_categories(store_id) -> returns categories for stores the user has access to
-- Replaces client-side RLS queries with resilient server-side category fetching
-- Handles both identity models (auth_uid vs direct id mapping)

CREATE OR REPLACE FUNCTION public.get_user_categories(p_store_id UUID)
RETURNS TABLE(
  category_id UUID,
  store_id UUID,
  name TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  has_auth_uid BOOLEAN;
  user_has_access BOOLEAN := FALSE;
BEGIN
  -- Detect app_users identity model
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- Check if user has access to this store
  IF has_auth_uid THEN
    -- Legacy model: check via app_users.auth_uid mapping
    SELECT EXISTS (
      SELECT 1 FROM public.store_members sm
      JOIN public.app_users u ON sm.user_id = u.id
      WHERE sm.store_id = p_store_id AND u.auth_uid = auth.uid()
    ) INTO user_has_access;
  ELSE
    -- Simple model: direct auth.uid() mapping
    SELECT EXISTS (
      SELECT 1 FROM public.store_members sm
      WHERE sm.store_id = p_store_id AND sm.user_id = auth.uid()
    ) INTO user_has_access;
  END IF;

  -- Return empty if user has no access
  IF NOT user_has_access THEN
    RETURN;
  END IF;

  -- Return categories for the store
  RETURN QUERY
  SELECT c.id as category_id, c.store_id, c.name
  FROM public.categories c
  WHERE c.store_id = p_store_id
  ORDER BY c.name;
END;
$$;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION public.get_user_categories(UUID) TO authenticated;