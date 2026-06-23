-- Migration: Allow authenticated users to delete their own suki relationships (unsubscribe)
DROP POLICY IF EXISTS suki_delete_own ON public.suki_relationships;
CREATE POLICY suki_delete_own ON public.suki_relationships
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());
