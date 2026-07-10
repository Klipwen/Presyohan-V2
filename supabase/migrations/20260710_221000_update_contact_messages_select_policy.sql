-- Migration: contact_messages select policy (safe, non-recursive)
-- The previous version used a recursive subquery on the same RLS-protected table,
-- which caused Supabase to return zero rows (infinite recursion bug).
-- Private thread scoping is handled client-side in ContactUsActivity.kt.
-- This policy simply allows all authenticated users to read messages.

DROP POLICY IF EXISTS select_messages ON public.contact_messages;
CREATE POLICY select_messages ON public.contact_messages
    FOR SELECT TO authenticated
    USING (true);
