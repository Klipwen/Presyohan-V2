-- RPC: create_store(name, branch, type) -> returns store id
-- Inserts a new row into public.stores and makes the caller the owner
-- via public.store_members. SECURITY DEFINER to bypass RLS safely.

CREATE OR REPLACE FUNCTION public.create_store(p_name TEXT, p_branch TEXT, p_type TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_store_id UUID;
BEGIN
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RAISE EXCEPTION 'Store name is required';
  END IF;

  INSERT INTO public.stores (name, branch, type)
  VALUES (trim(p_name), NULLIF(trim(p_branch), ''), NULLIF(trim(p_type), ''))
  RETURNING id INTO v_store_id;

  INSERT INTO public.store_members (store_id, user_id, role)
  VALUES (v_store_id, auth.uid(), 'owner');

  RETURN v_store_id;
END;
$$;

COMMENT ON FUNCTION public.create_store(TEXT, TEXT, TEXT)
IS 'Creates a store and assigns the caller as owner; returns store id.';