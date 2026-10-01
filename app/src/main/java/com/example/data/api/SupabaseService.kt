package com.example.data.api

import com.example.data.models.*
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

@JvmSuppressWildcards
interface SupabaseService {

    // Auth Endpoints
    @POST("auth/v1/otp")
    suspend fun sendOtp(@Body body: @JvmSuppressWildcards Map<String, String>): Response<Unit>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("auth/v1/token?grant_type=password")
    suspend fun loginWithPassword(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/get_or_create_phone_auth")
    suspend fun getOrCreatePhoneAuth(@Body body: @JvmSuppressWildcards Map<String, Any>): Response<@JvmSuppressWildcards Map<String, Any>>

    // Public Lookups
    @GET("rest/v1/districts?select=*&order=id.asc")
    suspend fun getDistricts(): Response<List<District>>

    @GET("rest/v1/cities")
    suspend fun getCities(@Query("district_id") districtFilter: String = "gt.0", @Query("select") select: String = "*", @Query("order") order: String = "name.asc"): Response<List<City>>

    @GET("rest/v1/crop_suggestions?select=*")
    suspend fun getCropSuggestions(): Response<List<CropSuggestion>>

    // Profiles & Farmer
    @GET("rest/v1/profiles")
    suspend fun getProfile(@Query("id") idFilter: String, @Query("select") select: String = "*"): Response<List<UserProfile>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(@Query("id") idFilter: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Unit>

    @GET("rest/v1/farmers")
    suspend fun getFarmer(@Query("user_id") idFilter: String, @Query("select") select: String = "*"): Response<List<FarmerRecord>>

    @GET("rest/v1/farmer_private")
    suspend fun getFarmerPrivate(@Query("user_id") idFilter: String, @Query("select") select: String = "*"): Response<List<FarmerPrivateData>>

    @PATCH("rest/v1/farmer_private")
    suspend fun updateFarmerPrivate(@Query("user_id") userFilter: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Unit>

    @GET("rest/v1/farmer_stats")
    suspend fun getFarmerStats(@Query("farmer_id") idFilter: String, @Query("select") select: String = "*"): Response<List<FarmerStats>>

    // RPC Functions
    @POST("rest/v1/rpc/register_profile")
    suspend fun registerProfile(@Body body: @JvmSuppressWildcards Map<String, Any>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/register_farmer")
    suspend fun registerFarmer(@Body body: @JvmSuppressWildcards Map<String, Any>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/search_listings")
    suspend fun searchListings(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<List<ListingItem>>

    @POST("rest/v1/rpc/create_order_request")
    suspend fun createOrderRequest(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<String>

    @POST("rest/v1/rpc/farmer_accept_order")
    suspend fun farmerAcceptOrder(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/farmer_reject_order")
    suspend fun farmerRejectOrder(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/buyer_cancel_order")
    suspend fun buyerCancelOrder(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/farmer_cancel_order")
    suspend fun farmerCancelOrder(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/farmer_mark_ready")
    suspend fun farmerMarkReady(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/farmer_mark_dispatched")
    suspend fun farmerMarkDispatched(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/farmer_mark_delivered")
    suspend fun farmerMarkDelivered(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/buyer_confirm_delivered")
    suspend fun buyerConfirmDelivered(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/buyer_raise_dispute")
    suspend fun buyerRaiseDispute(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<String>

    @POST("rest/v1/rpc/buyer_submit_review")
    suspend fun buyerSubmitReview(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<String>

    @POST("rest/v1/rpc/get_order_private_details")
    suspend fun getOrderPrivateDetails(@Body body: @JvmSuppressWildcards Map<String, String>): Response<List<OrderPrivateDetails>>

    // Listings
    @POST("rest/v1/rpc/create_farmer_listing")
    suspend fun createFarmerListing(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<ListingItem>

    @GET("rest/v1/listings")
    suspend fun getFarmerListings(@Query("farmer_id") farmerFilter: String, @Query("select") select: String = "*", @Query("order") order: String = "created_at.desc"): Response<List<ListingItem>>

    @GET("rest/v1/listings")
    suspend fun getListingsById(@Query("id") idFilter: String, @Query("select") select: String = "*"): Response<List<ListingItem>>

    @POST("rest/v1/listings")
    @Headers("Prefer: return=representation")
    suspend fun createListing(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<List<ListingItem>>

    @PATCH("rest/v1/listings")
    @Headers("Prefer: return=representation")
    suspend fun updateListing(@Query("id") idFilter: String, @Body body: @JvmSuppressWildcards Map<String, Any?>): Response<List<ListingItem>>

    @DELETE("rest/v1/listings")
    suspend fun deleteListing(@Query("id") idFilter: String): Response<Unit>

    // Orders
    @POST("rest/v1/orders")
    @Headers("Prefer: return=representation")
    suspend fun insertOrderDirect(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<List<OrderItem>>

    @GET("rest/v1/orders")
    suspend fun getOrders(@Query("select") select: String = "*", @QueryMap queryMap: @JvmSuppressWildcards Map<String, String>, @Query("order") order: String = "requested_at.desc"): Response<List<OrderItem>>

    @GET("rest/v1/orders")
    suspend fun getOrderById(@Query("id") idFilter: String, @Query("select") select: String = "*"): Response<List<OrderItem>>

    // Messages & Rebuilt Order Chat
    @POST("functions/v1/send-message")
    suspend fun sendChatMessageEdgeFunction(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<@JvmSuppressWildcards Map<String, Any?>>

    @POST("rest/v1/rpc/get_order_messages")
    suspend fun getOrderMessagesRpc(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<List<MessageItem>>

    @POST("rest/v1/rpc/mark_chat_read")
    suspend fun markChatRead(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Unit>

    @POST("rest/v1/rpc/get_chat_overview")
    suspend fun getChatOverview(@Body body: @JvmSuppressWildcards Map<String, Any?> = emptyMap()): Response<List<@JvmSuppressWildcards Map<String, Any?>>>

    @POST("rest/v1/rpc/send_chat_message")
    suspend fun sendChatMessage(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("rest/v1/rpc/create_or_get_inquiry_chat")
    suspend fun createOrGetInquiryChat(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<String>

    @POST("rest/v1/messages")
    suspend fun insertMessageDirect(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<Unit>

    @GET("rest/v1/messages")
    suspend fun getOrderMessages(@Query("order_id") orderFilter: String, @Query("select") select: String = "*", @Query("order") order: String = "created_at.asc"): Response<List<MessageItem>>

    @DELETE("rest/v1/messages")
    suspend fun deleteOrderMessages(@Query("order_id") orderFilter: String): Response<Unit>

    // Reviews
    @GET("rest/v1/reviews")
    suspend fun getFarmerReviews(@Query("farmer_id") farmerFilter: String, @Query("select") select: String = "*", @Query("order") order: String = "created_at.desc"): Response<List<ReviewItem>>

    // Notifications
    @GET("rest/v1/notifications")
    suspend fun getNotifications(@Query("user_id") userFilter: String, @Query("select") select: String = "*", @Query("order") order: String = "created_at.desc"): Response<List<NotificationItem>>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationRead(@Query("id") idFilter: String, @Body body: @JvmSuppressWildcards Map<String, String>): Response<Unit>

    // Device Tokens
    @POST("rest/v1/device_tokens")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun registerDeviceToken(@Body body: @JvmSuppressWildcards Map<String, String>): Response<Unit>

    // Edge Functions
    @POST("functions/v1/send-message")
    suspend fun sendMessage(@Body body: @JvmSuppressWildcards Map<String, String>): Response<@JvmSuppressWildcards Map<String, Any>>

    @POST("functions/v1/payhere-create-payment")
    suspend fun createPayHerePayment(@Body body: @JvmSuppressWildcards Map<String, String>): Response<PayHerePaymentRequest>
}
