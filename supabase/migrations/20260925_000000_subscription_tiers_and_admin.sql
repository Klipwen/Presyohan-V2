-- Migration: Subscription Tiers Table & Admin Management Schema
-- Created: September 25, 2026 (Updated: Added Hybrid Discount, Promo Override & Custom Badge fields)

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
    discount_percent NUMERIC DEFAULT 0,
    promo_price NUMERIC DEFAULT NULL,
    promo_badge VARCHAR DEFAULT NULL,
    cta_button_text VARCHAR DEFAULT NULL,
    billing_period VARCHAR DEFAULT 'month',
    trial_days INT DEFAULT 0,
    display_order INT DEFAULT 0,
    max_stores INT DEFAULT 1,
    max_items_per_store INT DEFAULT 100,
    max_staff_per_store INT DEFAULT 3,
    max_categories_per_store INT DEFAULT 10,
    max_ai_parses_per_day INT DEFAULT 3,
    max_photo_scans_per_day INT DEFAULT 3,
    max_suki_partners INT DEFAULT 5,
    max_presyohan_stores INT DEFAULT 5,
    max_internet_searches_per_day INT DEFAULT 3,
    has_excel_export BOOLEAN DEFAULT false,
    has_pdf_export BOOLEAN DEFAULT false,
    has_notes_export BOOLEAN DEFAULT true,
    has_price_cloning BOOLEAN DEFAULT false,
    has_customer_pairing BOOLEAN DEFAULT false,
    has_priority_support BOOLEAN DEFAULT false,
    merchant_benefits JSONB DEFAULT '[]'::jsonb,
    customer_benefits JSONB DEFAULT '[]'::jsonb,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure feature columns exist if table was previously created
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='discount_percent') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN discount_percent NUMERIC DEFAULT 0;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_price') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_price NUMERIC DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_badge') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_badge VARCHAR DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='cta_button_text') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN cta_button_text VARCHAR DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='display_order') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN display_order INT DEFAULT 0;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='max_categories_per_store') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN max_categories_per_store INT DEFAULT 10;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='has_customer_pairing') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN has_customer_pairing BOOLEAN DEFAULT false;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='has_notes_export') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN has_notes_export BOOLEAN DEFAULT true;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='max_presyohan_stores') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN max_presyohan_stores INT DEFAULT 5;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='max_internet_searches_per_day') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN max_internet_searches_per_day INT DEFAULT 3;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_start_at') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_start_at TIMESTAMPTZ DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_end_at') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_end_at TIMESTAMPTZ DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_expiry_label') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_expiry_label VARCHAR DEFAULT NULL;
    END IF;
END $$;

-- 3. Seed initial tier defaults matching exact matrix
INSERT INTO public.subscription_tiers (
    tier_id, name, price, discount_percent, promo_price, promo_badge, cta_button_text, billing_period, trial_days,
    max_stores, max_items_per_store, max_staff_per_store, max_categories_per_store,
    max_ai_parses_per_day, max_photo_scans_per_day, max_suki_partners, max_presyohan_stores, max_internet_searches_per_day,
    has_excel_export, has_pdf_export, has_notes_export, has_price_cloning, has_customer_pairing, has_priority_support,
    merchant_benefits, customer_benefits
) VALUES
(
    'free', 'Free Tier', 0, 0, NULL, 'BASIC PLAN', 'CURRENT ACTIVE PLAN', 'forever', 0,
    1, 100, 3, 10,
    3, 3, 5, 5, 3,
    false, false, true, false, false, false,
    '["1 Store Branch", "3 Staffs / Store", "10 Categories / Store", "100 Items / Store", "3 AI Parses / day", "3 Photo Scans / day", "Convert as Notes"]'::jsonb,
    '["5 Suking Tindahan", "5 Presyohan Stores", "3 Internet Searches / day"]'::jsonb
),
(
    'pro', 'PRO Tier', 99.00, 0, NULL, '7 DAYS TRIAL', 'UPGRADE TO PRO (₱99/MO)', 'month', 7,
    10, 500, 10, 25,
    10, 10, 15, 15, 15,
    true, true, true, true, true, false,
    '["Up to 10 Stores", "10 Staffs / Store", "25 Categories / Store", "500 Items / Store", "10 AI Parses / day", "10 Photo Scans / day", "Store Items Cloning", "Customer Pairing", "Convert to Excel & PDF"]'::jsonb,
    '["15 Suking Tindahan", "15 Presyohan Stores", "15 Internet Searches / day"]'::jsonb
),
(
    'vip', 'VIP Tier', 299.00, 0, NULL, 'BEST VALUE', 'UPGRADE TO VIP (₱299/MO)', 'month', 0,
    999999, 999999, 999999, 999999,
    50, 50, 999999, 999999, 999999,
    true, true, true, true, true, true,
    '["Unlimited Stores", "Unlimited Staff / Store", "Unlimited Categories / Store", "Unlimited Items / Store", "50 AI Parses / day", "50 Photo Scans / day", "Unlimited Cloning & Export", "Unlimited Customer Pairing"]'::jsonb,
    '["Unlimited Suking Tindahan", "Unlimited Presyohan Stores", "Unlimited Internet Search"]'::jsonb
)
ON CONFLICT (tier_id) DO UPDATE SET
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    discount_percent = EXCLUDED.discount_percent,
    promo_price = EXCLUDED.promo_price,
    promo_badge = EXCLUDED.promo_badge,
    cta_button_text = EXCLUDED.cta_button_text,
    billing_period = EXCLUDED.billing_period,
    trial_days = EXCLUDED.trial_days,
    max_stores = EXCLUDED.max_stores,
    max_items_per_store = EXCLUDED.max_items_per_store,
    max_staff_per_store = EXCLUDED.max_staff_per_store,
    max_categories_per_store = EXCLUDED.max_categories_per_store,
    max_ai_parses_per_day = EXCLUDED.max_ai_parses_per_day,
    max_photo_scans_per_day = EXCLUDED.max_photo_scans_per_day,
    max_suki_partners = EXCLUDED.max_suki_partners,
    max_presyohan_stores = EXCLUDED.max_presyohan_stores,
    max_internet_searches_per_day = EXCLUDED.max_internet_searches_per_day,
    has_excel_export = EXCLUDED.has_excel_export,
    has_pdf_export = EXCLUDED.has_pdf_export,
    has_notes_export = EXCLUDED.has_notes_export,
    has_price_cloning = EXCLUDED.has_price_cloning,
    has_customer_pairing = EXCLUDED.has_customer_pairing,
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
