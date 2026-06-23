-- Migration: Allow Multiple Default Starter Stores
-- 1. Drop the unique constraint index that limited stores to a single default/starter store
DROP INDEX IF EXISTS public.unique_default_standard_store;

-- 2. Update the RPC function to only modify the target store without resetting others
CREATE OR REPLACE FUNCTION public.update_standard_store_status(
  p_store_id UUID,
  p_is_public BOOLEAN,
  p_is_default_standard BOOLEAN
)
RETURNS VOID AS $$
BEGIN
  -- Simply update the status of the target store. Multiple starter stores are now allowed.
  UPDATE public.stores
  SET 
    is_public = p_is_public,
    is_default_standard = p_is_default_standard
  WHERE id = p_store_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.update_standard_store_status(UUID, BOOLEAN, BOOLEAN) TO authenticated;
