package com.example.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class District(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "province") val province: String
)

@JsonClass(generateAdapter = true)
data class City(
    @Json(name = "id") val id: Int,
    @Json(name = "district_id") val districtId: Int,
    @Json(name = "name") val name: String,
    @Json(name = "postal_code") val postalCode: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "id") val id: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "district_id") val districtId: Int,
    @Json(name = "city_id") val cityId: Int,
    @Json(name = "is_suspended") val isSuspended: Boolean = false,
    @Json(name = "suspended_reason") val suspendedReason: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class FarmerRecord(
    @Json(name = "user_id") val userId: String,
    @Json(name = "cultivation_district_id") val cultivationDistrictId: Int,
    @Json(name = "cultivation_city_id") val cultivationCityId: Int,
    @Json(name = "main_crops") val mainCrops: List<String> = emptyList(),
    @Json(name = "land_size") val landSize: Double? = null,
    @Json(name = "land_unit") val landUnit: String? = null,
    @Json(name = "default_pickup_landmark") val defaultPickupLandmark: String? = null
)

@JsonClass(generateAdapter = true)
data class FarmerPrivateData(
    @Json(name = "cultivation_address") val cultivationAddress: String,
    @Json(name = "bank_name") val bankName: String,
    @Json(name = "bank_branch") val bankBranch: String,
    @Json(name = "account_holder_name") val accountHolderName: String,
    @Json(name = "account_number") val accountNumber: String
)

@JsonClass(generateAdapter = true)
data class ListingItem(
    @Json(name = "id") val id: String,
    @Json(name = "farmer_id") val farmerId: String,
    @Json(name = "crop_name") val cropName: String,
    @Json(name = "crop_name_key") val cropNameKey: String? = null,
    @Json(name = "quantity_available") val quantityAvailable: Double,
    @Json(name = "price_per_kg") val pricePerKg: Double,
    @Json(name = "min_order_kg") val minOrderKg: Double,
    @Json(name = "harvest_date") val harvestDate: String,
    @Json(name = "photos") val photos: List<String> = emptyList(),
    @Json(name = "is_available_now") val isAvailableNow: Boolean = true,
    @Json(name = "farmer_first_name") val farmerFirstName: String? = null,
    @Json(name = "cultivation_district_name") val cultivationDistrictName: String? = null,
    @Json(name = "cultivation_city_name") val cultivationCityName: String? = null,
    @Json(name = "rating_avg") val ratingAvg: Double = 0.0,
    @Json(name = "rating_count") val ratingCount: Int = 0,
    @Json(name = "reliability_score") val reliabilityScore: Double = 0.0,
    @Json(name = "is_top_farmer") val isTopFarmer: Boolean = false,
    @Json(name = "completed_orders") val completedOrders: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class OrderItem(
    @Json(name = "id") val id: String,
    @Json(name = "order_number") val orderNumber: String,
    @Json(name = "buyer_id") val buyerId: String,
    @Json(name = "farmer_id") val farmerId: String,
    @Json(name = "listing_id") val listingId: String,
    @Json(name = "crop_name") val cropName: String,
    @Json(name = "price_per_kg") val pricePerKg: Double,
    @Json(name = "quantity_kg") val quantityKg: Double,
    @Json(name = "subtotal") val subtotal: Double,
    @Json(name = "delivery_method") val deliveryMethod: String, // 'buyer_arranged' | 'farmer_delivery'
    @Json(name = "farmer_delivery_mode") val farmerDeliveryMode: String? = null, // 'own_transport' | 'pickme'
    @Json(name = "delivery_fee") val deliveryFee: Double = 0.0,
    @Json(name = "commission_rate") val commissionRate: Double = 0.03,
    @Json(name = "commission_amount") val commissionAmount: Double = 0.0,
    @Json(name = "total_amount") val totalAmount: Double = 0.0,
    @Json(name = "farmer_payout_amount") val farmerPayoutAmount: Double = 0.0,
    @Json(name = "requested_date") val requestedDate: String,
    @Json(name = "delivery_district_id") val deliveryDistrictId: Int? = null,
    @Json(name = "delivery_city_id") val deliveryCityId: Int? = null,
    @Json(name = "status") val status: String,
    @Json(name = "stock_reserved") val stockReserved: Boolean = false,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "requested_at") val requestedAt: String? = null,
    @Json(name = "accepted_at") val acceptedAt: String? = null,
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "ready_at") val readyAt: String? = null,
    @Json(name = "dispatched_at") val dispatchedAt: String? = null,
    @Json(name = "delivered_at") val deliveredAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "ended_at") val endedAt: String? = null,
    @Json(name = "ended_by") val endedBy: String? = null,
    @Json(name = "end_reason") val endReason: String? = null,
    @Json(name = "payout_status") val payoutStatus: String = "none",
    @Json(name = "payout_amount") val payoutAmount: Double = 0.0,
    @Json(name = "refund_status") val refundStatus: String = "none",
    @Json(name = "refund_amount") val refundAmount: Double = 0.0,
    @Json(name = "is_on_time") val isOnTime: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class OrderPrivateDetails(
    @Json(name = "delivery_address") val deliveryAddress: String? = null,
    @Json(name = "pickup_landmark") val pickupLandmark: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageItem(
    @Json(name = "id") val id: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "sender_id") val senderId: String? = null,
    @Json(name = "kind") val kind: String = "user",
    @Json(name = "body") val body: String,
    @Json(name = "client_nonce") val clientNonce: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "is_mine") val isMine: Boolean? = null,
    @Json(name = "sender_role") val senderRole: String? = null,
    val isSending: Boolean = false,
    val sendFailed: Boolean = false,
    val failError: String? = null
)

@JsonClass(generateAdapter = true)
data class DisputeItem(
    @Json(name = "id") val id: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "raised_by") val raisedBy: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "photos") val photos: List<String> = emptyList(),
    @Json(name = "status") val status: String,
    @Json(name = "resolution") val resolution: String? = null,
    @Json(name = "refund_amount") val refundAmount: Double? = 0.0,
    @Json(name = "admin_note") val adminNote: String? = null,
    @Json(name = "resolved_at") val resolvedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ReviewItem(
    @Json(name = "id") val id: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "farmer_id") val farmerId: String,
    @Json(name = "buyer_id") val buyerId: String,
    @Json(name = "rating") val rating: Int,
    @Json(name = "comment") val comment: String? = null,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class FarmerStats(
    @Json(name = "farmer_id") val farmerId: String,
    @Json(name = "completed_orders") val completedOrders: Int = 0,
    @Json(name = "terminal_accepted_orders") val terminalAcceptedOrders: Int = 0,
    @Json(name = "fulfilled_orders") val fulfilledOrders: Int = 0,
    @Json(name = "fulfillment_rate") val fulfillmentRate: Double = 0.0,
    @Json(name = "delivered_orders") val deliveredOrders: Int = 0,
    @Json(name = "on_time_orders") val onTimeOrders: Int = 0,
    @Json(name = "on_time_rate") val onTimeRate: Double = 0.0,
    @Json(name = "rating_avg") val ratingAvg: Double = 0.0,
    @Json(name = "rating_count") val ratingCount: Int = 0,
    @Json(name = "reliability_score") val reliabilityScore: Double = 0.0,
    @Json(name = "is_top_farmer") val isTopFarmer: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NotificationItem(
    @Json(name = "id") val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "type") val type: String,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String,
    @Json(name = "order_id") val orderId: String? = null,
    @Json(name = "read_at") val readAt: String? = null,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class PayHerePaymentRequest(
    @Json(name = "merchant_id") val merchantId: String,
    @Json(name = "payhere_order_id") val payhereOrderId: String,
    @Json(name = "amount") val amount: String,
    @Json(name = "currency") val currency: String,
    @Json(name = "hash") val hash: String,
    @Json(name = "notify_url") val notifyUrl: String,
    @Json(name = "item_description") val itemDescription: String,
    @Json(name = "buyer_first_name") val buyerFirstName: String
)

@JsonClass(generateAdapter = true)
data class CropSuggestion(
    @Json(name = "crop_name_key") val cropNameKey: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "listing_count") val listingCount: Int
)

@JsonClass(generateAdapter = true)
data class ConversationItem(
    @Json(name = "order_id") val orderId: String,
    @Json(name = "order_number") val orderNumber: String,
    @Json(name = "listing_id") val listingId: String,
    @Json(name = "crop_name") val cropName: String,
    @Json(name = "price_per_kg") val pricePerKg: Double,
    @Json(name = "quantity_kg") val quantityKg: Double,
    @Json(name = "status") val status: String,
    @Json(name = "counterpart_id") val counterpartId: String,
    @Json(name = "counterpart_name") val counterpartName: String,
    @Json(name = "last_message") val lastMessage: String,
    @Json(name = "last_message_time") val lastMessageTime: String,
    @Json(name = "listing_photo") val listingPhoto: String? = null,
    @Json(name = "is_farmer_view") val isFarmerView: Boolean = false,
    @Json(name = "unread_count") val unreadCount: Int = 0
)

enum class AppMode {
    BUYING,
    SELLING
}
