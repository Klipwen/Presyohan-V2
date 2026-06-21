-- Migration: Allow Suki partners and public store viewers to read categories and public products
-- 1. Update public.products select policy
DROP POLICY IF EXISTS products_select_members ON public.products;
DROP POLICY IF EXISTS products_select_members_and_public ON public.products;

CREATE POLICY products_select_suki_and_members ON public.products
    FOR SELECT TO authenticated
    USING (
        -- User is a store staff member/owner
        EXISTS (
            SELECT 1 FROM public.store_members m
            WHERE m.store_id = public.products.store_id AND m.user_id = auth.uid()
        )
        -- OR product is public and user is partnered (Suki) or the store is public
        OR (
            is_public = true 
            AND (
                EXISTS (
                    SELECT 1 FROM public.suki_relationships r
                    WHERE r.store_id = public.products.store_id AND r.user_id = auth.uid()
                )
                OR EXISTS (
                    SELECT 1 FROM public.stores s
                    WHERE s.id = public.products.store_id AND s.is_public = true
                )
            )
        )
    );

-- 2. Update public.categories select policy
DROP POLICY IF EXISTS categories_select_members ON public.categories;
DROP POLICY IF EXISTS categories_select_members_and_public ON public.categories;

CREATE POLICY categories_select_suki_and_members ON public.categories
    FOR SELECT TO authenticated
    USING (
        -- User is a store staff member/owner
        EXISTS (
            SELECT 1 FROM public.store_members m
            WHERE m.store_id = public.categories.store_id AND m.user_id = auth.uid()
        )
        -- OR store is public or user is partnered (Suki)
        OR EXISTS (
            SELECT 1 FROM public.suki_relationships r
            WHERE r.store_id = public.categories.store_id AND r.user_id = auth.uid()
        )
        OR EXISTS (
            SELECT 1 FROM public.stores s
            WHERE s.id = public.categories.store_id AND s.is_public = true
        )
    );
