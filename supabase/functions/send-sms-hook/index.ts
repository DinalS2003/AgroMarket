// AgroMarket: Supabase Send SMS Auth Hook
// Dispatches verification codes using sendOtpSms adapter
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { sendOtpSms } from "../_shared/otpProvider.ts";

interface SmsPayload {
  user: {
    id: string;
    phone: string;
  };
  sms: {
    otp: string;
  };
}

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const rawBody = await req.text();
    const data: SmsPayload = JSON.parse(rawBody);

    const phone = data.user?.phone;
    const otpCode = data.sms?.otp;

    if (!phone || !otpCode) {
      return new Response(JSON.stringify({ error: "Missing phone or otp" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    console.log(`[send-sms-hook] Processing OTP request for ${phone}`);
    const result = await sendOtpSms(phone);

    return new Response(JSON.stringify({ success: true, uid: result.uid }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    console.error("[send-sms-hook] Error:", errorMsg);
    return new Response(JSON.stringify({ error: errorMsg }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
