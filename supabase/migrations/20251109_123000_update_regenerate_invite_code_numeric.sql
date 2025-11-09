-- Update RPC to generate a 6-digit numeric invite code (no letters)
-- Keeps SECURITY DEFINER and owner-only check; retries on rare collisions

CREATE OR REPLACE FUNCTION public.regenerate_invite_code(p_store_id UUID)
RETURNS TABLE(invite_code TEXT, invite_code_created_at TIMESTAMP)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  has_auth_uid BOOLEAN;
  is_owner BOOLEAN := FALSE;
  v_code TEXT;
BEGIN
  -- Detect identity model
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- Ownership check (schema-aware)
  IF has_auth_uid THEN
    SELECT EXISTS (
      SELECT 1 FROM public.store_members m
      JOIN public.app_users u ON u.id = m.user_id
      WHERE m.store_id = p_store_id AND u.auth_uid = auth.uid() AND m.role = 'owner'
    ) INTO is_owner;
  ELSE
    SELECT EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = p_store_id AND m.user_id = auth.uid() AND m.role = 'owner'
    ) INTO is_owner;
  END IF;

  IF NOT is_owner THEN
    RAISE EXCEPTION 'Only owners can regenerate invite code';
  END IF;

  -- Generate a numeric-only 6-digit code (100000-999999); retry on rare collisions
  FOR i IN 1..5 LOOP
    v_code := (floor(random() * 900000)::int + 100000)::text;
    BEGIN
      UPDATE public.stores
      SET invite_code = v_code,
          invite_code_created_at = NOW()
      WHERE id = p_store_id;
      EXIT; -- success
    EXCEPTION
      WHEN unique_violation THEN
        -- collision on invite_code; try again
        CONTINUE;
    END;
  END LOOP;

  -- Return the new values
  RETURN QUERY
  SELECT s.invite_code, s.invite_code_created_at
  FROM public.stores s
  WHERE s.id = p_store_id;
END;
$$;

COMMENT ON FUNCTION public.regenerate_invite_code(UUID)
IS 'Regenerates 6-digit numeric invite_code for a store; owner-only; SECURITY DEFINER.';

GRANT EXECUTE ON FUNCTION public.regenerate_invite_code(UUID) TO authenticated;
ALTER FUNCTION public.regenerate_invite_code(UUID) OWNER TO postgres;