-- Migration: Allow all authenticated users to view stores
DROP POLICY IF EXISTS stores_select_members ON public.stores;

CREATE POLICY stores_select_authenticated ON public.stores
    FOR SELECT TO authenticated
    USING (true);
