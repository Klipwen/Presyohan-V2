-- Migration: Atomic RPC to Update Standard Store Status
-- Performs updates inside a single transaction to prevent unique constraint violations.

CREATE OR REPLACE FUNCTION public.update_standard_store_status(
  p_store_id UUID,
  p_is_public BOOLEAN,
  p_is_default_standard BOOLEAN
)
RETURNS VOID AS $$
BEGIN
  -- 1. If setting as default starter store, clear other defaults first
  IF p_is_default_standard THEN
    UPDATE public.stores
    SET 
      is_default_standard = FALSE,
      is_public = TRUE
    WHERE is_default_standard = TRUE AND id <> p_store_id;
  END IF;

  -- 2. Update the target store status
  UPDATE public.stores
  SET 
    is_public = p_is_public,
    is_default_standard = p_is_default_standard
  WHERE id = p_store_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Grant execution to authenticated users (admin checks are done in the app / RLS policies,
-- but the function itself is SECURITY DEFINER).
GRANT EXECUTE ON FUNCTION public.update_standard_store_status(UUID, BOOLEAN, BOOLEAN) TO authenticated;
