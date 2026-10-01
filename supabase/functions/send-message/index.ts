// AgroMarket: Moderated Chat Dispatcher Edge Function
// Gateway for all order chat message dispatching. Clients never insert messages directly.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { getAdminClient } from "../_shared/supabaseClient.ts";
import { moderate } from "../_shared/moderation.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const supabase = getAdminClient();

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing authorization token", code: "UNAUTHORIZED" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Authenticate user via JWT
    const token = authHeader.replace(/^Bearer\s+/i, "");
    const { data: { user }, error: authError } = await supabase.auth.getUser(token);
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Invalid user session", code: "UNAUTHORIZED" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const payload = await req.json().catch(() => null);
    if (!payload) {
      return new Response(JSON.stringify({ error: "Invalid JSON body", code: "BAD_REQUEST" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { order_id, body, client_nonce } = payload;

    // Validate order_id
    if (!order_id || typeof order_id !== "string" || !UUID_REGEX.test(order_id)) {
      return new Response(JSON.stringify({ error: "Valid UUID order_id is required", code: "BAD_REQUEST" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Validate body
    if (!body || typeof body !== "string") {
      return new Response(JSON.stringify({ error: "String body is required", code: "BAD_REQUEST" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const trimmedBody = body.trim();
    if (trimmedBody.length < 1 || trimmedBody.length > 1000) {
      return new Response(JSON.stringify({ error: "Message length must be between 1 and 1000 characters", code: "BAD_REQUEST" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Validate client_nonce
    if (!client_nonce || typeof client_nonce !== "string" || !UUID_REGEX.test(client_nonce)) {
      return new Response(JSON.stringify({ error: "Valid UUID client_nonce is required", code: "BAD_REQUEST" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Server-side moderation check (Never log message bodies)
    const modResult = moderate(trimmedBody);

    if (modResult.blocked) {
      // Record blocked attempt into flagged_messages audit log via service_role RPC
      await supabase.rpc("record_blocked_message", {
        p_order_id: order_id,
        p_sender_id: user.id,
        p_original_body: trimmedBody,
        p_reasons: modResult.reasons,
      }).catch((e: unknown) => {
        console.error("[send-message] Failed to record blocked message audit:", e);
      });

      // HTTP 422: CONTENT_BLOCKED (body is NOT stored or broadcast)
      return new Response(
        JSON.stringify({
          error: "Sharing contact details or addresses is not allowed. Use in-app chat.",
          code: "CONTENT_BLOCKED",
          reasons: modResult.reasons,
        }),
        {
          status: 422,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    // Clean message: invoke service_role only chat_send function in a single transaction
    const { data: sendResult, error: sendError } = await supabase.rpc("chat_send", {
      p_order_id: order_id,
      p_sender_id: user.id,
      p_body: trimmedBody,
      p_client_nonce: client_nonce,
    });

    if (sendError) {
      const errMessage = sendError.message || "";

      if (errMessage.includes("NOT_A_PARTY")) {
        return new Response(
          JSON.stringify({ error: "You are not a participant in this order.", code: "NOT_A_PARTY" }),
          { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      if (errMessage.includes("ORDER_CLOSED")) {
        return new Response(
          JSON.stringify({ error: "Chat is closed because the order has ended.", code: "ORDER_CLOSED" }),
          { status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      if (errMessage.includes("SUSPENDED")) {
        return new Response(
          JSON.stringify({ error: "Your account is suspended. Chat is disabled.", code: "SUSPENDED" }),
          { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      if (errMessage.includes("RATE_LIMITED")) {
        return new Response(
          JSON.stringify({ error: "Rate limit exceeded. Please wait a minute before sending more messages.", code: "RATE_LIMITED" }),
          { status: 429, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      console.error("[send-message] Database RPC error:", errMessage);
      return new Response(
        JSON.stringify({ error: "Failed to send message", code: "DATABASE_ERROR" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ success: true, message: sendResult }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    console.error("[send-message] Unexpected error:", errorMsg);
    return new Response(JSON.stringify({ error: "Internal server error", code: "INTERNAL_ERROR" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
