// AgroMarket: SMS OTP Provider Adapter for Text.lk v3 OTP API
// Sends OTP using POST https://app.text.lk/api/v3/sms/send with type="otp"

// =========================================================================
// TEMPORARY DEVELOPMENT OTP CONFIGURATION
// Set DEV_MODE_DISABLE_REAL_SMS to false when enabling real Text.lk SMS dispatch.
// =========================================================================
export const DEV_MODE_DISABLE_REAL_SMS = true;
export const DEV_STATIC_OTP = "123456";

export interface TextLkOtpResult {
  success: boolean;
  otp?: string;
  uid?: string;
  error?: string;
}

export async function sendOtpSms(phoneE164: string): Promise<TextLkOtpResult> {
  // Check development mode bypass
  if (DEV_MODE_DISABLE_REAL_SMS) {
    console.log(`[Text.lk DEV MODE] Real SMS dispatch disabled. Using static OTP: ${DEV_STATIC_OTP}`);
    return {
      success: true,
      otp: DEV_STATIC_OTP,
      uid: `dev_otp_${Date.now()}`,
    };
  }

  const apiUrl = Deno.env.get("OTP_API_URL") || "https://app.text.lk/api/v3/sms/send";
  const apiKey = Deno.env.get("TEXTLK_API_TOKEN") || Deno.env.get("OTP_API_KEY") || "";
  const senderId = Deno.env.get("TEXTLK_SENDER_ID") || Deno.env.get("OTP_SENDER_ID") || "TextLKDemo";

  if (!apiKey) {
    throw new Error("Text.lk API token must be configured in environment");
  }

  // Format phone to 947XXXXXXXX for Text.lk
  const cleanDigits = phoneE164.replace(/[^\d]/g, "");
  const formattedPhone = cleanDigits.startsWith("0")
    ? "94" + cleanDigits.substring(1)
    : cleanDigits.startsWith("94")
      ? cleanDigits
      : "94" + cleanDigits;

  const response = await fetch(apiUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept": "application/json",
      "Authorization": `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      recipient: formattedPhone,
      sender_id: senderId,
      type: "otp",
      message: "Your AgroMarket verification code is: {{OTP6}}",
    }),
  });

  const responseData = await response.json();

  if (!response.ok || responseData.status !== "success") {
    const errorMsg = responseData.message || `Text.lk failed with status ${response.status}`;
    console.error(`[Text.lk OTP Gateway Error]`, errorMsg);
    throw new Error(errorMsg);
  }

  const generatedOtp = responseData.data?.otp?.toString();
  return {
    success: true,
    otp: generatedOtp,
    uid: responseData.data?.uid,
  };
}
