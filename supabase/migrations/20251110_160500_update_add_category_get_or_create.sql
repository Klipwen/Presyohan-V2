-- Update RPC: add_category -> get-or-create semantics to prevent duplicates
-- Ensures only one category per (store_id, name), case-insensitive match

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
  v_name TEXT;
  existing_id UUID;
BEGIN
  v_name := trim(p_name);
  IF v_name IS NULL OR length(v_name) = 0 THEN
    RAISE EXCEPTION 'Category name is required';
  END IF;

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

  -- Check for existing category (case-insensitive)
  SELECT id INTO existing_id
  FROM public.categories
  WHERE store_id = p_store_id AND lower(name) = lower(v_name)
  LIMIT 1;

  IF existing_id IS NOT NULL THEN
    -- Return existing category
    RETURN QUERY
      SELECT c.id, c.store_id, c.name
      FROM public.categories c
      WHERE c.id = existing_id;
    RETURN;
  END IF;

  -- Create category if missing
  INSERT INTO public.categories (store_id, name)
  VALUES (p_store_id, v_name)
  RETURNING id, categories.store_id, categories.name INTO category_id, store_id, name;

  -- Return the inserted row
  RETURN QUERY SELECT category_id, store_id, name;
END;
$$;