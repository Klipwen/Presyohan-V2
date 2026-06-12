-- Migration File: 20260611_000000_add_public_and_suki_entities.sql

-- 1. Add 'is_public' flag to stores (default is private)
ALTER TABLE public.stores 
ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Add 'is_public' flag to products (default is private)
-- Toggling this allows select items to be visible to Sukis when the store is public
ALTER TABLE public.products 
ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Define Suki partnerships (Customer to Store relationship)
CREATE TABLE IF NOT EXISTS public.suki_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES public.stores(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL DEFAULT 'active',
    CONSTRAINT suki_user_store_unique UNIQUE (user_id, store_id)
);

-- 4. Indexes for optimization
CREATE INDEX IF NOT EXISTS suki_relationships_user_idx ON public.suki_relationships (user_id);
CREATE INDEX IF NOT EXISTS products_public_idx ON public.products (store_id, is_public);

-- 5. Enable Row Level Security (RLS) and define Policies
ALTER TABLE public.suki_relationships ENABLE ROW LEVEL SECURITY;

-- Suki RLS Policies
DROP POLICY IF EXISTS suki_select_own ON public.suki_relationships;
CREATE POLICY suki_select_own ON public.suki_relationships
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS suki_insert_own ON public.suki_relationships;
CREATE POLICY suki_insert_own ON public.suki_relationships
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());
