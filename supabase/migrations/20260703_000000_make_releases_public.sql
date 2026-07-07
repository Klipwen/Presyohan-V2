-- Migration: Make App Releases Publicly Selectable
-- Allow unauthenticated clients to read app releases for landing page and update checks

DROP POLICY IF EXISTS select_releases_auth ON public.app_releases;

CREATE POLICY select_releases_public ON public.app_releases
    FOR SELECT TO public USING (true);
