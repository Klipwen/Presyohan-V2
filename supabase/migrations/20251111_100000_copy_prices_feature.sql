-- Copy Prices Feature: paste-code columns, validation RPC, and copy_prices RPC
-- Note: Initial implementation stores paste_code as plain text for simplicity.
--       You can harden to HMAC using pgcrypto in a follow-up migration.

BEGIN;

-- 1) Add paste_code and expiry to stores
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'stores' AND column_name = 'paste_code'
  ) THEN
    ALTER TABLE public.stores ADD COLUMN paste_code TEXT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'stores' AND column_name = 'paste_code_expires_at'
  ) THEN
    ALTER TABLE public.stores ADD COLUMN paste_code_expires_at TIMESTAMPTZ;
  END IF;

  -- Partial index for quick validation lookup
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'stores' AND indexname = 'idx_stores_paste_code_active'
  ) THEN
    -- NOTE: Partial index predicates must be IMMUTABLE. `now()` is VOLATILE and
    -- cannot be used in index predicates. We index non-null paste_code only;
    -- the query will still filter by expiry at runtime.
    CREATE INDEX idx_stores_paste_code_active ON public.stores(paste_code)
      WHERE paste_code IS NOT NULL;
  END IF;
END $$;

-- 2) RPC: Generate Paste-Code (owner-only)
CREATE OR REPLACE FUNCTION public.generate_paste_code(p_store_id UUID)
RETURNS TABLE(code TEXT, expires_at TIMESTAMPTZ)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_is_owner BOOLEAN := FALSE;
  v_code TEXT;
BEGIN
  -- Owner check
  SELECT EXISTS (
    SELECT 1 FROM public.store_members m
    WHERE m.store_id = p_store_id AND m.user_id = auth.uid() AND m.role = 'owner'
  ) INTO v_is_owner;
  IF NOT v_is_owner THEN
    RAISE EXCEPTION 'Only owner can generate paste-code' USING ERRCODE = 'insufficient_privilege';
  END IF;

  -- Generate 6-digit code (random)
  v_code := lpad((floor(random()*1000000))::INT::TEXT, 6, '0');

  UPDATE public.stores
    SET paste_code = v_code,
        paste_code_expires_at = now() + interval '24 hours'
    WHERE id = p_store_id;

  RETURN QUERY SELECT v_code, (now() + interval '24 hours');
END;
$$;
GRANT EXECUTE ON FUNCTION public.generate_paste_code(UUID) TO authenticated;

-- 3) RPC: Revoke Paste-Code (owner-only)
CREATE OR REPLACE FUNCTION public.revoke_paste_code(p_store_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE v_is_owner BOOLEAN := FALSE;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM public.store_members m
    WHERE m.store_id = p_store_id AND m.user_id = auth.uid() AND m.role = 'owner'
  ) INTO v_is_owner;
  IF NOT v_is_owner THEN
    RAISE EXCEPTION 'Only owner can revoke paste-code' USING ERRCODE = 'insufficient_privilege';
  END IF;

  UPDATE public.stores
    SET paste_code = NULL,
        paste_code_expires_at = NULL
    WHERE id = p_store_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.revoke_paste_code(UUID) TO authenticated;

-- 4) RPC: Validate paste-code → return source store
CREATE OR REPLACE FUNCTION public.validate_paste_code(p_code TEXT)
RETURNS TABLE(store_id UUID, store_name TEXT)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  RETURN QUERY
  SELECT s.id, s.name
  FROM public.stores s
  WHERE s.paste_code = p_code AND s.paste_code_expires_at > now()
  LIMIT 1;
END;
$$;
GRANT EXECUTE ON FUNCTION public.validate_paste_code(TEXT) TO authenticated;

-- 5) RPC: Copy prices (dry-run or apply)
CREATE OR REPLACE FUNCTION public.copy_prices(
  p_dest_store_id UUID,
  p_paste_code TEXT,
  p_items UUID[],
  p_dry_run BOOLEAN DEFAULT FALSE
)
RETURNS TABLE(product_id UUID, name TEXT, source_price NUMERIC, dest_price NUMERIC, action TEXT)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_source_store_id UUID;
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
  -- Resolve source from code
  SELECT s.id INTO v_source_store_id
  FROM public.stores s
  WHERE s.paste_code = p_paste_code AND s.paste_code_expires_at > now()
  LIMIT 1;
  IF v_source_store_id IS NULL THEN
    RAISE EXCEPTION 'Invalid or expired paste-code' USING ERRCODE = 'invalid_parameter_value';
  END IF;

  -- Owner check on destination
  SELECT EXISTS (
    SELECT 1 FROM public.store_members m
    WHERE m.store_id = p_dest_store_id AND m.user_id = auth.uid() AND m.role = 'owner'
  ) INTO v_is_owner;
  IF NOT v_is_owner THEN
    RAISE EXCEPTION 'Only owner can copy prices to this store' USING ERRCODE = 'insufficient_privilege';
  END IF;

  -- Iterate items
  FOREACH v_item IN ARRAY p_items LOOP
    -- Get source product details
    SELECT p.name, p.description, p.unit, p.price, c.id, COALESCE(c.name,'')
    INTO v_name, v_desc, v_unit, v_source_price, v_category_id, v_category_name
    FROM public.products p
    LEFT JOIN public.categories c ON c.id = p.category_id
    WHERE p.id = v_item AND p.store_id = v_source_store_id;

    IF v_name IS NULL THEN
      -- Skip if not found
      CONTINUE;
    END IF;

    -- Find destination product by name (case-insensitive)
    SELECT id, price INTO v_existing_dest, v_dest_price
    FROM public.products dp
    WHERE dp.store_id = p_dest_store_id AND lower(dp.name) = lower(v_name)
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
          PERFORM * FROM public.add_category_get_or_create(p_dest_store_id, v_category_name);
        END IF;
        -- Insert product with source price
        INSERT INTO public.products (store_id, category_id, name, description, price, unit)
        VALUES (
          p_dest_store_id,
          (SELECT id FROM public.categories c WHERE c.store_id = p_dest_store_id AND lower(c.name) = lower(v_category_name) LIMIT 1),
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