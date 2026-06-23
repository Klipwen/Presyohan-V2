-- Migration: Admin Portal Schema Setup
-- Safe, idempotent script for Supabase Postgres.

-- 1. Extend app_users with Admin Roles and Activity Tracking
ALTER TABLE public.app_users 
  ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user',
  ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS app_users_activity_idx ON public.app_users (last_activity_at);

-- 2. Create app_releases table for version checking and force-updates
CREATE TABLE IF NOT EXISTS public.app_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_code INT UNIQUE NOT NULL,
    version_name TEXT NOT NULL,
    download_url TEXT NOT NULL,
    whats_new TEXT NOT NULL,
    is_forced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES public.app_users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS app_releases_version_idx ON public.app_releases (version_code DESC);

-- 3. Create announcements table for broadcasting alerts
CREATE TABLE IF NOT EXISTS public.announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES public.app_users(id) ON DELETE SET NULL
);

-- 4. Support multiple Standard Stores & an Active Default Store
ALTER TABLE public.stores
  ADD COLUMN IF NOT EXISTS is_standard_store BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_default_standard BOOLEAN NOT NULL DEFAULT FALSE;

-- Ensure only ONE store can be marked as the default standard store at any time
DROP INDEX IF EXISTS public.unique_default_standard_store;
CREATE UNIQUE INDEX unique_default_standard_store ON public.stores (is_default_standard) 
WHERE (is_default_standard = TRUE);

-- 5. Enable Row Level Security (RLS) & Policies
ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;

-- App Releases Policies
DROP POLICY IF EXISTS select_releases_auth ON public.app_releases;
CREATE POLICY select_releases_auth ON public.app_releases 
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS admin_all_releases ON public.app_releases;
CREATE POLICY admin_all_releases ON public.app_releases 
    FOR ALL TO authenticated 
    USING (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

-- Announcements Policies
DROP POLICY IF EXISTS select_announcements_auth ON public.announcements;
CREATE POLICY select_announcements_auth ON public.announcements 
    FOR SELECT TO authenticated USING (is_active = true);

DROP POLICY IF EXISTS admin_all_announcements ON public.announcements;
CREATE POLICY admin_all_announcements ON public.announcements 
    FOR ALL TO authenticated 
    USING (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));
