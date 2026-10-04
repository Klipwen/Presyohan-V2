// Supabase Edge Function: create-paymongo-checkout
// Creates a PayMongo Checkout Session for Presyohan PRO/VIP Subscriptions

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const paymongoSecretKey = Deno.env.get("PAYMONGO_SECRET_KEY");
    if (!paymongoSecretKey) {
      return new Response(
        JSON.stringify({ error: "Server configuration error: PAYMONGO_SECRET_KEY secret is not set." }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const supabase = createClient(supabaseUrl, supabaseServiceKey || supabaseAnonKey);

    const body = await req.json();
    const {
      user_id,
      tier_id,
      amount,
      plan_name,
      description,
      customer_name,
      customer_email,
      customer_phone,
      success_url,
      cancel_url
    } = body;

    const effectiveUserId = user_id || (customer_email ? `user_${customer_email.replace(/[^a-zA-Z0-9]/g, '_')}` : `guest_${Date.now()}`);
    const effectiveTierId = tier_id || "pro";
    const effectiveAmount = Number(amount) > 0 ? Number(amount) : (effectiveTierId === "vip" ? 299 : 99);

    const amountInCentavos = Math.round(effectiveAmount * 100);
    const tierNameUpper = effectiveTierId.toUpperCase();
    const lineItemTitle = `Presyohan ${tierNameUpper} Subscription`;
    const lineItemDesc = description || `1 Month ${tierNameUpper} Plan subscription with full capacity unlocks.`;

    const authHeader = `Basic ${btoa(paymongoSecretKey.trim() + ":")}`;

    // Clean phone number if present (PayMongo requires clean digits or omit)
    const cleanedPhone = (customer_phone || "").replace(/[^0-9+]/g, "");
    const billingObj: Record<string, string> = {
      name: (customer_name || "Presyohan Subscriber").trim(),
      email: (customer_email || "subscriber@presyohan.com").trim(),
    };
    if (cleanedPhone.length >= 10) {
      billingObj.phone = cleanedPhone;
    }

    const paymongoPayload = {
      data: {
        attributes: {
          billing: billingObj,
          send_email_receipt: true,
          show_description: true,
          show_line_items: true,
          line_items: [
            {
              amount: amountInCentavos,
              currency: "PHP",
              name: lineItemTitle,
              quantity: 1,
              description: lineItemDesc,
            },
          ],
          payment_method_types: [
            "gcash",
            "paymaya",
            "card",
            "grab_pay",
            "qrph"
          ],
          description: `Presyohan ${tierNameUpper} - ₱${effectiveAmount}/mo`,
          success_url: success_url || `https://presyohan.onrender.com/checkout?status=success&tier=${effectiveTierId}&uid=${effectiveUserId}`,
          cancel_url: cancel_url || `https://presyohan.onrender.com/checkout?status=cancelled&tier=${effectiveTierId}`,
          metadata: {
            user_id: effectiveUserId,
            tier_id: effectiveTierId,
            plan_name: plan_name || tierNameUpper,
            environment: paymongoSecretKey.startsWith("sk_live") ? "live" : "test"
          }
        }
      }
    };

    const paymongoRes = await fetch("https://api.paymongo.com/v1/checkout_sessions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": authHeader,
      },
      body: JSON.stringify(paymongoPayload),
    });

    const paymongoData = await paymongoRes.json();

    if (!paymongoRes.ok || !paymongoData.data) {
      console.error("PayMongo API error:", paymongoData);
      return new Response(
        JSON.stringify({ error: "PayMongo session creation failed", details: paymongoData }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const checkoutSession = paymongoData.data;
    const checkoutUrl = checkoutSession.attributes.checkout_url;
    const checkoutId = checkoutSession.id;

    // Log pending payment row in supabase
    try {
      await supabase.from("subscription_payments").insert({
        user_id: user_id,
        tier_id: tier_id,
        amount: Number(amount),
        currency: "PHP",
        payment_method: "paymongo",
        paymongo_checkout_id: checkoutId,
        status: "pending",
        customer_name: customer_name,
        customer_email: customer_email,
        customer_phone: customer_phone,
        metadata: {
          checkout_url: checkoutUrl,
          created_via: "create-paymongo-checkout"
        }
      });
    } catch (dbErr) {
      console.warn("Could not record pending payment row:", dbErr);
    }

    return new Response(
      JSON.stringify({
        success: true,
        checkout_url: checkoutUrl,
        checkout_id: checkoutId,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("Unhandled error:", err);
    return new Response(
      JSON.stringify({ error: err.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
