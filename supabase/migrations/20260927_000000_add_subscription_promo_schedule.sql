-- Migration: Add promo_start_at, promo_end_at, and promo_expiry_label to subscription_tiers table
-- Created: September 27, 2026

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_start_at') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_start_at TIMESTAMPTZ DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_end_at') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_end_at TIMESTAMPTZ DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='promo_expiry_label') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN promo_expiry_label VARCHAR DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='subscription_tiers' AND column_name='description') THEN
        ALTER TABLE public.subscription_tiers ADD COLUMN description TEXT DEFAULT NULL;
    END IF;
END $$;
