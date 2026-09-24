-- Migration: In-App Account Deletion RPC for Google Play Policy Compliance
-- Location: supabase/migrations/20260924_000000_add_delete_user_account_rpc.sql
-- Author: Presyohan Engineering
-- Date: 2026-09-24

CREATE OR REPLACE FUNCTION public.delete_user_account()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    -- Get current authenticated user ID
    v_user_id := auth.uid();
    
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- 1. Remove user notifications (receiver or sender)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'notifications') THEN
        DELETE FROM public.notifications
        WHERE receiver_user_id = v_user_id OR sender_user_id = v_user_id;
    END IF;

    -- 2. Remove suki relationships & suki customer pairings
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'suki_relationships') THEN
        DELETE FROM public.suki_relationships
        WHERE user_id = v_user_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'suki_customers') THEN
        DELETE FROM public.suki_customers
        WHERE customer_user_id = v_user_id;
    END IF;

    -- 3. Remove user ratings & contact messages
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'app_ratings') THEN
        DELETE FROM public.app_ratings WHERE user_id = v_user_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'contact_messages') THEN
        DELETE FROM public.contact_messages WHERE user_id = v_user_id;
    END IF;

    -- 4. Nullify foreign key references in shared/admin tables
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'app_contact_info') THEN
        UPDATE public.app_contact_info SET updated_by = NULL WHERE updated_by = v_user_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'app_releases') THEN
        UPDATE public.app_releases SET created_by = NULL WHERE created_by = v_user_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'announcements') THEN
        UPDATE public.announcements SET created_by = NULL WHERE created_by = v_user_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'stores' AND column_name = 'billing_owner_id') THEN
        EXECUTE 'UPDATE public.stores SET billing_owner_id = NULL WHERE billing_owner_id = $1' USING v_user_id;
    END IF;

    -- 5. Remove store memberships
    DELETE FROM public.store_members
    WHERE user_id = v_user_id;

    -- 6. Delete user profile record from public.app_users
    DELETE FROM public.app_users
    WHERE id = v_user_id;

    -- 7. Delete user record from auth.users schema
    DELETE FROM auth.users
    WHERE id = v_user_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Account and personal data successfully deleted'
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', SQLERRM
    );
END;
$$;

-- Grant execution to authenticated users
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO authenticated;
