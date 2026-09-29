-- Migration: Add subscription_auto_renew support and safe expiration checking
-- Date: September 29, 2026

-- 1. Ensure subscription_auto_renew column exists on app_users
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'app_users' 
          AND column_name = 'subscription_auto_renew'
    ) THEN
        ALTER TABLE public.app_users ADD COLUMN subscription_auto_renew BOOLEAN DEFAULT false;
    END IF;
END $$;

-- 2. Ensure subscription_auto_renew column exists on stores
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'stores' 
          AND column_name = 'subscription_auto_renew'
    ) THEN
        ALTER TABLE public.stores ADD COLUMN subscription_auto_renew BOOLEAN DEFAULT false;
    END IF;
END $$;

-- 3. Update override_subscription_tier to support auto_renew flag optionally
CREATE OR REPLACE FUNCTION public.override_subscription_tier(
    target_user_id UUID,
    new_tier VARCHAR,
    expires_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    -- Check administrative role using SECURITY DEFINER helper
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'Access denied. Administrative privileges required.';
    END IF;

    -- Update merchant user account
    UPDATE public.app_users
    SET 
        subscription_tier = LOWER(new_tier),
        subscription_expires_at = expires_at,
        subscription_auto_renew = (expires_at IS NOT NULL AND LOWER(new_tier) != 'free'),
        updated_at = NOW()
    WHERE id = target_user_id;

    -- Update all store branches owned by this merchant via store_members or billing_owner_id
    UPDATE public.stores
    SET 
        subscription_tier = LOWER(new_tier),
        subscription_expires_at = expires_at,
        subscription_auto_renew = (expires_at IS NOT NULL AND LOWER(new_tier) != 'free'),
        updated_at = NOW()
    WHERE id IN (
        SELECT store_id FROM public.store_members 
        WHERE user_id = target_user_id AND role = 'owner'
    )
    OR billing_owner_id = target_user_id;

    RETURN TRUE;
END;
$$;
