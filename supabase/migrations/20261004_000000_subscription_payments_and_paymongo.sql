-- Migration: Subscription Payments Table & PayMongo Webhook Handlers
-- Created: October 4, 2026

-- 1. Create subscription_payments table
CREATE TABLE IF NOT EXISTS public.subscription_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.app_users(id) ON DELETE SET NULL,
    tier_id VARCHAR NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR DEFAULT 'PHP',
    payment_method VARCHAR DEFAULT 'paymongo',
    paymongo_checkout_id VARCHAR,
    paymongo_payment_id VARCHAR,
    status VARCHAR DEFAULT 'pending', -- 'pending', 'paid', 'failed', 'cancelled'
    billing_period VARCHAR DEFAULT 'month',
    customer_name VARCHAR,
    customer_email VARCHAR,
    customer_phone VARCHAR,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for quick lookups
CREATE INDEX IF NOT EXISTS idx_subscription_payments_user_id ON public.subscription_payments(user_id);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_checkout_id ON public.subscription_payments(paymongo_checkout_id);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_status ON public.subscription_payments(status);

-- Enable RLS
ALTER TABLE public.subscription_payments ENABLE ROW LEVEL SECURITY;

-- 2. RLS Policies
DROP POLICY IF EXISTS "Users can view their own payments" ON public.subscription_payments;
CREATE POLICY "Users can view their own payments"
ON public.subscription_payments FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Admin full access on payments" ON public.subscription_payments;
CREATE POLICY "Admin full access on payments"
ON public.subscription_payments FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- Allow Edge Functions (service_role or authenticated users during checkout creation) to insert payments
DROP POLICY IF EXISTS "Allow authenticated insert subscription payments" ON public.subscription_payments;
CREATE POLICY "Allow authenticated insert subscription payments"
ON public.subscription_payments FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id OR public.is_admin());

-- 3. Atomic RPC to finalize payment success & activate tier
CREATE OR REPLACE FUNCTION public.process_subscription_payment_success(
    p_user_id UUID,
    p_tier_id VARCHAR,
    p_amount NUMERIC,
    p_payment_method VARCHAR DEFAULT 'paymongo',
    p_checkout_id VARCHAR DEFAULT NULL,
    p_payment_id VARCHAR DEFAULT NULL,
    p_customer_email VARCHAR DEFAULT NULL,
    p_metadata JSONB DEFAULT '{}'::jsonb
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_target_tier VARCHAR;
    v_current_expires_at TIMESTAMPTZ;
    v_new_expires_at TIMESTAMPTZ;
    v_user_name TEXT;
    v_tier_name TEXT;
    v_result JSONB;
BEGIN
    v_target_tier := LOWER(p_tier_id);
    IF v_target_tier NOT IN ('pro', 'vip', 'free') THEN
        v_target_tier := 'pro';
    END IF;

    -- Fetch existing subscription info
    SELECT subscription_expires_at, full_name
    INTO v_current_expires_at, v_user_name
    FROM public.app_users
    WHERE id = p_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User with ID % not found.', p_user_id;
    END IF;

    -- Compute new expiration date (extend if active, or add 30 days from now)
    IF v_current_expires_at IS NOT NULL AND v_current_expires_at > NOW() THEN
        v_new_expires_at := v_current_expires_at + INTERVAL '30 days';
    ELSE
        v_new_expires_at := NOW() + INTERVAL '30 days';
    END IF;

    -- Update User Account
    UPDATE public.app_users
    SET 
        subscription_tier = v_target_tier,
        subscription_expires_at = v_new_expires_at,
        updated_at = NOW()
    WHERE id = p_user_id;

    -- Update all stores owned/billed by this user
    UPDATE public.stores
    SET 
        subscription_tier = v_target_tier,
        subscription_expires_at = v_new_expires_at,
        updated_at = NOW()
    WHERE owner_id = p_user_id OR billing_owner_id = p_user_id;

    -- Insert or update payment record
    IF p_checkout_id IS NOT NULL THEN
        UPDATE public.subscription_payments
        SET 
            status = 'paid',
            paymongo_payment_id = COALESCE(p_payment_id, paymongo_payment_id),
            updated_at = NOW(),
            metadata = metadata || p_metadata
        WHERE paymongo_checkout_id = p_checkout_id;

        IF NOT FOUND THEN
            INSERT INTO public.subscription_payments (
                user_id, tier_id, amount, payment_method,
                paymongo_checkout_id, paymongo_payment_id, status,
                customer_email, metadata
            ) VALUES (
                p_user_id, v_target_tier, p_amount, p_payment_method,
                p_checkout_id, p_payment_id, 'paid',
                p_customer_email, p_metadata
            );
        END IF;
    ELSE
        INSERT INTO public.subscription_payments (
            user_id, tier_id, amount, payment_method,
            paymongo_checkout_id, paymongo_payment_id, status,
            customer_email, metadata
        ) VALUES (
            p_user_id, v_target_tier, p_amount, p_payment_method,
            p_checkout_id, p_payment_id, 'paid',
            p_customer_email, p_metadata
        );
    END IF;

    -- Resolve tier display title
    IF v_target_tier = 'vip' THEN
        v_tier_name := 'VIP Tier';
    ELSIF v_target_tier = 'pro' THEN
        v_tier_name := 'PRO Tier';
    ELSE
        v_tier_name := 'Free Tier';
    END IF;

    -- Send in-app notification to user
    BEGIN
        INSERT INTO public.notifications (
            user_id,
            title,
            message,
            type,
            is_read,
            created_at
        ) VALUES (
            p_user_id,
            'Payment Confirmed: Welcome to ' || v_tier_name || '!',
            'Your payment of ₱' || p_amount::TEXT || ' was successful. Your account limits have been unlocked until ' || TO_CHAR(v_new_expires_at, 'Mon DD, YYYY') || '.',
            'subscription_upgrade',
            false,
            NOW()
        );
    EXCEPTION WHEN OTHERS THEN
        -- Non-blocking if notifications table schema varies
        NULL;
    END;

    v_result := jsonb_build_object(
        'success', true,
        'user_id', p_user_id,
        'subscription_tier', v_target_tier,
        'subscription_expires_at', v_new_expires_at,
        'amount', p_amount
    );

    RETURN v_result;
END;
$$;
