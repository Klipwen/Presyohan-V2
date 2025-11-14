-- Update copy_prices RPC to follow Source -> Destination via paste-code
-- New semantics: caller selects items from their source store, enters a
-- destination store paste-code, and the function copies prices accordingly.

BEGIN;

-- Drop old signature to allow renaming parameters cleanly
DROP FUNCTION IF EXISTS public.copy_prices(UUID, TEXT, UUID[], BOOLEAN);

-- Redefine copy_prices with new parameter order and logic
CREATE OR REPLACE FUNCTION public.copy_prices(
  p_source_store_id UUID,
  p_dest_paste_code TEXT,
  p_items UUID[],
  p_dry_run BOOLEAN DEFAULT FALSE
)
RETURNS TABLE(product_id UUID, name TEXT, source_price NUMERIC, dest_price NUMERIC, action TEXT)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_dest_store_id UUID;
  v_is_owner BOOLEAN := FALSE;
  v_item UUID;
  v_name TEXT;
  v_desc TEXT;
  v_unit TEXT;
  v_category_id UUID;
  v_category_name TEXT;
  v_source_price NUMERIC;
  v_existing_dest UUID;
  v_dest_price NUMERIC;
BEGIN
  -- Resolve destination from paste-code
  SELECT s.id INTO v_dest_store_id
  FROM public.stores s
  WHERE s.paste_code = p_dest_paste_code AND s.paste_code_expires_at > now()
  LIMIT 1;
  IF v_dest_store_id IS NULL THEN
    RAISE EXCEPTION 'Invalid or expired paste-code' USING ERRCODE = 'invalid_parameter_value';
  END IF;

  -- Owner check on source store (caller must own the source store)
  SELECT EXISTS (
    SELECT 1 FROM public.store_members m
    WHERE m.store_id = p_source_store_id AND m.user_id = auth.uid() AND m.role = 'owner'
  ) INTO v_is_owner;
  IF NOT v_is_owner THEN
    RAISE EXCEPTION 'Only source store owner can copy prices' USING ERRCODE = 'insufficient_privilege';
  END IF;

  -- Iterate selected items
  FOREACH v_item IN ARRAY p_items LOOP
    -- Get source product details
    SELECT p.name, p.description, p.unit, p.price, c.id, COALESCE(c.name,'')
    INTO v_name, v_desc, v_unit, v_source_price, v_category_id, v_category_name
    FROM public.products p
    LEFT JOIN public.categories c ON c.id = p.category_id
    WHERE p.id = v_item AND p.store_id = p_source_store_id;

    IF v_name IS NULL THEN
      CONTINUE;
    END IF;

    -- Find destination product by name AND description (do NOT merge if description differs)
    SELECT id, price INTO v_existing_dest, v_dest_price
    FROM public.products dp
    WHERE dp.store_id = v_dest_store_id
      AND lower(dp.name) = lower(v_name)
      AND COALESCE(dp.description, '') = COALESCE(v_desc, '')
    LIMIT 1;

    IF p_dry_run THEN
      IF v_existing_dest IS NULL THEN
        RETURN QUERY SELECT v_item, v_name, v_source_price, NULL::NUMERIC, 'create';
      ELSE
        RETURN QUERY SELECT v_item, v_name, v_source_price, v_dest_price, 'update';
      END IF;
    ELSE
      IF v_existing_dest IS NULL THEN
        -- Ensure destination category exists by name
        IF v_category_name IS NOT NULL AND v_category_name <> '' THEN
          PERFORM * FROM public.add_category(v_dest_store_id, v_category_name);
        END IF;
        -- Insert product with source price into destination
        INSERT INTO public.products (store_id, category_id, name, description, price, unit)
        VALUES (
          v_dest_store_id,
          (SELECT id FROM public.categories c WHERE c.store_id = v_dest_store_id AND lower(c.name) = lower(v_category_name) LIMIT 1),
          v_name,
          v_desc,
          v_source_price,
          v_unit
        );
        RETURN QUERY SELECT v_item, v_name, v_source_price, NULL::NUMERIC, 'create';
      ELSE
        UPDATE public.products SET price = v_source_price WHERE id = v_existing_dest;
        RETURN QUERY SELECT v_item, v_name, v_source_price, v_dest_price, 'update';
      END IF;
    END IF;
  END LOOP;
END;
$$;
GRANT EXECUTE ON FUNCTION public.copy_prices(UUID, TEXT, UUID[], BOOLEAN) TO authenticated;

COMMIT;