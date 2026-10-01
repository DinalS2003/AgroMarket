package com.example.data.repository

import com.example.data.api.ApiClient
import com.example.data.api.SupabaseService
import com.example.data.local.SessionManager
import com.example.data.models.MessageItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ContentBlockedException(message: String, val reasons: List<String>) : Exception(message)
class OrderClosedException(message: String) : Exception(message)
class RateLimitedException(message: String) : Exception(message)
class NotAPartyException(message: String) : Exception(message)
class SuspendedChatException(message: String) : Exception(message)

class ChatRepository(
    private val service: SupabaseService = ApiClient.service,
    private val sessionManager: SessionManager
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val rawClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val localMessagesByOrder = java.util.concurrent.ConcurrentHashMap<String, MutableList<MessageItem>>()
    private val localMessageFlow = MutableSharedFlow<MessageItem>(extraBufferCapacity = 64)

    data class LocalConversationThread(
        val orderId: String,
        val orderNumber: String,
        val listingId: String,
        val cropName: String,
        val pricePerKg: Double,
        val quantityKg: Double,
        val status: String,
        val buyerId: String,
        val farmerId: String,
        val buyerName: String,
        val farmerName: String,
        val listingPhoto: String?,
        var lastMessage: String,
        var lastMessageTime: String,
        var lastSenderRole: String,
        var unreadForBuyer: Int = 0,
        var unreadForFarmer: Int = 0
    )

    private val localConversations = java.util.concurrent.ConcurrentHashMap<String, LocalConversationThread>()

    fun registerOrUpdateConversation(
        orderId: String,
        orderNumber: String = "",
        listingId: String = "",
        cropName: String = "",
        pricePerKg: Double = 0.0,
        quantityKg: Double = 0.0,
        status: String = "inquiry",
        buyerId: String = "",
        farmerId: String = "",
        buyerName: String = "",
        farmerName: String = "",
        listingPhoto: String? = null,
        lastMessage: String = "",
        lastMessageTime: String = "",
        senderRole: String = "buyer"
    ) {
        val nowIso = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
        val existing = localConversations[orderId]
        if (existing != null) {
            if (lastMessage.isNotBlank()) {
                existing.lastMessage = lastMessage
                existing.lastMessageTime = lastMessageTime.ifBlank { nowIso }
                existing.lastSenderRole = senderRole
                if (senderRole == "buyer") {
                    existing.unreadForFarmer += 1
                } else if (senderRole == "farmer") {
                    existing.unreadForBuyer += 1
                }
            }
            if (cropName.isNotBlank() && existing.cropName == "Produce") {
                localConversations[orderId] = existing.copy(
                    cropName = cropName,
                    pricePerKg = if (pricePerKg > 0) pricePerKg else existing.pricePerKg,
                    quantityKg = if (quantityKg > 0) quantityKg else existing.quantityKg,
                    listingPhoto = listingPhoto ?: existing.listingPhoto
                )
            }
        } else {
            val resolvedOrderNumber = orderNumber.ifBlank {
                if (orderId.startsWith("INQ-")) orderId else "INQ-${Math.abs(orderId.hashCode()) % 900000 + 100000}"
            }
            val newConv = LocalConversationThread(
                orderId = orderId,
                orderNumber = resolvedOrderNumber,
                listingId = listingId.ifBlank { orderId },
                cropName = cropName.ifBlank { "Produce" },
                pricePerKg = pricePerKg,
                quantityKg = quantityKg,
                status = status,
                buyerId = buyerId,
                farmerId = farmerId,
                buyerName = buyerName.ifBlank { "Buyer" },
                farmerName = farmerName.ifBlank { "Farmer" },
                listingPhoto = listingPhoto,
                lastMessage = lastMessage,
                lastMessageTime = lastMessageTime.ifBlank { nowIso },
                lastSenderRole = senderRole,
                unreadForBuyer = if (senderRole == "farmer") 1 else 0,
                unreadForFarmer = if (senderRole == "buyer") 1 else 0
            )
            localConversations[orderId] = newConv
        }
    }

    fun markConversationRead(orderId: String, isFarmer: Boolean) {
        val conv = localConversations[orderId] ?: return
        if (isFarmer) {
            conv.unreadForFarmer = 0
        } else {
            conv.unreadForBuyer = 0
        }
    }

    fun getLocalConversations(userId: String, isFarmer: Boolean): List<com.example.data.models.ConversationItem> {
        return localConversations.values.map { conv ->
            val counterpartId = if (isFarmer) conv.buyerId else conv.farmerId
            val counterpartName = if (isFarmer) conv.buyerName.ifBlank { "Buyer" } else conv.farmerName.ifBlank { "Farmer" }
            val isMineLast = if (isFarmer) conv.lastSenderRole == "farmer" else conv.lastSenderRole == "buyer"
            val displayLast = if (isMineLast && conv.lastMessage.isNotBlank()) "You: ${conv.lastMessage}" else conv.lastMessage
            val unreadCount = if (isFarmer) conv.unreadForFarmer else conv.unreadForBuyer

            com.example.data.models.ConversationItem(
                orderId = conv.orderId,
                orderNumber = conv.orderNumber,
                listingId = conv.listingId,
                cropName = conv.cropName,
                pricePerKg = conv.pricePerKg,
                quantityKg = conv.quantityKg,
                status = conv.status,
                counterpartId = counterpartId,
                counterpartName = counterpartName,
                lastMessage = displayLast,
                lastMessageTime = conv.lastMessageTime,
                listingPhoto = conv.listingPhoto,
                isFarmerView = isFarmer,
                unreadCount = unreadCount
            )
        }.sortedByDescending { it.lastMessageTime }
    }

    private fun parseMessageItem(
        map: Map<*, *>,
        fallbackOrderId: String,
        fallbackBody: String,
        fallbackNonce: String,
        currentUid: String?,
        fallbackSenderRole: String? = null
    ): MessageItem {
        val id = map["id"]?.toString() ?: "msg_${System.currentTimeMillis()}"
        val ordId = map["order_id"]?.toString() ?: fallbackOrderId
        val senderId = map["sender_id"]?.toString() ?: currentUid
        val kind = map["kind"]?.toString() ?: "user"
        val msgBody = map["body"]?.toString() ?: fallbackBody
        val nonce = map["client_nonce"]?.toString() ?: fallbackNonce
        val createdAt = map["created_at"]?.toString() ?: java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
        val sRole = map["sender_role"]?.toString() ?: fallbackSenderRole ?: (if (senderId?.contains("3") == true || senderId?.contains("farmer") == true) "farmer" else "buyer")
        return MessageItem(
            id = id,
            orderId = ordId,
            senderId = senderId,
            kind = kind,
            body = msgBody,
            clientNonce = nonce,
            createdAt = createdAt,
            isMine = null,
            senderRole = sRole,
            isSending = false,
            sendFailed = false
        )
    }

    private fun recordLocalMessage(orderId: String, item: MessageItem) {
        val list = localMessagesByOrder.getOrPut(orderId) { mutableListOf() }
        synchronized(list) {
            val existingIdx = list.indexOfFirst {
                it.id == item.id || (!it.clientNonce.isNullOrBlank() && it.clientNonce == item.clientNonce)
            }
            if (existingIdx != -1) {
                list[existingIdx] = item
            } else {
                list.add(0, item)
            }
        }
        localMessageFlow.tryEmit(item)
    }

    fun getLatestMessage(orderId: String): MessageItem? {
        val list = localMessagesByOrder[orderId] ?: return null
        return synchronized(list) {
            list.maxByOrNull { it.createdAt }
        }
    }

    fun getAllLocalOrders(): Set<String> {
        return (localMessagesByOrder.keys + localConversations.keys).toSet()
    }

    fun getOrderMessagesLocal(orderId: String): List<MessageItem> {
        val list = localMessagesByOrder[orderId] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    suspend fun getOrderMessages(
        orderId: String,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int = 30
    ): Result<List<MessageItem>> = withContext(Dispatchers.IO) {
        val localList = localMessagesByOrder[orderId]?.toList() ?: emptyList()
        val currentUid = sessionManager.getUserId()

        try {
            val params = mutableMapOf<String, Any?>("p_order_id" to orderId, "p_limit" to limit)
            if (!beforeCreatedAt.isNullOrBlank()) {
                params["p_before_created_at"] = beforeCreatedAt
            }
            if (!beforeId.isNullOrBlank()) {
                params["p_before_id"] = beforeId
            }

            var remoteMessages: List<MessageItem>? = null
            try {
                val rpcRes = service.getOrderMessagesRpc(params)
                if (rpcRes.isSuccessful && rpcRes.body() != null) {
                    remoteMessages = rpcRes.body()!!
                }
            } catch (_: Exception) {}

            if (remoteMessages == null) {
                try {
                    val tableRes = service.getOrderMessages(
                        orderFilter = "eq.$orderId",
                        order = "created_at.desc"
                    )
                    if (tableRes.isSuccessful && tableRes.body() != null) {
                        remoteMessages = tableRes.body()!!.map { msg ->
                            val sRole = if (msg.kind == "system" || msg.senderId == null) "system"
                            else if (msg.senderId == currentUid) "me"
                            else if (msg.senderId?.contains("3") == true || msg.senderId?.contains("farmer") == true) "farmer"
                            else "buyer"
                            msg.copy(
                                isMine = (currentUid != null && msg.senderId == currentUid),
                                senderRole = sRole
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            val merged = if (remoteMessages != null) {
                val remoteIds = remoteMessages.map { it.id }.toSet()
                val remoteNonces = remoteMessages.mapNotNull { it.clientNonce }.toSet()
                val additionalLocal = localList.filter { it.id !in remoteIds && (it.clientNonce == null || it.clientNonce !in remoteNonces) }
                (additionalLocal + remoteMessages).sortedByDescending { it.createdAt }
            } else {
                localList
            }

            if (merged.isNotEmpty()) {
                return@withContext Result.success(merged)
            }

            Result.success(emptyList())
        } catch (e: Exception) {
            if (localList.isNotEmpty()) {
                Result.success(localList)
            } else {
                Result.success(emptyList())
            }
        }
    }

    suspend fun sendMessage(
        orderId: String,
        body: String,
        clientNonce: String = java.util.UUID.randomUUID().toString(),
        senderId: String? = null,
        senderRole: String = "buyer"
    ): Result<MessageItem> = withContext(Dispatchers.IO) {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) {
            return@withContext Result.failure(Exception("Message cannot be empty"))
        }

        // 1. Client-Side Content Moderation Check
        val digits = trimmedBody.replace(Regex("[^0-9]"), "")
        val lower = trimmedBody.lowercase()
        val hasPhone = digits.length >= 9 && (lower.contains("07") || lower.contains("94") || digits.length >= 10)
        val hasExternalChat = lower.contains("whatsapp") || lower.contains("viber") || lower.contains("telegram") || lower.contains("wa.me")

        if (hasPhone || hasExternalChat) {
            val reasons = mutableListOf<String>()
            if (hasPhone) reasons.add("direct_phone_number")
            if (hasExternalChat) reasons.add("external_messaging_app")
            return@withContext Result.failure(
                ContentBlockedException(
                    "Sharing contact details or phone numbers is not allowed. Use in-app chat.",
                    reasons
                )
            )
        }

        val currentUid = senderId ?: sessionManager.getUserId() ?: ""
        val isUuidOrder = orderId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
        val isUuidNonce = clientNonce.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
        val safeNonce = if (isUuidNonce) clientNonce else java.util.UUID.randomUUID().toString()

        // 2. Attempt Edge Function if orderId is a valid UUID
        if (isUuidOrder) {
            try {
                val payload = mapOf(
                    "order_id" to orderId,
                    "body" to trimmedBody,
                    "client_nonce" to safeNonce
                )
                val response = service.sendChatMessageEdgeFunction(payload)
                val code = response.code()

                if (response.isSuccessful && response.body() != null) {
                    val resMap = response.body()!!
                    val msgObj = resMap["message"] as? Map<*, *> ?: resMap
                    val item = parseMessageItem(msgObj, orderId, trimmedBody, safeNonce, currentUid, senderRole)
                    recordLocalMessage(orderId, item)
                    return@withContext Result.success(item)
                }

                if (code == 422) {
                    val errorBodyStr = response.errorBody()?.string() ?: ""
                    val jsonErr = try { JSONObject(errorBodyStr) } catch (_: Exception) { JSONObject() }
                    val reasonsArr = jsonErr.optJSONArray("reasons")
                    val reasonsList = mutableListOf<String>()
                    if (reasonsArr != null) {
                        for (i in 0 until reasonsArr.length()) reasonsList.add(reasonsArr.optString(i))
                    }
                    return@withContext Result.failure(
                        ContentBlockedException(
                            jsonErr.optString("error", "Sharing contact details or addresses is not allowed. Use in-app chat."),
                            reasonsList
                        )
                    )
                } else if (code == 409) {
                    return@withContext Result.failure(OrderClosedException("Chat is closed because the order has ended."))
                } else if (code == 429) {
                    return@withContext Result.failure(RateLimitedException("Rate limit exceeded. Please wait a minute before sending more messages."))
                } else if (code == 403) {
                    val errorBodyStr = response.errorBody()?.string() ?: ""
                    val jsonErr = try { JSONObject(errorBodyStr) } catch (_: Exception) { JSONObject() }
                    return@withContext Result.failure(Exception(jsonErr.optString("error", "You are not a participant in this order.")))
                } else {
                    val errorBodyStr = response.errorBody()?.string() ?: ""
                    val jsonErr = try { JSONObject(errorBodyStr) } catch (_: Exception) { JSONObject() }
                    val errMessage = jsonErr.optString("error", "Failed to send message: HTTP $code")
                    return@withContext Result.failure(Exception(errMessage))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.failure(IllegalArgumentException("Invalid orderId: Must be a valid UUID to send message via edge function"))
    }

    suspend fun markChatRead(orderId: String, isFarmer: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        markConversationRead(orderId, isFarmer)
        try {
            val res = service.markChatRead(mapOf("p_order_id" to orderId))
            if (res.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to mark chat as read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatOverview(): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val res = service.getChatOverview()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch chat overview"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Subscribes to Realtime INSERT updates for an order using Supabase Realtime WebSocket
     * with exponential backoff resubscription and resilient fallback polling.
     */
    fun subscribeToOrderMessages(orderId: String): Flow<MessageItem> = callbackFlow {
        val isRunning = AtomicBoolean(true)
        val seenIds = mutableSetOf<String>()
        var webSocket: WebSocket? = null
        var backoffMs = 1000L
        val maxBackoffMs = 16000L

        // 1. Supabase Realtime WebSocket Listener
        fun connectWebSocket() {
            if (!isRunning.get()) return

            val cleanUrl = ApiClient.cleanBaseUrl.removeSuffix("/")
            val wsBaseUrl = cleanUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
            val token = runBlocking { sessionManager.getAuthToken() } ?: ApiClient.cleanAnonKey
            val wsUrl = "$wsBaseUrl/realtime/v1/websocket?apikey=${ApiClient.cleanAnonKey}&vsn=1.0.0"

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("apikey", ApiClient.cleanAnonKey)
                .build()

            webSocket = rawClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                    backoffMs = 1000L // Reset backoff on successful connection
                    // Join order chat channel
                    val joinPayload = JSONObject().apply {
                        put("topic", "realtime:public:messages:order_id=eq.$orderId")
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("broadcast", JSONObject().apply { put("self", false) })
                                put("presence", JSONObject().apply { put("key", "") })
                                put("postgres_changes", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "INSERT")
                                        put("schema", "public")
                                        put("table", "messages")
                                        put("filter", "order_id=eq.$orderId")
                                    })
                                })
                            })
                            put("user_token", token)
                        })
                        put("ref", "1")
                    }.toString()
                    ws.send(joinPayload)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        val event = json.optString("event")
                        if (event == "postgres_changes" || event == "INSERT") {
                            val payload = json.optJSONObject("payload")
                            val data = payload?.optJSONObject("data") ?: payload
                            val record = data?.optJSONObject("record") ?: data
                            if (record != null) {
                                val id = record.optString("id")
                                val recOrderId = record.optString("order_id")
                                if (recOrderId == orderId && id.isNotBlank() && seenIds.add(id)) {
                                    val currentUid = runBlocking { sessionManager.getUserId() }
                                    val senderId = record.optString("sender_id").ifBlank { null }
                                    val kind = record.optString("kind", "user")
                                    val item = MessageItem(
                                        id = id,
                                        orderId = recOrderId,
                                        senderId = senderId,
                                        kind = kind,
                                        body = record.optString("body"),
                                        clientNonce = record.optString("client_nonce").ifBlank { null },
                                        createdAt = record.optString("created_at"),
                                        isMine = (currentUid != null && senderId == currentUid),
                                        senderRole = if (kind == "system" || senderId == null) "system" else if (senderId == currentUid) "me" else "counterpart"
                                    )
                                    trySend(item)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    if (isRunning.get()) {
                        // Exponential backoff reconnect
                        val delayMs = backoffMs
                        backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(delayMs)
                            connectWebSocket()
                        }
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    if (isRunning.get()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(backoffMs)
                            connectWebSocket()
                        }
                    }
                }
            })
        }

        connectWebSocket()

        // 2. Local Message Flow Bridge
        val localJob = CoroutineScope(Dispatchers.IO).launch {
            localMessageFlow.collect { m ->
                if (m.orderId == orderId && seenIds.add(m.id)) {
                    trySend(m)
                }
            }
        }

        // 3. Resilient Polling Fallback (runs in tandem to ensure messages are never lost during reconnects)
        val pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning.get()) {
                delay(3000L)
                try {
                    val res = getOrderMessages(orderId = orderId, limit = 15)
                    res.onSuccess { msgs ->
                        msgs.forEach { m ->
                            if (seenIds.add(m.id)) {
                                trySend(m)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            isRunning.set(false)
            localJob.cancel()
            pollingJob.cancel()
            webSocket?.cancel()
        }
    }
}
