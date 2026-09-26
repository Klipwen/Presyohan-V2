-- Migration: Subscription Tiers Table & Admin Management Schema
-- Created: September 25, 2026 (Updated: Fix RLS Infinite Recursion with is_admin() SECURITY DEFINER)

-- 1. Helper function: is_admin() to check admin role without RLS recursion
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.app_users
    WHERE id = auth.uid() AND role = 'admin'
  );
$$;

-- 2. Create public.subscription_tiers table if not exists
CREATE TABLE IF NOT EXISTS public.subscription_tiers (
    tier_id VARCHAR PRIMARY KEY,
    name VARCHAR NOT NULL,
    price NUMERIC NOT NULL DEFAULT 0,
    billing_period VARCHAR DEFAULT 'month',
    trial_days INT DEFAULT 0,
    max_stores INT DEFAULT 1,
    max_items_per_store INT DEFAULT 50,
    max_staff_per_store INT DEFAULT 1,
    max_ai_parses_per_day INT DEFAULT 2,
    max_photo_scans_per_day INT DEFAULT 2,
    max_suki_partners INT DEFAULT 5,
    has_excel_export BOOLEAN DEFAULT false,
    has_pdf_export BOOLEAN DEFAULT false,
    has_price_cloning BOOLEAN DEFAULT false,
    has_priority_support BOOLEAN DEFAULT false,
    merchant_benefits JSONB DEFAULT '[]'::jsonb,
    customer_benefits JSONB DEFAULT '[]'::jsonb,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Seed initial tier defaults
INSERT INTO public.subscription_tiers (
    tier_id, name, price, billing_period, trial_days,
    max_stores, max_items_per_store, max_staff_per_store,
    max_ai_parses_per_day, max_photo_scans_per_day, max_suki_partners,
    has_excel_export, has_pdf_export, has_price_cloning, has_priority_support,
    merchant_benefits, customer_benefits
) VALUES
(
    'free', 'Free Tier', 0, 'forever', 0,
    1, 50, 1,
    2, 2, 5,
    false, false, false, false,
    '["1 Store Branch", "50 Items / Store", "1 Staff Account", "2 AI Parses / day", "2 Photo Scans / day"]'::jsonb,
    '["5 Suking Tindahan Partners", "Basic Price Search"]'::jsonb
),
(
    'pro', 'PRO Tier', 100.00, 'month', 7,
    10, 500, 10,
    10, 10, 15,
    true, true, true, false,
    '["Up to 10 Stores", "500 items / store branch", "10 staffs / store branch", "10 AI parses / day", "10 photo scans / day", "Unlock Suki partner"]'::jsonb,
    '["15 partner stores", "15 Presyohan Stores", "AI Online Price Search"]'::jsonb
),
(
    'vip', 'VIP Tier', 299.00, 'month', 0,
    999999, 999999, 999999,
    50, 50, 999999,
    true, true, true, true,
    '["Unlimited Stores", "Unlimited items / store", "Unlimited staff / store", "50 AI parses / day", "50 photo scans / day", "Unlock Suki partner"]'::jsonb,
    '["Unlimited partner stores", "Unlimited Presyohan Stores", "AI Online Price Search + Priority"]'::jsonb
)
ON CONFLICT (tier_id) DO UPDATE SET
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    billing_period = EXCLUDED.billing_period,
    trial_days = EXCLUDED.trial_days,
    max_stores = EXCLUDED.max_stores,
    max_items_per_store = EXCLUDED.max_items_per_store,
    max_staff_per_store = EXCLUDED.max_staff_per_store,
    max_ai_parses_per_day = EXCLUDED.max_ai_parses_per_day,
    max_photo_scans_per_day = EXCLUDED.max_photo_scans_per_day,
    max_suki_partners = EXCLUDED.max_suki_partners,
    has_excel_export = EXCLUDED.has_excel_export,
    has_pdf_export = EXCLUDED.has_pdf_export,
    has_price_cloning = EXCLUDED.has_price_cloning,
    has_priority_support = EXCLUDED.has_priority_support,
    merchant_benefits = EXCLUDED.merchant_benefits,
    customer_benefits = EXCLUDED.customer_benefits;

-- 4. Add tier columns to app_users and stores if missing
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='subscription_tier') THEN
        ALTER TABLE public.app_users ADD COLUMN subscription_tier VARCHAR DEFAULT 'free';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='subscription_expires_at') THEN
        ALTER TABLE public.app_users ADD COLUMN subscription_expires_at TIMESTAMPTZ;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='stores' AND column_name='subscription_tier') THEN
        ALTER TABLE public.stores ADD COLUMN subscription_tier VARCHAR DEFAULT 'free';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='stores' AND column_name='subscription_expires_at') THEN
        ALTER TABLE public.stores ADD COLUMN subscription_expires_at TIMESTAMPTZ;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='stores' AND column_name='billing_owner_id') THEN
        ALTER TABLE public.stores ADD COLUMN billing_owner_id UUID REFERENCES public.app_users(id);
    END IF;
END $$;

-- 5. Enable RLS and Policies for subscription_tiers
ALTER TABLE public.subscription_tiers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read access to subscription tiers" ON public.subscription_tiers;
CREATE POLICY "Allow public read access to subscription tiers"
ON public.subscription_tiers FOR SELECT
USING (true);

DROP POLICY IF EXISTS "Allow admin write access to subscription tiers" ON public.subscription_tiers;
CREATE POLICY "Allow admin write access to subscription tiers"
ON public.subscription_tiers FOR ALL
USING (public.is_admin());

-- 6. Clean up old recursive policy on app_users if exists and apply safe policy
DROP POLICY IF EXISTS app_users_admin_all ON public.app_users;
CREATE POLICY app_users_admin_all ON public.app_users
  FOR ALL TO authenticated
  USING (public.is_admin())
  WITH CHECK (public.is_admin());

-- 7. RPC Function for Fail-Proof Subscription Overrides (Atomic & SECURITY DEFINER)
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
        updated_at = NOW()
    WHERE id = target_user_id;

    -- Update all store branches owned by this merchant
    UPDATE public.stores
    SET 
        subscription_tier = LOWER(new_tier),
        subscription_expires_at = expires_at,
        updated_at = NOW()
    WHERE owner_id = target_user_id OR billing_owner_id = target_user_id;

    RETURN TRUE;
END;
$$;
