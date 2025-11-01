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
  v_user_id UUID;
  has_auth_uid BOOLEAN;
BEGIN
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RAISE EXCEPTION 'Store name is required';
  END IF;

  -- Detect app_users identity model and upsert profile accordingly
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  IF has_auth_uid THEN
    -- Legacy model: app_users(id generated), app_users.auth_uid maps to auth.uid()
    INSERT INTO public.app_users (auth_uid, email, created_at)
    VALUES (auth.uid(), NULL, NOW())
    ON CONFLICT (auth_uid) DO NOTHING;
    SELECT id INTO v_user_id FROM public.app_users WHERE auth_uid = auth.uid();
  ELSE
    -- Simple model: app_users.id equals auth.uid()
    INSERT INTO public.app_users (id, email, created_at)
    VALUES (auth.uid(), NULL, NOW())
    ON CONFLICT (id) DO NOTHING;
    SELECT id INTO v_user_id FROM public.app_users WHERE id = auth.uid();
  END IF;

  -- Create store
  INSERT INTO public.stores (name, branch, type)
  VALUES (trim(p_name), NULLIF(trim(p_branch), ''), NULLIF(trim(p_type), ''))
  RETURNING id INTO v_store_id;

  -- Assign caller as owner using resolved app_users.id
  INSERT INTO public.store_members (store_id, user_id, role)
  VALUES (v_store_id, v_user_id, 'owner');

  RETURN v_store_id;
END;
$$;

COMMENT ON FUNCTION public.create_store(TEXT, TEXT, TEXT)
IS 'Creates a store and assigns the caller as owner; returns store id.';

-- Allow authenticated users to execute the RPC
GRANT EXECUTE ON FUNCTION public.create_store(TEXT, TEXT, TEXT) TO authenticated;

-- Ensure the function is owned by table owner to bypass RLS under SECURITY DEFINER
ALTER FUNCTION public.create_store(TEXT, TEXT, TEXT) OWNER TO postgres;