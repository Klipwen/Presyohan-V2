-- Migration: Add missing RLS policies for store creation
-- Allows authenticated users to create stores and become owners

-- Ensure idempotency: drop existing policies before re-creating
DROP POLICY IF EXISTS stores_insert_authenticated ON public.stores;
DROP POLICY IF EXISTS store_members_insert_owner ON public.store_members;
DROP POLICY IF EXISTS stores_update_owners ON public.stores;
DROP POLICY IF EXISTS stores_delete_owners ON public.stores;

-- Add INSERT policy for stores table (authenticated users can create stores)
CREATE POLICY stores_insert_authenticated ON public.stores
  FOR INSERT TO authenticated
  WITH CHECK (true);

-- Add INSERT policy for store_members table (for adding owners during store creation)
CREATE POLICY store_members_insert_owner ON public.store_members
  FOR INSERT TO authenticated
  WITH CHECK (user_id = auth.uid() AND role = 'owner');

-- Add UPDATE policy for stores table (owners can update their stores)
CREATE POLICY stores_update_owners ON public.stores
  FOR UPDATE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.stores.id AND m.user_id = auth.uid() AND m.role = 'owner'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.stores.id AND m.user_id = auth.uid() AND m.role = 'owner'
    )
  );

-- Add DELETE policy for stores table (owners can delete their stores)
CREATE POLICY stores_delete_owners ON public.stores
  FOR DELETE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.store_members m
      WHERE m.store_id = public.stores.id AND m.user_id = auth.uid() AND m.role = 'owner'
    )
  );

COMMENT ON POLICY stores_insert_authenticated ON public.stores
IS 'Allow authenticated users to create new stores';

COMMENT ON POLICY store_members_insert_owner ON public.store_members
IS 'Allow users to add themselves as owners when creating stores';