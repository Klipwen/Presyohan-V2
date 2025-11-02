-- RPCs: add_category and add_product
-- SECURITY DEFINER to bypass RLS safely with role checks.

CREATE OR REPLACE FUNCTION public.add_category(
  p_store_id UUID,
  p_name TEXT
)
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
  user_role TEXT;
BEGIN
  -- Detect app_users identity model
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- Determine role of current user in the store
  IF has_auth_uid THEN
    SELECT m.role INTO user_role
    FROM public.store_members m
    JOIN public.app_users u ON u.id = m.user_id
    WHERE m.store_id = p_store_id AND u.auth_uid = auth.uid()
    LIMIT 1;
  ELSE
    SELECT m.role INTO user_role
    FROM public.store_members m
    WHERE m.store_id = p_store_id AND m.user_id = auth.uid()
    LIMIT 1;
  END IF;

  -- Only owners/managers can create categories
  IF user_role IS NULL OR user_role NOT IN ('owner','manager') THEN
    RAISE EXCEPTION 'Not authorized to add categories for this store';
  END IF;

  INSERT INTO public.categories (store_id, name)
  VALUES (p_store_id, p_name)
  RETURNING id, categories.store_id, categories.name INTO category_id, store_id, name;
  -- Return the inserted row
  RETURN QUERY SELECT category_id, store_id, name;
END;
$$;

GRANT EXECUTE ON FUNCTION public.add_category(UUID, TEXT) TO authenticated;
ALTER FUNCTION public.add_category(UUID, TEXT) OWNER TO postgres;


CREATE OR REPLACE FUNCTION public.add_product(
  p_store_id UUID,
  p_category_id UUID,
  p_name TEXT,
  p_description TEXT,
  p_price NUMERIC,
  p_unit TEXT
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
  user_role TEXT;
  out_units TEXT;
  out_category TEXT;
BEGIN
  -- Detect app_users identity model
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- Determine role of current user in the store
  IF has_auth_uid THEN
    SELECT m.role INTO user_role
    FROM public.store_members m
    JOIN public.app_users u ON u.id = m.user_id
    WHERE m.store_id = p_store_id AND u.auth_uid = auth.uid()
    LIMIT 1;
  ELSE
    SELECT m.role INTO user_role
    FROM public.store_members m
    WHERE m.store_id = p_store_id AND m.user_id = auth.uid()
    LIMIT 1;
  END IF;

  -- Only owners/managers can create products
  IF user_role IS NULL OR user_role NOT IN ('owner','manager') THEN
    RAISE EXCEPTION 'Not authorized to add products for this store';
  END IF;

  -- Insert product
  INSERT INTO public.products (store_id, category_id, name, description, price, unit)
  VALUES (p_store_id, p_category_id, p_name, p_description, p_price, p_unit)
  RETURNING products.id, products.store_id, products.name, products.description, products.price, products.unit INTO product_id, store_id, name, description, price, out_units;

  -- Fetch category name for output (may be NULL)
  SELECT COALESCE(c.name, '') INTO out_category FROM public.categories c WHERE c.id = p_category_id;

  units := out_units;
  category := out_category;
  -- Return the inserted row enriched with category name
  RETURN QUERY SELECT product_id, store_id, name, description, price, units, category;
END;
$$;

GRANT EXECUTE ON FUNCTION public.add_product(UUID, UUID, TEXT, TEXT, NUMERIC, TEXT) TO authenticated;
ALTER FUNCTION public.add_product(UUID, UUID, TEXT, TEXT, NUMERIC, TEXT) OWNER TO postgres;