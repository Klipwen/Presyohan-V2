-- Update validate_paste_code to also return branch and type for the store card display in the Copy Prices dialog

DROP FUNCTION IF EXISTS public.validate_paste_code(TEXT);

CREATE OR REPLACE FUNCTION public.validate_paste_code(p_code TEXT)
RETURNS TABLE(store_id UUID, store_name TEXT, store_branch TEXT, store_type TEXT)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  RETURN QUERY
  SELECT s.id, s.name, s.branch, s.type
  FROM public.stores s
  WHERE s.paste_code = p_code AND s.paste_code_expires_at > now()
  LIMIT 1;
END;
$$;

GRANT EXECUTE ON FUNCTION public.validate_paste_code(TEXT) TO authenticated;
