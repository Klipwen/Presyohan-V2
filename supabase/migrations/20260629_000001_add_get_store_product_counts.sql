-- Migration: Add get_store_product_counts RPC to return accurate product counts
CREATE OR REPLACE FUNCTION public.get_store_product_counts(p_store_ids UUID[])
RETURNS TABLE(store_id UUID, total_count INT, public_count INT)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  RETURN QUERY
  SELECT 
    s_id AS store_id,
    COALESCE(COUNT(p.id), 0)::INT AS total_count,
    COALESCE(SUM(CASE WHEN p.is_public = TRUE THEN 1 ELSE 0 END), 0)::INT AS public_count
  FROM unnest(p_store_ids) AS s_id
  LEFT JOIN public.products p ON p.store_id = s_id
  GROUP BY s_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_store_product_counts(UUID[]) TO authenticated;
