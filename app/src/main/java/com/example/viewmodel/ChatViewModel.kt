package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.ListingItem
import com.example.data.models.MessageItem
import com.example.data.models.OrderItem
import com.example.data.repository.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChatUiState(
    val orderId: String = "",
    val orderNumber: String = "",
    val cropName: String = "",
    val orderStatus: String = "",
    val isReadOnly: Boolean = false,
    val readOnlyReason: String? = null,
    val isFarmer: Boolean = false,
    val isBuyer: Boolean = false,
    val isAuthorized: Boolean = true,
    val currentUserId: String = "",
    val listing: ListingItem? = null,
    val order: OrderItem? = null,
    val messages: List<MessageItem> = emptyList(), // Index 0 is newest (reverseLayout = true)
    val inputText: String = "",
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val isSending: Boolean = false,
    val warningBanner: String? = null,
    val moderationReasons: List<String> = emptyList(),
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val hasUnseenNewMessages: Boolean = false
)

class ChatViewModel(
    private val repository: AgroMarketRepository,
    private val chatRepository: ChatRepository = repository.chatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var realtimeJob: Job? = null
    private var isCurrentlyVisible = false

    companion object {
        val READ_ONLY_STATUSES = setOf("rejected", "cancelled", "expired", "completed", "refunded")

        fun isTerminalStatus(status: String?): Boolean {
            return status?.lowercase() in READ_ONLY_STATUSES
        }

        fun computeReadOnlyReason(status: String?): String {
            return when (status?.lowercase()) {
                "rejected" -> "Order request was rejected by the farmer. Chat is read-only."
                "cancelled" -> "Order was cancelled. Messaging is closed."
                "expired" -> "Order acceptance window expired. Chat is read-only."
                "completed" -> "Order has been completed and delivery confirmed. Chat is archived for reference."
                "refunded" -> "Order was refunded and closed. Chat is read-only."
                else -> "This order is finalized and read-only."
            }
        }

        fun currentUtcIsoString(): String {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        }
    }

    fun initChat(
        orderId: String,
        orderStatus: String,
        listing: ListingItem? = null,
        currentUserId: String? = null,
        isFarmerView: Boolean = false
    ) {
        val normalizedStatus = orderStatus.lowercase()
        val readOnly = isTerminalStatus(normalizedStatus)
        val reason = if (readOnly) computeReadOnlyReason(normalizedStatus) else null
        val resolvedUserId = currentUserId ?: ""

        _uiState.update {
            it.copy(
                orderId = orderId,
                orderStatus = orderStatus,
                isReadOnly = readOnly,
                readOnlyReason = reason,
                isFarmer = isFarmerView,
                isBuyer = !isFarmerView,
                isAuthorized = true,
                currentUserId = resolvedUserId,
                listing = listing,
                messages = emptyList(),
                inputText = "",
                warningBanner = null,
                errorMessage = null,
                isLoadingInitial = true,
                hasMorePages = true
            )
        }

        isCurrentlyVisible = true

        viewModelScope.launch {
            var targetOrderId = orderId

            // If navigating from pre-order inquiry, ensure consistent inquiry orderId
            if (orderId.startsWith("listing_") || orderId.startsWith("inq_") || orderStatus == "inquiry") {
                val listingId = when {
                    orderId.startsWith("listing_") -> orderId.removePrefix("listing_")
                    orderId.startsWith("inq_") -> orderId.removePrefix("inq_")
                    else -> (listing?.id ?: orderId)
                }
                targetOrderId = "inq_$listingId"
                _uiState.update { it.copy(orderId = targetOrderId) }
                val activeListing = _uiState.value.listing ?: listing
                if (activeListing != null) {
                    chatRepository.registerOrUpdateConversation(
                        orderId = targetOrderId,
                        listingId = activeListing.id,
                        cropName = activeListing.cropName,
                        pricePerKg = activeListing.pricePerKg,
                        quantityKg = activeListing.quantityAvailable,
                        status = "inquiry",
                        farmerId = activeListing.farmerId,
                        farmerName = activeListing.farmerFirstName ?: "Farmer",
                        listingPhoto = activeListing.photos.firstOrNull()
                    )
                }
            }

            // Load Order Details to verify buyer & farmer identities
            val orderRes = repository.getOrderById(targetOrderId)
            orderRes.onSuccess { ord ->
                val ordStatus = ord.status.lowercase()
                val isNowReadOnly = isTerminalStatus(ordStatus)
                val ordReason = if (isNowReadOnly) computeReadOnlyReason(ordStatus) else null

                val isActualBuyer = (resolvedUserId.isNotBlank() && resolvedUserId == ord.buyerId)
                val isActualFarmer = (resolvedUserId.isNotBlank() && resolvedUserId == ord.farmerId)

                val isParticipant = if (resolvedUserId.isBlank() ||
                    resolvedUserId.startsWith("demo_") ||
                    resolvedUserId.startsWith("farmer_me") ||
                    resolvedUserId.startsWith("usr_buyer_me") ||
                    targetOrderId.startsWith("inq_") ||
                    targetOrderId.startsWith("INQ-")) {
                    true
                } else {
                    isActualBuyer || isActualFarmer
                }

                val finalIsFarmer = if (isActualFarmer) true else if (isActualBuyer) false else isFarmerView
                val finalIsBuyer = !finalIsFarmer

                val loadedListing = if (_uiState.value.listing == null) {
                    repository.getListingById(ord.listingId).getOrNull() ?: ListingItem(
                        id = ord.listingId,
                        farmerId = ord.farmerId,
                        cropName = ord.cropName,
                        pricePerKg = ord.pricePerKg,
                        quantityAvailable = ord.quantityKg,
                        minOrderKg = 1.0,
                        harvestDate = ord.requestedDate,
                        photos = emptyList(),
                        farmerFirstName = "Farmer"
                    )
                } else {
                    _uiState.value.listing
                }

                _uiState.update {
                    it.copy(
                        order = ord,
                        orderNumber = ord.orderNumber,
                        cropName = ord.cropName,
                        listing = loadedListing,
                        isReadOnly = isNowReadOnly,
                        readOnlyReason = ordReason,
                        isFarmer = finalIsFarmer,
                        isBuyer = finalIsBuyer,
                        isAuthorized = isParticipant
                    )
                }
            }

            // Initial load of latest messages (keyset newest first)
            loadInitialMessages(targetOrderId)

            // Mark chat as read
            markChatRead(targetOrderId)

            // Start Realtime INSERT subscription with backoff
            startRealtimeSubscription(targetOrderId)
        }
    }

    private fun loadInitialMessages(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInitial = true, errorMessage = null) }
            val res = chatRepository.getOrderMessages(orderId = orderId, limit = 30)
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        messages = list,
                        isLoadingInitial = false,
                        hasMorePages = list.size >= 30,
                        isOffline = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingInitial = false,
                        errorMessage = err.message ?: "Failed to load chat messages",
                        isOffline = err is java.net.UnknownHostException || err is java.net.ConnectException
                    )
                }
            }
        }
    }

    /**
     * Keyset pagination: Loads older messages before the oldest current message.
     */
    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMorePages || state.messages.isEmpty()) return

        val oldestMsg = state.messages.lastOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val res = chatRepository.getOrderMessages(
                orderId = state.orderId,
                beforeCreatedAt = oldestMsg.createdAt,
                beforeId = oldestMsg.id,
                limit = 30
            )

            res.onSuccess { olderList ->
                _uiState.update { current ->
                    val existingIds = current.messages.map { it.id }.toSet()
                    val uniqueOlder = olderList.filter { it.id !in existingIds }
                    current.copy(
                        messages = current.messages + uniqueOlder,
                        isLoadingMore = false,
                        hasMorePages = olderList.size >= 30
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * Realtime subscription per order with automatic backoff reconnection and refetch on resume.
     */
    private fun startRealtimeSubscription(orderId: String) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            chatRepository.subscribeToOrderMessages(orderId).collect { incomingMsg ->
                handleIncomingMessage(incomingMsg)
            }
        }
    }

    private fun handleIncomingMessage(incomingMsg: MessageItem) {
        _uiState.update { state ->
            // Deduplicate by client_nonce or id
            val existingIndexByNonce = if (!incomingMsg.clientNonce.isNullOrBlank()) {
                state.messages.indexOfFirst { it.clientNonce == incomingMsg.clientNonce }
            } else -1

            val existingIndexById = state.messages.indexOfFirst { it.id == incomingMsg.id }

            val updatedMessages = when {
                existingIndexByNonce != -1 -> {
                    // Replace optimistic bubble with confirmed server message
                    state.messages.toMutableList().apply {
                        this[existingIndexByNonce] = incomingMsg.copy(isSending = false, sendFailed = false)
                    }
                }
                existingIndexById != -1 -> {
                    // Message already present, ignore or update delivery status
                    state.messages
                }
                else -> {
                    // Prepend new message at index 0 (newest)
                    listOf(incomingMsg) + state.messages
                }
            }

            val isCounterpartMsg = (incomingMsg.isMine != true) && incomingMsg.kind != "system"
            state.copy(
                messages = updatedMessages,
                hasUnseenNewMessages = isCounterpartMsg && !isCurrentlyVisible
            )
        }

        if (isCurrentlyVisible) {
            markChatRead(_uiState.value.orderId)
        }

        updateConversationLastMessage(
            incomingMsg.orderId,
            incomingMsg.body,
            incomingMsg.isMine == true || incomingMsg.senderRole == "me",
            incomingMsg.createdAt
        )
    }

    fun onInputTextChange(text: String) {
        if (text.length <= 1000) {
            _uiState.update { it.copy(inputText = text, warningBanner = null, errorMessage = null) }
        }
    }

    fun dismissWarningBanner() {
        _uiState.update { it.copy(warningBanner = null, moderationReasons = emptyList()) }
    }

    fun clearUnseenMessagesPill() {
        _uiState.update { it.copy(hasUnseenNewMessages = false) }
    }

    /**
     * Sends message with UUID nonce, optimistic bubble, and server response handling:
     * - 422: removes optimistic bubble and shows warning banner
     * - 409: flips chat to read-only
     * - 429: marks bubble with "too fast" message
     * - Other failure: preserves bubble with Retry action (same nonce!)
     */
    fun sendMessage(retryItem: MessageItem? = null) {
        val state = _uiState.value

        if (state.isReadOnly) {
            _uiState.update {
                it.copy(errorMessage = state.readOnlyReason ?: "This order chat is read-only. Messages cannot be sent.")
            }
            return
        }

        if (!state.isAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access restricted: Only the buyer and farmer of this order may participate in this chat.")
            }
            return
        }

        val textToSend = retryItem?.body ?: state.inputText.trim()
        if (textToSend.isBlank()) return

        val nonce = retryItem?.clientNonce ?: UUID.randomUUID().toString()
        val tempId = retryItem?.id ?: "temp_$nonce"

        // Optimistic bubble: placed at index 0 (newest)
        val role = if (state.isFarmer) "farmer" else "buyer"
        val optimisticMsg = MessageItem(
            id = tempId,
            orderId = state.orderId,
            senderId = state.currentUserId,
            kind = "user",
            body = textToSend,
            clientNonce = nonce,
            createdAt = retryItem?.createdAt ?: currentUtcIsoString(),
            isMine = true,
            senderRole = role,
            isSending = true,
            sendFailed = false,
            failError = null
        )

        _uiState.update { current ->
            val updated = if (retryItem != null) {
                current.messages.map { if (it.clientNonce == nonce) optimisticMsg else it }
            } else {
                listOf(optimisticMsg) + current.messages
            }
            current.copy(
                inputText = if (retryItem == null) "" else current.inputText,
                isSending = true,
                warningBanner = null,
                errorMessage = null,
                messages = updated
            )
        }

        val activeListing = state.listing
        chatRepository.registerOrUpdateConversation(
            orderId = state.orderId,
            orderNumber = state.orderNumber.ifBlank { "INQ-${Math.abs(state.orderId.hashCode()) % 900000 + 100000}" },
            listingId = activeListing?.id ?: state.orderId,
            cropName = state.cropName.ifBlank { activeListing?.cropName ?: "Produce" },
            pricePerKg = activeListing?.pricePerKg ?: 0.0,
            quantityKg = activeListing?.quantityAvailable ?: 0.0,
            status = if (state.orderStatus.isNotBlank()) state.orderStatus else "inquiry",
            buyerId = if (!state.isFarmer) state.currentUserId else (state.order?.buyerId ?: ""),
            farmerId = if (state.isFarmer) state.currentUserId else (activeListing?.farmerId ?: state.order?.farmerId ?: ""),
            buyerName = if (!state.isFarmer) "Buyer" else (state.order?.buyerId?.take(6) ?: "Buyer"),
            farmerName = if (state.isFarmer) "Farmer" else (activeListing?.farmerFirstName ?: "Farmer"),
            listingPhoto = activeListing?.photos?.firstOrNull(),
            lastMessage = textToSend,
            lastMessageTime = optimisticMsg.createdAt,
            senderRole = role
        )

        updateConversationLastMessage(state.orderId, textToSend, true, optimisticMsg.createdAt)

        viewModelScope.launch {
            val res = chatRepository.sendMessage(
                orderId = state.orderId,
                body = textToSend,
                clientNonce = nonce,
                senderId = state.currentUserId,
                senderRole = role
            )

            res.onSuccess { confirmedMsg ->
                _uiState.update { current ->
                    val replaced = current.messages.map { msg ->
                        if (msg.clientNonce == nonce) confirmedMsg.copy(isSending = false, sendFailed = false) else msg
                    }
                    current.copy(
                        isSending = false,
                        messages = replaced,
                        warningBanner = null,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                when (err) {
                    is ContentBlockedException -> {
                        // 422: Remove the bubble and show warning banner
                        _uiState.update { current ->
                            val withoutBlocked = current.messages.filter { it.clientNonce != nonce }
                            current.copy(
                                isSending = false,
                                messages = withoutBlocked,
                                warningBanner = err.message ?: "Sharing contact details or addresses is not allowed. Use in-app chat.",
                                moderationReasons = err.reasons
                            )
                        }
                    }
                    is OrderClosedException -> {
                        // 409: Flip to read-only and mark bubble as failed
                        _uiState.update { current ->
                            val withFail = current.messages.map { msg ->
                                if (msg.clientNonce == nonce) msg.copy(isSending = false, sendFailed = true, failError = "Order is closed") else msg
                            }
                            current.copy(
                                isSending = false,
                                isReadOnly = true,
                                readOnlyReason = err.message ?: computeReadOnlyReason(current.orderStatus),
                                messages = withFail
                            )
                        }
                    }
                    is RateLimitedException -> {
                        // 429: Mark bubble as failed with too fast message
                        _uiState.update { current ->
                            val withFail = current.messages.map { msg ->
                                if (msg.clientNonce == nonce) msg.copy(isSending = false, sendFailed = true, failError = "Too fast: max 20 msgs/min") else msg
                            }
                            current.copy(
                                isSending = false,
                                errorMessage = "You are sending messages too quickly. Limit is 20 per minute.",
                                messages = withFail
                            )
                        }
                    }
                    else -> {
                        // General failure: preserve bubble with Retry button (same nonce)
                        _uiState.update { current ->
                            val withFail = current.messages.map { msg ->
                                if (msg.clientNonce == nonce) msg.copy(isSending = false, sendFailed = true, failError = err.message ?: "Failed to deliver") else msg
                            }
                            current.copy(
                                isSending = false,
                                errorMessage = err.message ?: "Failed to deliver message. Tap retry.",
                                messages = withFail
                            )
                        }
                    }
                }
            }
        }
    }

    fun onResumeScreen() {
        isCurrentlyVisible = true
        clearUnseenMessagesPill()
        val orderId = _uiState.value.orderId
        if (orderId.isNotBlank()) {
            markChatRead(orderId)
            loadInitialMessages(orderId)
            startRealtimeSubscription(orderId)
        }
    }

    fun onPauseScreen() {
        isCurrentlyVisible = false
    }

    private fun markChatRead(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            chatRepository.markChatRead(orderId, isFarmer = _uiState.value.isFarmer)
        }
    }

    fun updateConversationLastMessage(orderId: String, text: String, isMine: Boolean, timestamp: String) {
        val displayMsg = if (isMine) "You: $text" else text
        _conversations.update { list ->
            val idx = list.indexOfFirst { it.orderId == orderId }
            if (idx != -1) {
                val updated = list.toMutableList()
                val old = updated[idx]
                updated.removeAt(idx)
                updated.add(0, old.copy(lastMessage = displayMsg, lastMessageTime = timestamp))
                updated
            } else {
                val state = _uiState.value
                val crop = state.cropName.ifBlank { state.listing?.cropName ?: "Produce" }
                val photo = state.listing?.photos?.firstOrNull()
                val isFarmer = state.isFarmer
                val newItem = com.example.data.models.ConversationItem(
                    orderId = orderId,
                    orderNumber = state.orderNumber.ifBlank {
                        if (orderId.startsWith("INQ-")) orderId else "INQ-${Math.abs(orderId.hashCode()) % 900000 + 100000}"
                    },
                    listingId = state.listing?.id ?: orderId,
                    cropName = crop,
                    pricePerKg = state.listing?.pricePerKg ?: 0.0,
                    quantityKg = state.listing?.quantityAvailable ?: 0.0,
                    status = if (state.orderStatus.isNotBlank()) state.orderStatus else "inquiry",
                    counterpartId = "",
                    counterpartName = if (isFarmer) "Buyer" else "Farmer",
                    lastMessage = displayMsg,
                    lastMessageTime = timestamp,
                    listingPhoto = photo,
                    isFarmerView = isFarmer,
                    unreadCount = 0
                )
                listOf(newItem) + list
            }
        }
    }

    // Conversations overview for MessagesScreen
    private val _conversations = MutableStateFlow<List<com.example.data.models.ConversationItem>>(emptyList())
    val conversations: StateFlow<List<com.example.data.models.ConversationItem>> = _conversations.asStateFlow()

    private val _isLoadingConversations = MutableStateFlow(false)
    val isLoadingConversations: StateFlow<Boolean> = _isLoadingConversations.asStateFlow()

    fun loadConversations(userId: String, isFarmer: Boolean) {
        viewModelScope.launch {
            _isLoadingConversations.value = true
            val overviewRes = chatRepository.getChatOverview()
            val serverItems = if (overviewRes.isSuccess && overviewRes.getOrNull() != null) {
                overviewRes.getOrNull()!!.mapNotNull { row ->
                    val ordId = row["order_id"]?.toString() ?: return@mapNotNull null
                    val ordNum = row["order_number"]?.toString() ?: "AM-${ordId.take(6)}"
                    val crop = row["crop_name"]?.toString() ?: "Produce"
                    val status = row["order_status"]?.toString() ?: "requested"
                    var lastMsg = row["last_message_body"]?.toString() ?: "Tap to view order chat"
                    var lastTime = row["last_message_at"]?.toString() ?: ""
                    val unreads = (row["unread_count"] as? Number)?.toInt() ?: 0

                    val localLatest = chatRepository.getLatestMessage(ordId)
                    if (localLatest != null) {
                        lastMsg = if (localLatest.isMine == true) "You: ${localLatest.body}" else localLatest.body
                        lastTime = localLatest.createdAt
                    }

                    com.example.data.models.ConversationItem(
                        orderId = ordId,
                        orderNumber = ordNum,
                        listingId = ordId,
                        cropName = crop,
                        pricePerKg = 0.0,
                        quantityKg = 0.0,
                        status = status,
                        counterpartId = "",
                        counterpartName = if (isFarmer) "Buyer" else "Farmer",
                        lastMessage = lastMsg,
                        lastMessageTime = lastTime,
                        listingPhoto = null,
                        isFarmerView = isFarmer,
                        unreadCount = unreads
                    )
                }
            } else {
                emptyList()
            }

            val fallbackRes = repository.getUserConversations(userId, isFarmer)
            val fallbackItems = fallbackRes.getOrDefault(emptyList())

            val serverOrderIds = serverItems.map { it.orderId }.toSet()
            val merged = (serverItems + fallbackItems.filter { it.orderId !in serverOrderIds }).toMutableList()

            // Merge local registered conversations from ChatRepository
            val localConversations = chatRepository.getLocalConversations(userId, isFarmer)
            for (loc in localConversations) {
                val idx = merged.indexOfFirst { it.orderId == loc.orderId }
                if (idx != -1) {
                    merged[idx] = loc
                } else {
                    merged.add(loc)
                }
            }

            // Preserve any existing active conversations created in-memory during this session
            val currentInMemory = _conversations.value
            for (curr in currentInMemory) {
                if (merged.none { it.orderId == curr.orderId }) {
                    merged.add(curr)
                }
            }

            // Also check all locally active orders in ChatRepository
            for (localOrderId in chatRepository.getAllLocalOrders()) {
                if (merged.none { it.orderId == localOrderId }) {
                    val latest = chatRepository.getLatestMessage(localOrderId)
                    if (latest != null) {
                        val isMine = if (isFarmer) latest.senderRole == "farmer" else latest.senderRole == "buyer"
                        merged.add(
                            com.example.data.models.ConversationItem(
                                orderId = localOrderId,
                                orderNumber = if (localOrderId.startsWith("INQ-")) localOrderId else "INQ-${Math.abs(localOrderId.hashCode()) % 900000 + 100000}",
                                listingId = localOrderId,
                                cropName = "Produce",
                                pricePerKg = 0.0,
                                quantityKg = 0.0,
                                status = "inquiry",
                                counterpartId = "",
                                counterpartName = if (isFarmer) "Buyer" else "Farmer",
                                lastMessage = if (isMine) "You: ${latest.body}" else latest.body,
                                lastMessageTime = latest.createdAt,
                                listingPhoto = null,
                                isFarmerView = isFarmer,
                                unreadCount = 0
                            )
                        )
                    }
                }
            }

            // Always update lastMessage and lastMessageTime to the most recent message
            val finalized = merged.map { item ->
                val localLatest = chatRepository.getLatestMessage(item.orderId)
                if (localLatest != null) {
                    val isMine = if (isFarmer) localLatest.senderRole == "farmer" else localLatest.senderRole == "buyer"
                    item.copy(
                        lastMessage = if (isMine) "You: ${localLatest.body}" else localLatest.body,
                        lastMessageTime = localLatest.createdAt
                    )
                } else {
                    item
                }
            }.sortedByDescending { it.lastMessageTime }

            _conversations.value = finalized
            _isLoadingConversations.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
