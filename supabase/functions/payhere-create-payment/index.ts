// AgroMarket: PayHere Payment Initiation Edge Function
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { getAdminClient } from "../_shared/supabaseClient.ts";
import md5 from "https://esm.sh/js-md5@0.8.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const supabase = getAdminClient();

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing authorization token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const token = authHeader.replace("Bearer ", "");
    const { data: { user }, error: authError } = await supabase.auth.getUser(token);
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Invalid user session" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { order_id } = await req.json();
    if (!order_id) {
      return new Response(JSON.stringify({ error: "order_id is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Query order
    const { data: order, error: orderErr } = await supabase
      .from("orders")
      .select("id, order_number, buyer_id, crop_name, quantity_kg, total_amount, status, expires_at")
      .eq("id", order_id)
      .single();

    if (orderErr || !order) {
      return new Response(JSON.stringify({ error: "Order not found" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (order.buyer_id !== user.id) {
      return new Response(JSON.stringify({ error: "Only the order buyer can initiate payment" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (order.status !== "accepted") {
      return new Response(JSON.stringify({ error: `Cannot pay for order in status '${order.status}'` }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (new Date(order.expires_at).getTime() <= Date.now()) {
      return new Response(JSON.stringify({ error: "Order payment window has expired" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Query buyer first name
    const { data: profile } = await supabase
      .from("profiles")
      .select("full_name")
      .eq("id", user.id)
      .single();

    const buyerFirstName = profile?.full_name ? profile.full_name.split(" ")[0] : "Customer";

    // Determine attempt count
    const { count: prevAttempts } = await supabase
      .from("payments")
      .select("*", { count: "exact", head: true })
      .eq("order_id", order_id);

    const attemptNumber = (prevAttempts || 0) + 1;
    const payhereOrderId = `${order.order_number}-${attemptNumber}`;
    const amountFormatted = Number(order.total_amount).toFixed(2);
    const currency = "LKR";

    const merchantId = Deno.env.get("PAYHERE_MERCHANT_ID") || "1234567";
    const merchantSecret = Deno.env.get("PAYHERE_MERCHANT_SECRET") || "secret";
    const notifyUrl = Deno.env.get("PAYHERE_NOTIFY_URL") || "https://your-project.functions.supabase.co/payhere-notify";

    // PayHere formula:
    // hash = UPPERCASE(md5(merchant_id + payhere_order_id + amount + currency + UPPERCASE(md5(merchant_secret))))
    const hashedSecret = md5(merchantSecret).toUpperCase();
    const hashString = `${merchantId}${payhereOrderId}${amountFormatted}${currency}${hashedSecret}`;
    const hash = md5(hashString).toUpperCase();

    // Insert payment record
    const { error: insertPayErr } = await supabase.from("payments").insert({
      order_id,
      attempt: attemptNumber,
      payhere_order_id: payhereOrderId,
      amount: order.total_amount,
      currency,
      status: "initiated",
    });

    if (insertPayErr) {
      throw insertPayErr;
    }

    return new Response(
      JSON.stringify({
        merchant_id: merchantId,
        payhere_order_id: payhereOrderId,
        amount: amountFormatted,
        currency,
        hash,
        notify_url: notifyUrl,
        item_description: `${order.quantity_kg} kg ${order.crop_name} (${order.order_number})`,
        buyer_first_name: buyerFirstName,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    console.error("[payhere-create-payment] Error:", errorMsg);
    return new Response(JSON.stringify({ error: errorMsg }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
