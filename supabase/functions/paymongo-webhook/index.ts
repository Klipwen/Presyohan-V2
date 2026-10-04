// Supabase Edge Function: paymongo-webhook
// Secure Webhook Listener for PayMongo checkout.payment.paid events

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

    const supabase = createClient(supabaseUrl, supabaseServiceKey || supabaseAnonKey);

    const payloadText = await req.text();
    const event = JSON.parse(payloadText);

    const eventType = event?.data?.attributes?.type;
    const eventData = event?.data?.attributes?.data;

    console.log(`Received PayMongo Webhook Event: ${eventType}`, JSON.stringify(eventData?.id));

    if (eventType === "checkout_session.payment.paid" || eventType === "payment.paid") {
      let userId: string | null = null;
      let tierId: string = "pro";
      let amount: number = 99;
      let checkoutId: string | null = null;
      let paymentId: string | null = null;
      let paymentMethod: string = "paymongo";
      let customerEmail: string | null = null;

      if (eventType === "checkout_session.payment.paid") {
        checkoutId = eventData?.id ?? null;
        const attributes = eventData?.attributes ?? {};
        const metadata = attributes.metadata ?? {};

        userId = metadata.user_id ?? null;
        tierId = metadata.tier_id ?? "pro";

        const payments = attributes.payments ?? [];
        if (payments.length > 0) {
          const firstPay = payments[0];
          paymentId = firstPay?.id ?? null;
          const centavos = firstPay?.attributes?.amount ?? 0;
          if (centavos > 0) amount = centavos / 100;
          paymentMethod = firstPay?.attributes?.source?.type ?? "paymongo";
        }

        const billing = attributes.billing ?? {};
        customerEmail = billing.email ?? null;
      } else if (eventType === "payment.paid") {
        paymentId = eventData?.id ?? null;
        const attributes = eventData?.attributes ?? {};
        const metadata = attributes.metadata ?? {};

        userId = metadata.user_id ?? null;
        tierId = metadata.tier_id ?? "pro";
        const centavos = attributes.amount ?? 0;
        if (centavos > 0) amount = centavos / 100;
        paymentMethod = attributes.source?.type ?? "paymongo";
      }

      // If user_id wasn't directly in metadata, look up pending record by checkoutId
      if (!userId && checkoutId) {
        const { data: existingPayment } = await supabase
          .from("subscription_payments")
          .select("user_id, tier_id, amount")
          .eq("paymongo_checkout_id", checkoutId)
          .maybeSingle();

        if (existingPayment) {
          userId = existingPayment.user_id;
          tierId = existingPayment.tier_id || tierId;
          amount = existingPayment.amount || amount;
        }
      }

      if (userId) {
        // Execute atomic subscription activation RPC
        const { data: rpcRes, error: rpcErr } = await supabase.rpc(
          "process_subscription_payment_success",
          {
            p_user_id: userId,
            p_tier_id: tierId,
            p_amount: amount,
            p_payment_method: paymentMethod,
            p_checkout_id: checkoutId,
            p_payment_id: paymentId,
            p_customer_email: customerEmail,
            p_metadata: {
              raw_event_type: eventType,
              received_at: new Date().toISOString()
            }
          }
        );

        if (rpcErr) {
          console.error("RPC Error processing subscription upgrade:", rpcErr);
          return new Response(JSON.stringify({ error: rpcErr.message }), {
            status: 500,
            headers: { "Content-Type": "application/json" }
          });
        }

        console.log(`Successfully upgraded user ${userId} to ${tierId} plan:`, rpcRes);
      } else {
        console.warn("Could not determine user_id for paid checkout session:", checkoutId);
      }
    }

    return new Response(JSON.stringify({ received: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  } catch (err) {
    console.error("Webhook processing exception:", err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" }
    });
  }
});
