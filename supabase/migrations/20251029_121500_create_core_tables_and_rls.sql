-- Migration: core tables (app_users, categories, store_members, notifications)
-- plus RLS policies and indexes. Designed for Supabase Postgres.
-- Safe, idempotent where practical.

-- Ensure pgcrypto for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Profiles table mapped to Supabase auth.users
CREATE TABLE IF NOT EXISTS public.app_users (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT NULL,
  name TEXT NULL,
  avatar_url TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS app_users_email_idx ON public.app_users (email);

-- Categories scoped to a store
CREATE TABLE IF NOT EXISTS public.categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES public.stores(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS categories_store_id_idx ON public.categories (store_id);

-- Store membership and roles
CREATE TABLE IF NOT EXISTS public.store_members (
  store_id UUID NOT NULL REFERENCES public.stores(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  role TEXT NOT NULL DEFAULT 'member',
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT store_members_pk PRIMARY KEY (store_id, user_id)
);

CREATE INDEX IF NOT EXISTS store_members_user_id_idx ON public.store_members (user_id);

-- Notifications between users, optionally tied to a store
CREATE TABLE IF NOT EXISTS public.notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  receiver_user_id UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  sender_user_id UUID NULL REFERENCES public.app_users(id) ON DELETE SET NULL,
  store_id UUID NULL REFERENCES public.stores(id) ON DELETE SET NULL,
  type TEXT NULL,
  title TEXT NULL,
  message TEXT NULL,
  read BOOLEAN NOT NULL DEFAULT FALSE,
  read_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS notifications_receiver_idx ON public.notifications (receiver_user_id, read, created_at);
CREATE INDEX IF NOT EXISTS notifications_store_idx ON public.notifications (store_id);

-- Add updated_at to stores and keep it in sync via trigger
ALTER TABLE public.stores
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'stores_updated_at') THEN
    CREATE TRIGGER stores_updated_at
    BEFORE UPDATE ON public.stores
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
  END IF;
END $$;

-- Enable RLS and define conservative policies
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_users_select_own ON public.app_users;
DROP POLICY IF EXISTS app_users_insert_own ON public.app_users;
DROP POLICY IF EXISTS app_users_update_own ON public.app_users;
CREATE POLICY app_users_select_own ON public.app_users
  FOR SELECT TO authenticated
  USING (id = auth.uid());
CREATE POLICY app_users_insert_own ON public.app_users
  FOR INSERT TO authenticated
  WITH CHECK (id = auth.uid());
CREATE POLICY app_users_update_own ON public.app_users
  FOR UPDATE TO authenticated
  USING (id = auth.uid())
  WITH CHECK (id = auth.uid());

ALTER TABLE public.stores ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS stores_select_members ON public.stores;
CREATE POLICY stores_select_members ON public.stores
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.stores.id AND m.user_id = auth.uid()
    )
  );

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS categories_select_members ON public.categories;
CREATE POLICY categories_select_members ON public.categories
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.categories.store_id AND m.user_id = auth.uid()
    )
  );

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS products_select_members ON public.products;
CREATE POLICY products_select_members ON public.products
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.products.store_id AND m.user_id = auth.uid()
    )
  );

-- Admin/owner writes for products and categories
DROP POLICY IF EXISTS products_insert_admins ON public.products;
CREATE POLICY products_insert_admins ON public.products
  FOR INSERT TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.products.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  );
DROP POLICY IF EXISTS products_update_admins ON public.products;
CREATE POLICY products_update_admins ON public.products
  FOR UPDATE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.products.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.products.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  );
DROP POLICY IF EXISTS products_delete_admins ON public.products;
CREATE POLICY products_delete_admins ON public.products
  FOR DELETE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.products.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  );

DROP POLICY IF EXISTS categories_insert_admins ON public.categories;
CREATE POLICY categories_insert_admins ON public.categories
  FOR INSERT TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.categories.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  );
DROP POLICY IF EXISTS categories_update_admins ON public.categories;
CREATE POLICY categories_update_admins ON public.categories
  FOR UPDATE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.categories.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.categories.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  );
DROP POLICY IF EXISTS categories_delete_admins ON public.categories;
CREATE POLICY categories_delete_admins ON public.categories
  FOR DELETE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.categories.store_id AND m.user_id = auth.uid() AND m.role IN ('owner','admin')
    )
  );

ALTER TABLE public.store_members ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS store_members_select_own ON public.store_members;
CREATE POLICY store_members_select_own ON public.store_members
  FOR SELECT TO authenticated
  USING (user_id = auth.uid());

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS notifications_select_own ON public.notifications;
CREATE POLICY notifications_select_own ON public.notifications
  FOR SELECT TO authenticated
  USING (receiver_user_id = auth.uid() OR sender_user_id = auth.uid());

-- Notes:
-- 1) Writes to store_members and notifications are intentionally restricted to service role
--    (no client policies) until secure flows (e.g., invite codes) are implemented.
-- 2) Policy names are descriptive for easier maintenance.