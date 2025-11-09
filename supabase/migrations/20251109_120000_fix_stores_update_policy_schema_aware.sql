-- Migration: Make stores UPDATE policy schema-aware for owner checks
-- Ensures owners can update store fields (including invite_code) regardless of
-- whether app_users uses direct id mapping or auth_uid mapping.

BEGIN;

DO $m$
DECLARE
  has_auth_uid BOOLEAN;
BEGIN
  -- Detect identity model for app_users
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'app_users'
      AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- Drop existing UPDATE policy to replace with schema-aware version
  EXECUTE 'DROP POLICY IF EXISTS stores_update_owners ON public.stores';

  IF has_auth_uid THEN
    -- Legacy model: app_users.id is arbitrary; auth.uid() is stored in app_users.auth_uid
    EXECUTE $policy$
      CREATE POLICY stores_update_owners ON public.stores
        FOR UPDATE TO authenticated
        USING (
          EXISTS (
            SELECT 1
            FROM public.store_members m
            JOIN public.app_users u ON u.id = m.user_id
            WHERE m.store_id = public.stores.id
              AND u.auth_uid = auth.uid()
              AND m.role = 'owner'
          )
        )
        WITH CHECK (
          EXISTS (
            SELECT 1
            FROM public.store_members m
            JOIN public.app_users u ON u.id = m.user_id
            WHERE m.store_id = public.stores.id
              AND u.auth_uid = auth.uid()
              AND m.role = 'owner'
          )
        );
    $policy$;
  ELSE
    -- Simple model: app_users.id equals auth.uid()
    EXECUTE $policy$
      CREATE POLICY stores_update_owners ON public.stores
        FOR UPDATE TO authenticated
        USING (
          EXISTS (
            SELECT 1
            FROM public.store_members m
            WHERE m.store_id = public.stores.id
              AND m.user_id = auth.uid()
              AND m.role = 'owner'
          )
        )
        WITH CHECK (
          EXISTS (
            SELECT 1
            FROM public.store_members m
            WHERE m.store_id = public.stores.id
              AND m.user_id = auth.uid()
              AND m.role = 'owner'
          )
        );
    $policy$;
  END IF;
END $m$;

COMMENT ON POLICY stores_update_owners ON public.stores
IS 'Owners can update their stores; schema-aware for app_users identity model.';

COMMIT;