package com.example.data.api.textlk

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface TextLkService {

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("sms/send")
    suspend fun sendSms(
        @Header("Authorization") authorization: String,
        @Body request: TextLkSendSmsRequest
    ): Response<TextLkApiResponse<TextLkSmsData>>

    @Headers("Accept: application/json")
    @GET("balance")
    suspend fun getBalance(
        @Header("Authorization") authorization: String
    ): Response<TextLkApiResponse<TextLkBalanceData>>
}
