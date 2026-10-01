package com.example.data.api

import com.example.BuildConfig
import com.example.data.local.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var sessionManager: SessionManager? = null

    fun init(session: SessionManager) {
        sessionManager = session
    }

    fun notifySessionExpired() {
        sessionManager?.notifySessionExpired()
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Plain client specifically for refresh token requests to avoid authInterceptor recursion
    private val rawHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val refreshLock = Any()

    val cleanAnonKey: String
        get() {
            var k = BuildConfig.SUPABASE_ANON_KEY.trim()
            while (k.startsWith("SUPABASE_ANON_KEY=", ignoreCase = true) || k.startsWith("SUPABASE_ANON_KEY =", ignoreCase = true)) {
                k = k.substringAfter("=").trim()
            }
            k = k.removeSurrounding("\"").removeSurrounding("'").trim()
            if (k.isBlank() || k == "your_supabase_anon_key_here") {
                k = "sb_publishable_n2zQAa9SMGD9BrqayYfcVQ_VfwU1ItX"
            }
            return k
        }

    val cleanBaseUrl: String
        get() {
            var u = BuildConfig.SUPABASE_URL.trim()
            while (u.startsWith("SUPABASE_URL=", ignoreCase = true) || u.startsWith("SUPABASE_URL =", ignoreCase = true)) {
                u = u.substringAfter("=").trim()
            }
            u = u.removeSurrounding("\"").removeSurrounding("'").trim()
            if (u.isBlank() || u.contains("your-project-ref")) {
                u = "https://wqqvikjsfbcprueivxzn.supabase.co"
            }
            return if (u.endsWith("/")) u else "$u/"
        }

    private fun loginWithCredentials(email: String, pass: String): String? {
        return try {
            val loginUrl = "${cleanBaseUrl}auth/v1/token?grant_type=password"
            val loginPayload = JSONObject().apply {
                put("email", email)
                put("password", pass)
            }.toString()

            val loginReq = Request.Builder()
                .url(loginUrl)
                .header("apikey", cleanAnonKey)
                .header("Authorization", "Bearer $cleanAnonKey")
                .header("Content-Type", "application/json")
                .post(loginPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val loginRes = rawHttpClient.newCall(loginReq).execute()
            if (loginRes.isSuccessful && loginRes.body != null) {
                val loginJson = JSONObject(loginRes.body!!.string())
                val newAccess = loginJson.optString("access_token")
                val newRefresh = loginJson.optString("refresh_token")
                val expiresIn = loginJson.optLong("expires_in", 2592000L)
                if (newAccess.isNotBlank()) {
                    runBlocking {
                        sessionManager?.updateTokens(newAccess, newRefresh, expiresIn)
                    }
                    return newAccess
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun refreshSessionToken(refreshToken: String): String? {
        synchronized(refreshLock) {
            return try {
                val refreshUrl = "${cleanBaseUrl}auth/v1/token?grant_type=refresh_token"
                val jsonPayload = JSONObject().apply {
                    put("refresh_token", refreshToken)
                }.toString()

                val request = Request.Builder()
                    .url(refreshUrl)
                    .header("apikey", cleanAnonKey)
                    .header("Authorization", "Bearer $cleanAnonKey")
                    .header("Content-Type", "application/json")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val res = rawHttpClient.newCall(request).execute()
                if (res.isSuccessful && res.body != null) {
                    val resStr = res.body!!.string()
                    val json = JSONObject(resStr)
                    val newAccess = json.optString("access_token")
                    val newRefresh = json.optString("refresh_token", refreshToken)
                    val expiresIn = json.optLong("expires_in", 2592000L)
                    if (newAccess.isNotBlank()) {
                        runBlocking {
                            sessionManager?.updateTokens(newAccess, newRefresh, expiresIn)
                        }
                        return newAccess
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun reAuthenticateSessionWithPhone(): String? {
        val phone = runBlocking { sessionManager?.getUserPhone() } ?: return null
        if (phone.isBlank()) return null
        val cleanDigits = phone.replace(Regex("[^0-9]"), "").let {
            if (it.startsWith("0")) "94" + it.substring(1) else if (it.startsWith("7")) "94$it" else it
        }
        val normalizedE164 = if (cleanDigits.startsWith("+")) cleanDigits else "+$cleanDigits"

        // 1. Try direct deterministic credentials first
        val fallbackEmail = "p${cleanDigits.trimStart('+')}@agromarket.lk"
        val fallbackPass = "AgroPass_${cleanDigits.trimStart('+')}!"
        val directResult = loginWithCredentials(fallbackEmail, fallbackPass)
        if (!directResult.isNullOrBlank()) {
            return directResult
        }

        // 2. Try get_or_create_phone_auth RPC
        return try {
            val rpcUrl = "${cleanBaseUrl}rest/v1/rpc/get_or_create_phone_auth"
            val rpcPayload = JSONObject().apply {
                put("p_phone_e164", normalizedE164)
            }.toString()

            val rpcReq = Request.Builder()
                .url(rpcUrl)
                .header("apikey", cleanAnonKey)
                .header("Authorization", "Bearer $cleanAnonKey")
                .header("Content-Type", "application/json")
                .post(rpcPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val rpcRes = rawHttpClient.newCall(rpcReq).execute()
            if (rpcRes.isSuccessful && rpcRes.body != null) {
                val rpcJson = JSONObject(rpcRes.body!!.string())
                val email = rpcJson.optString("email", fallbackEmail)
                val password = rpcJson.optString("password", fallbackPass)
                if (email.isNotBlank() && password.isNotBlank()) {
                    return loginWithCredentials(email, password)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun renewSession(): String? {
        synchronized(refreshLock) {
            // 1. Try refresh token
            val refreshToken = runBlocking { sessionManager?.getRefreshToken() }
            if (!refreshToken.isNullOrBlank()) {
                val fromRefresh = refreshSessionToken(refreshToken)
                if (!fromRefresh.isNullOrBlank()) return fromRefresh
            }

            // 2. Try stored email & password
            val email = runBlocking { sessionManager?.getUserEmail() }
            val pass = runBlocking { sessionManager?.getUserPassword() }
            if (!email.isNullOrBlank() && !pass.isNullOrBlank()) {
                val fromCreds = loginWithCredentials(email, pass)
                if (!fromCreds.isNullOrBlank()) return fromCreds
            }

            // 3. Try re-authentication via phone
            val fromPhone = reAuthenticateSessionWithPhone()
            if (!fromPhone.isNullOrBlank()) return fromPhone

            return null
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val isAuthEndpoint = path.contains("auth/v1/token") || path.contains("get_or_create_phone_auth")

        val anonKey = cleanAnonKey
        var userToken = runBlocking { sessionManager?.getAuthToken() }

        // Proactively renew session if token is missing, expired, or near expiration (within 10 minutes)
        if (!isAuthEndpoint && !userToken.isNullOrBlank() && userToken.contains(".")) {
            val expiresAt = runBlocking { sessionManager?.getTokenExpiresAt() }
            if (expiresAt != null && System.currentTimeMillis() > (expiresAt - 600_000L)) {
                val freshToken = renewSession()
                if (!freshToken.isNullOrBlank()) {
                    userToken = freshToken
                }
            }
        }

        val builder = original.newBuilder()
        builder.header("apikey", anonKey)
        val bearerToken = if (!userToken.isNullOrEmpty() && userToken.contains(".")) userToken else anonKey
        builder.header("Authorization", "Bearer $bearerToken")

        var response = chain.proceed(builder.build())

        // Check if response indicates token expiration (HTTP 401 or JWT expired error body)
        var isExpired = (response.code == 401 && !isAuthEndpoint)
        if (!isExpired && !response.isSuccessful && !isAuthEndpoint) {
            val peek = try { response.peekBody(1024).string() } catch (e: Exception) { "" }
            if (peek.contains("jwt expired", ignoreCase = true) ||
                peek.contains("PGRST301", ignoreCase = true) ||
                peek.contains("token expired", ignoreCase = true)) {
                isExpired = true
            }
        }

        if (isExpired) {
            // Attempt seamless session renewal
            val newAccessToken = renewSession()

            if (!newAccessToken.isNullOrBlank()) {
                response.close()
                val retryRequest = original.newBuilder()
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }

            // Both refresh and re-authentication failed: session has definitively expired.
            // Prompt requirement: "when it expires dont show the text jwt expired, just redirect to the login screen"
            runBlocking {
                sessionManager?.clearSession()
            }
            sessionManager?.notifySessionExpired()

            // Return sanitized 401 response with clean empty json so "jwt expired" text is NEVER parsed or shown anywhere
            response.close()
            return@Interceptor okhttp3.Response.Builder()
                .request(original)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Session Expired")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = cleanBaseUrl

    val service: SupabaseService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseService::class.java)
    }
}
