// AgroMarket: FCM HTTP v1 Push Notification Dispatcher
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { getAdminClient } from "../_shared/supabaseClient.ts";

interface PushPayload {
  notification_id?: string;
  user_id: string;
  title: string;
  body: string;
  type: string;
  order_id?: string;
}

// OAuth2 Google Access Token generator for FCM HTTP v1
async function getGoogleAccessToken(serviceAccount: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };

  // Base64URL encode header and payload
  const b64Url = (obj: object) =>
    btoa(JSON.stringify(obj))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");

  const unsignedToken = `${b64Url({ alg: "RS256", typ: "JWT" })}.${b64Url(claim)}`;

  // Import PKCS8 private key
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const pemContents = serviceAccount.private_key
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s+/g, "");

  const binaryDerString = atob(pemContents);
  const binaryDer = new Uint8Array(binaryDerString.length);
  for (let i = 0; i < binaryDerString.length; i++) {
    binaryDer[i] = binaryDerString.charCodeAt(i);
  }

  const key = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsignedToken)
  );

  const sigB64Url = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");

  const jwt = `${unsignedToken}.${sigB64Url}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  const resData = await res.json();
  if (!res.ok) {
    throw new Error(`Failed to obtain Google OAuth access token: ${JSON.stringify(resData)}`);
  }
  return resData.access_token;
}

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const supabase = getAdminClient();

  try {
    const payload: PushPayload = await req.json();
    const { user_id, title, body, type, order_id } = payload;

    if (!user_id || !title) {
      return new Response(JSON.stringify({ error: "user_id and title required" }), { status: 400 });
    }

    // Retrieve active device tokens
    const { data: tokens, error: tokenErr } = await supabase
      .from("device_tokens")
      .select("token, platform")
      .eq("user_id", user_id);

    if (tokenErr || !tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ message: "No device tokens found for user" }), { status: 200 });
    }

    const serviceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
    if (!serviceAccountJson) {
      console.log(`[push-dispatch] (Mock/Dry-run) Notification to user ${user_id}: ${title} - ${body}`);
      return new Response(JSON.stringify({ success: true, dryRun: true }), { status: 200 });
    }

    const serviceAccount = JSON.parse(serviceAccountJson);
    const accessToken = await getGoogleAccessToken(serviceAccount);
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;

    const deadTokens: string[] = [];

    for (const tokenRecord of tokens) {
      const messagePayload = {
        message: {
          token: tokenRecord.token,
          notification: {
            title,
            body,
          },
          data: {
            type,
            order_id: order_id || "",
          },
          android: {
            priority: "high",
            notification: {
              sound: "default",
              click_action: "OPEN_APPLET",
            },
          },
        },
      };

      const res = await fetch(fcmUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`,
        },
        body: JSON.stringify(messagePayload),
      });

      if (!res.ok) {
        const errorJson = await res.json().catch(() => ({}));
        console.warn(`[push-dispatch] FCM error for token:`, errorJson);
        const errorCode = errorJson?.error?.details?.[0]?.errorCode;
        if (errorCode === "UNREGISTERED" || res.status === 404) {
          deadTokens.push(tokenRecord.token);
        }
      }
    }

    // Remove expired / invalid tokens
    if (deadTokens.length > 0) {
      await supabase
        .from("device_tokens")
        .delete()
        .eq("user_id", user_id)
        .in("token", deadTokens);
    }

    return new Response(JSON.stringify({ success: true, tokensSent: tokens.length - deadTokens.length }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    console.error("[push-dispatch] Exception:", errorMsg);
    return new Response(JSON.stringify({ error: errorMsg }), { status: 500 });
  }
});
