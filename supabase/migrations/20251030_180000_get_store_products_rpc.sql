-- RPC: get_store_products(store_id, category_filter, search_query) -> returns products for stores the user has access to
-- Replaces client-side RLS queries with resilient server-side product fetching
-- Handles both identity models and includes server-side filtering for performance

CREATE OR REPLACE FUNCTION public.get_store_products(
  p_store_id UUID,
  p_category_filter TEXT DEFAULT NULL,
  p_search_query TEXT DEFAULT NULL
)
RETURNS TABLE(
  product_id UUID,
  store_id UUID,
  name TEXT,
  description TEXT,
  price NUMERIC,
  units TEXT,
  category TEXT
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

  -- Return products for the store with optional filtering
  RETURN QUERY
  SELECT 
    p.id as product_id, 
    p.store_id, 
    p.name, 
    p.description, 
    p.price, 
    p.units, 
    p.category
  FROM public.products p
  WHERE p.store_id = p_store_id
    AND (p_category_filter IS NULL OR p_category_filter = 'PRICELIST' OR p.category = p_category_filter)
    AND (p_search_query IS NULL OR p_search_query = '' OR (
      p.name ILIKE '%' || p_search_query || '%' OR
      p.description ILIKE '%' || p_search_query || '%' OR
      p.units ILIKE '%' || p_search_query || '%'
    ))
  ORDER BY p.name;
END;
$$;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION public.get_store_products(UUID, TEXT, TEXT) TO authenticated;