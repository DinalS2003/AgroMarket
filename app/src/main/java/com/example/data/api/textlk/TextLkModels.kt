package com.example.data.api.textlk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TextLkSendSmsRequest(
    @Json(name = "recipient") val recipient: String,
    @Json(name = "sender_id") val senderId: String = "TextLKDemo",
    @Json(name = "type") val type: String = "otp",
    @Json(name = "message") val message: String = "Your AgroMarket verification code is: {{OTP6}}"
)

@JsonClass(generateAdapter = true)
data class TextLkSmsData(
    @Json(name = "uid") val uid: String? = null,
    @Json(name = "to") val to: String? = null,
    @Json(name = "from") val from: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "cost") val cost: String? = null,
    @Json(name = "sms_count") val smsCount: Int? = null,
    @Json(name = "otp") val otpRaw: Any? = null
) {
    val otp: String?
        get() = when (otpRaw) {
            is String -> otpRaw.trim().padStart(6, '0')
            is Number -> otpRaw.toLong().toString().padStart(6, '0')
            null -> null
            else -> otpRaw.toString().trim().padStart(6, '0')
        }
}

@JsonClass(generateAdapter = true)
data class TextLkApiResponse<T>(
    @Json(name = "status") val status: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: T? = null
)

@JsonClass(generateAdapter = true)
data class TextLkBalanceData(
    @Json(name = "remaining_balance") val remainingBalance: String? = null,
    @Json(name = "expired_on") val expiredOn: String? = null
)

data class SendOtpResult(
    val success: Boolean,
    val httpStatusCode: Int? = null,
    val referenceId: String? = null,
    val isRealSmsDispatched: Boolean = false,
    val errorMessage: String? = null,
    val displayMessage: String = ""
)
