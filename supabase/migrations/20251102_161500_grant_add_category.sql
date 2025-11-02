-- Grant execute permission on add_category RPC for authenticated users
-- Fixes: permission denied when calling public.add_category via Postgrest

GRANT EXECUTE ON FUNCTION public.add_category(UUID, TEXT) TO authenticated;