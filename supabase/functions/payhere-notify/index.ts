// AgroMarket: PayHere Server-to-Server Webhook Handler
// Securely verifies signature and transitions order states
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { getAdminClient } from "../_shared/supabaseClient.ts";
import md5 from "https://esm.sh/js-md5@0.8.3";

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const supabase = getAdminClient();

  try {
    let params: Record<string, string> = {};
    const contentType = req.headers.get("content-type") || "";

    if (contentType.includes("application/x-www-form-urlencoded")) {
      const formData = await req.formData();
      for (const [key, value] of formData.entries()) {
        params[key] = String(value);
      }
    } else {
      params = await req.json();
    }

    const merchantId = params["merchant_id"];
    const orderId = params["order_id"]; // payhere_order_id (e.g. AM-001001-1)
    const payhereAmount = params["payhere_amount"];
    const payhereCurrency = params["payhere_currency"];
    const statusCode = params["status_code"];
    const md5sig = params["md5sig"];
    const paymentId = params["payment_id"];

    if (!merchantId || !orderId || !payhereAmount || !payhereCurrency || !statusCode || !md5sig) {
      console.error("[payhere-notify] Missing parameters", params);
      return new Response("Missing parameters", { status: 400 });
    }

    const expectedMerchantId = Deno.env.get("PAYHERE_MERCHANT_ID") || "1234567";
    const merchantSecret = Deno.env.get("PAYHERE_MERCHANT_SECRET") || "secret";

    if (merchantId !== expectedMerchantId) {
      console.error(`[payhere-notify] Merchant ID mismatch: received ${merchantId}`);
      return new Response("Invalid merchant", { status: 400 });
    }

    // Verify md5sig:
    // md5sig = UPPERCASE(md5(merchant_id + order_id + payhere_amount + payhere_currency + status_code + UPPERCASE(md5(merchant_secret))))
    const hashedSecret = md5(merchantSecret).toUpperCase();
    const sigString = `${merchantId}${orderId}${payhereAmount}${payhereCurrency}${statusCode}${hashedSecret}`;
    const calculatedSig = md5(sigString).toUpperCase();

    if (calculatedSig !== md5sig.toUpperCase()) {
      console.error(`[payhere-notify] Invalid signature. Calculated: ${calculatedSig}, received: ${md5sig}`);
      return new Response("Invalid signature", { status: 400 });
    }

    // Find payment record
    const { data: payment, error: payErr } = await supabase
      .from("payments")
      .select("id, order_id, amount, currency, status")
      .eq("payhere_order_id", orderId)
      .single();

    if (payErr || !payment) {
      console.error(`[payhere-notify] Payment record not found for ${orderId}`);
      return new Response("Payment not found", { status: 404 });
    }

    // Amount and currency validation
    const expectedAmount = Number(payment.amount).toFixed(2);
    const receivedAmount = Number(payhereAmount).toFixed(2);

    if (expectedAmount !== receivedAmount || payment.currency !== payhereCurrency) {
      console.error(`[payhere-notify] Amount/currency mismatch: ${expectedAmount} ${payment.currency} vs ${receivedAmount} ${payhereCurrency}`);
      return new Response("Amount/Currency mismatch", { status: 400 });
    }

    // Idempotency: if already processed, return 200 immediately
    if (payment.status === "success" && statusCode === "2") {
      return new Response("OK - Idempotent", { status: 200 });
    }

    const numStatus = parseInt(statusCode, 10);

    // Map status code:
    // 2 = Success
    // 0 = Pending
    // -1 = Cancelled
    // -2 = Failed
    // -3 = Chargedback
    let statusText = "initiated";
    if (numStatus === 2) statusText = "success";
    else if (numStatus === 0) statusText = "initiated";
    else if (numStatus === -1) statusText = "cancelled";
    else if (numStatus === -2) statusText = "failed";
    else if (numStatus === -3) statusText = "chargedback";

    // Update payment row
    await supabase
      .from("payments")
      .update({
        status: statusText,
        payhere_payment_id: paymentId,
        status_code: numStatus,
        updated_at: new Date().toISOString(),
      })
      .eq("id", payment.id);

    if (numStatus === 2) {
      // Call mark_order_paid RPC
      const { data: rpcRes, error: rpcErr } = await supabase.rpc("mark_order_paid", {
        p_order_id: payment.order_id,
        p_payhere_payment_id: paymentId,
        p_amount: Number(payhereAmount),
        p_status_code: numStatus,
      });

      if (rpcErr) {
        console.error("[payhere-notify] RPC mark_order_paid error:", rpcErr);
        return new Response("Database error processing payment", { status: 500 });
      }

      console.log(`[payhere-notify] Order ${payment.order_id} marked paid successfully:`, rpcRes);
    } else if (numStatus === -3) {
      // Chargeback: put refund and payout on hold, notify admins
      await supabase
        .from("orders")
        .update({
          payout_status: "held",
          refund_status: "required",
          updated_at: new Date().toISOString(),
        })
        .eq("id", payment.order_id);

      const { data: admins } = await supabase.from("admin_users").select("user_id");
      if (admins) {
        const alerts = admins.map((a: { user_id: string }) => ({
          user_id: a.user_id,
          type: "chargeback_alert",
          title: "Payment Chargeback Alert",
          body: `Chargeback received for payment ${paymentId} on order. Payout held.`,
          order_id: payment.order_id,
        }));
        await supabase.from("notifications").insert(alerts);
      }
    }

    return new Response("OK", { status: 200 });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    console.error("[payhere-notify] Exception:", errorMsg);
    return new Response(`Server error: ${errorMsg}`, { status: 500 });
  }
});
