-- Migration: Harden store_members and stores policies; normalize column naming
-- Safe to re-run (idempotent) and designed for Supabase Postgres.

BEGIN;

-- 1) Normalize store_members column name if legacy 'member_user_id' exists
DO $$
DECLARE
  col_exists BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'store_members'
      AND column_name = 'member_user_id'
  ) INTO col_exists;

  IF col_exists THEN
    EXECUTE 'ALTER TABLE public.store_members RENAME COLUMN member_user_id TO user_id';
  END IF;
END $$;

-- 2) Ensure RLS is enabled on target tables
ALTER TABLE IF EXISTS public.store_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.stores ENABLE ROW LEVEL SECURITY;

-- 3) Recreate SELECT policies for memberships and stores with schema-aware logic
DO $$
DECLARE
  has_auth_uid BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  -- store_members_select_own
  EXECUTE 'DROP POLICY IF EXISTS store_members_select_own ON public.store_members';
  IF has_auth_uid THEN
    EXECUTE 'CREATE POLICY store_members_select_own ON public.store_members
      FOR SELECT TO authenticated
      USING (
        EXISTS (
          SELECT 1 FROM public.app_users u
          WHERE u.id = public.store_members.user_id AND u.auth_uid = auth.uid()
        )
      )';
  ELSE
    EXECUTE 'CREATE POLICY store_members_select_own ON public.store_members
      FOR SELECT TO authenticated
      USING (user_id = auth.uid())';
  END IF;

  -- stores_select_members
  EXECUTE 'DROP POLICY IF EXISTS stores_select_members ON public.stores';
  IF has_auth_uid THEN
    EXECUTE 'CREATE POLICY stores_select_members ON public.stores
      FOR SELECT TO authenticated
      USING (
        EXISTS (
          SELECT 1 FROM public.store_members m
          JOIN public.app_users u ON u.id = m.user_id
          WHERE m.store_id = public.stores.id AND u.auth_uid = auth.uid()
        )
      )';
  ELSE
    EXECUTE 'CREATE POLICY stores_select_members ON public.stores
      FOR SELECT TO authenticated
      USING (
        EXISTS (
          SELECT 1 FROM public.store_members m
          WHERE m.store_id = public.stores.id AND m.user_id = auth.uid()
        )
      )';
  END IF;
END $$;

COMMIT;