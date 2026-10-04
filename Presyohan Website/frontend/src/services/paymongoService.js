import { supabase } from '../config/supabaseClient';

// Public Key configuration (Safe for client-side)
const PAYMONGO_PUBLIC_KEY = import.meta.env.VITE_PAYMONGO_PUBLIC_KEY || '';

/**
 * Creates a PayMongo Checkout Session for a Presyohan subscription tier via Supabase Edge Function.
 * Note: Secret keys are securely stored on Supabase Secrets and NEVER stored in client-side code.
 */
export async function createCheckoutSession({
  userId,
  tierId,
  amount,
  planName,
  description,
  customerName,
  customerEmail,
  customerPhone,
  successUrl,
  cancelUrl
}) {
  const currentUrl = window.location.origin;
  const finalSuccessUrl = successUrl || `${currentUrl}/checkout?status=success&tier=${tierId}&uid=${userId || ''}`;
  const finalCancelUrl = cancelUrl || `${currentUrl}/checkout?status=cancelled&tier=${tierId}`;

  const payload = {
    user_id: userId,
    tier_id: tierId,
    amount: Number(amount),
    plan_name: planName || (tierId === 'vip' ? 'VIP Tier' : 'PRO Tier'),
    description: description || `Presyohan ${(tierId || 'pro').toUpperCase()} 1-Month Subscription`,
    customer_name: customerName || 'Presyohan User',
    customer_email: customerEmail || 'user@presyohan.com',
    customer_phone: customerPhone || '09170000000',
    success_url: finalSuccessUrl,
    cancel_url: finalCancelUrl
  };

  // Invoke Supabase Edge Function create-paymongo-checkout
  const { data, error } = await supabase.functions.invoke('create-paymongo-checkout', {
    body: payload
  });

  if (error) {
    let detailedMsg = error.message;
    try {
      if (error.context && typeof error.context.json === 'function') {
        const errorJson = await error.context.json();
        if (errorJson?.error) {
          detailedMsg = errorJson.error;
          if (errorJson.details?.errors?.[0]?.detail) {
            detailedMsg += `: ${errorJson.details.errors[0].detail}`;
          }
        }
      }
    } catch (_) {}
    console.error('PayMongo invocation error:', error, detailedMsg);
    throw new Error(detailedMsg || 'Unable to create checkout session with PayMongo.');
  }

  if (!data?.checkout_url) {
    throw new Error(data?.error || 'Unable to create checkout session with PayMongo.');
  }

  return {
    success: true,
    checkoutUrl: data.checkout_url,
    checkoutId: data.checkout_id
  };
}

/**
 * Verifies or manually activates a subscription for a given user & tier (e.g., test mode or post-checkout callback).
 */
export async function finalizeSubscriptionPayment({ userId, tierId, amount, paymentMethod = 'paymongo', checkoutId = null, paymentId = null }) {
  if (!userId || !tierId) {
    throw new Error('User ID and Tier ID are required.');
  }

  // 1. Call RPC in Supabase
  const { data, error } = await supabase.rpc('process_subscription_payment_success', {
    p_user_id: userId,
    p_tier_id: tierId,
    p_amount: Number(amount || 0),
    p_payment_method: paymentMethod,
    p_checkout_id: checkoutId,
    p_payment_id: paymentId
  });

  if (error) {
    // If RPC doesn't exist yet, fallback to direct table updates
    console.warn('RPC process_subscription_payment_success error, falling back to direct updates:', error);
    
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
    
    await supabase.from('app_users').update({
      subscription_tier: tierId.toLowerCase(),
      subscription_expires_at: expiresAt,
      updated_at: new Date().toISOString()
    }).eq('id', userId);

    await supabase.from('stores').update({
      subscription_tier: tierId.toLowerCase(),
      subscription_expires_at: expiresAt,
      updated_at: new Date().toISOString()
    }).eq('owner_id', userId);

    return {
      success: true,
      user_id: userId,
      subscription_tier: tierId,
      subscription_expires_at: expiresAt
    };
  }

  return data;
}
