-- Migration File: 20260712_100000_add_get_store_sukis_rpc.sql
-- RPC: get_store_sukis(p_store_id) & remove_suki_relationship(p_store_id, p_user_id) + RLS Policies

BEGIN;

-- 1. Function to get store sukis (bypassing RLS)
CREATE OR REPLACE FUNCTION public.get_store_sukis(p_store_id UUID)
RETURNS TABLE (
    user_id UUID,
    status TEXT,
    created_at TIMESTAMPTZ,
    name TEXT,
    user_code TEXT,
    username TEXT,
    avatar_url TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        sr.user_id,
        sr.status,
        sr.created_at,
        COALESCE(u.name, 'Suki Customer') AS name,
        COALESCE(u.user_code, 'NO-ID') AS user_code,
        u.username,
        u.avatar_url
    FROM public.suki_relationships sr
    LEFT JOIN public.app_users u ON u.id = sr.user_id
    WHERE sr.store_id = p_store_id
    ORDER BY sr.created_at DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_store_sukis(UUID) TO authenticated;

-- 2. Function to remove a suki relationship or decline pending suki request (bypassing RLS)
CREATE OR REPLACE FUNCTION public.remove_suki_relationship(p_store_id UUID, p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    -- Delete from suki_relationships
    DELETE FROM public.suki_relationships
    WHERE store_id = p_store_id AND user_id = p_user_id;

    -- Delete any pending suki notifications between this user and store
    DELETE FROM public.notifications
    WHERE store_id = p_store_id 
      AND (sender_user_id = p_user_id OR receiver_user_id = p_user_id)
      AND type ILIKE '%suki%';
END;
$$;

GRANT EXECUTE ON FUNCTION public.remove_suki_relationship(UUID, UUID) TO authenticated;

-- 3. Update SELECT RLS policy on suki_relationships
DROP POLICY IF EXISTS suki_select_store_members ON public.suki_relationships;
CREATE POLICY suki_select_store_members ON public.suki_relationships
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.store_members sm
            WHERE sm.store_id = suki_relationships.store_id
              AND sm.user_id = auth.uid()
        )
    );

-- 4. Update DELETE RLS policy on suki_relationships so store members can also delete
DROP POLICY IF EXISTS suki_delete_own ON public.suki_relationships;
DROP POLICY IF EXISTS suki_delete_policy ON public.suki_relationships;
CREATE POLICY suki_delete_policy ON public.suki_relationships
    FOR DELETE TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.store_members sm
            WHERE sm.store_id = suki_relationships.store_id
              AND sm.user_id = auth.uid()
        )
    );

COMMIT;
