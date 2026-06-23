-- Migration: Grant Administrator Bypass RLS policies on core tables
-- Safe, idempotent script for Supabase Postgres.

-- 1. Overrides on public.stores
DROP POLICY IF EXISTS stores_admin_all ON public.stores;
CREATE POLICY stores_admin_all ON public.stores
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  );

-- 2. Overrides on public.categories
DROP POLICY IF EXISTS categories_admin_all ON public.categories;
CREATE POLICY categories_admin_all ON public.categories
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  );

-- 3. Overrides on public.products
DROP POLICY IF EXISTS products_admin_all ON public.products;
CREATE POLICY products_admin_all ON public.products
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  );

-- 4. Overrides on public.store_members
DROP POLICY IF EXISTS store_members_admin_all ON public.store_members;
CREATE POLICY store_members_admin_all ON public.store_members
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  );

-- 5. Overrides on public.suki_relationships
DROP POLICY IF EXISTS suki_relationships_admin_all ON public.suki_relationships;
CREATE POLICY suki_relationships_admin_all ON public.suki_relationships
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.app_users
      WHERE id = auth.uid() AND role = 'admin'
    )
  );
