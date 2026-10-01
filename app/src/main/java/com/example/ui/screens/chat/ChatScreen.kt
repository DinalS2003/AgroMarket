package com.example.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.data.models.ListingItem
import com.example.data.models.MessageItem
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.formatLkr
import com.example.viewmodel.ChatViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val COLOMBO_ZONE: ZoneId = ZoneId.of("Asia/Colombo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    orderId: String,
    orderStatus: String,
    currentUserId: String,
    isFarmerView: Boolean,
    listing: ListingItem? = null,
    viewModel: ChatViewModel,
    onRequestOrderFromChat: ((ListingItem) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle observer for resume/pause events (refetch and mark_chat_read)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResumeScreen()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPauseScreen()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initialize chat session
    LaunchedEffect(orderId, orderStatus, listing, currentUserId, isFarmerView) {
        viewModel.initChat(
            orderId = orderId,
            orderStatus = orderStatus,
            listing = listing,
            currentUserId = currentUserId,
            isFarmerView = isFarmerView
        )
    }

    // Infinite Keyset Pagination: Detect scroll near top (higher indexes in reverseLayout)
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 5
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMorePages && !state.isLoadingMore) {
            viewModel.loadOlderMessages()
        }
    }

    val activeOrder = state.order
    val activeListing = state.listing ?: listing
    val isReadOnly = state.isReadOnly
    val isAuthorized = state.isAuthorized

    val orderCropName = activeOrder?.cropName ?: activeListing?.cropName ?: state.cropName.ifBlank { "Produce" }
    val orderNumber = activeOrder?.orderNumber ?: state.orderNumber.ifBlank {
        if (orderId.startsWith("INQ-")) orderId else "Order #${orderId.take(8)}"
    }
    val effectiveStatus = activeOrder?.status ?: state.orderStatus.ifBlank { orderStatus }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("order_chat_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.isFarmer) "Buyer Chat: $orderCropName" else "Farmer Chat: $orderCropName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = orderNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isReadOnly) "Read-Only" else "Active Discussion",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isReadOnly) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isReadOnly) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    OrderStatusChip(status = effectiveStatus)
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (!isAuthorized) {
                // Unauthorized - no bottom bar
            } else if (isReadOnly) {
                // Read-Only Status Enforcement Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .testTag("chat_read_only_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (effectiveStatus.lowercase()) {
                                    "completed" -> Icons.Filled.CheckCircle
                                    "refunded" -> Icons.Filled.CurrencyExchange
                                    "rejected" -> Icons.Filled.Cancel
                                    "cancelled" -> Icons.Filled.Block
                                    "expired" -> Icons.Filled.TimerOff
                                    else -> Icons.Filled.Lock
                                },
                                contentDescription = null,
                                tint = if (effectiveStatus.lowercase() == "completed") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Chat is read-only (Order is ${effectiveStatus.replace('_', ' ').uppercase()})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = state.readOnlyReason ?: "This order is finalized. Messaging is closed and preserved for reference.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Active Message Input Bar with 1000-char counter and security policy hint
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .testTag("chat_active_input_bar")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        // Warning Banner (e.g. 422 CONTENT_BLOCKED)
                        AnimatedVisibility(
                            visible = state.warningBanner != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .testTag("chat_moderation_warning")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = state.warningBanner ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        if (state.moderationReasons.isNotEmpty()) {
                                            Text(
                                                text = "Blocked triggers: ${state.moderationReasons.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissWarningBanner() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // General Error Notice
                        AnimatedVisibility(
                            visible = !state.errorMessage.isNullOrBlank(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = state.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Text Field & Send Action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.inputText,
                                onValueChange = viewModel::onInputTextChange,
                                placeholder = {
                                    Text(
                                        if (state.isFarmer) "Reply to buyer regarding $orderCropName..."
                                        else "Message farmer regarding $orderCropName..."
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input_field")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = !state.isSending && state.inputText.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (state.inputText.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    )
                                    .testTag("chat_send_button")
                            ) {
                                if (state.isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (state.inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Helper hint and 1000-character counter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Do not share phone numbers, links or addresses.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${state.inputText.length}/1000",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (state.inputText.length > 950) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isAuthorized) {
                // Access restriction card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("chat_unauthorized_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text(
                                text = "Access Restricted",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "This order chat is private and accessible strictly to the registered buyer and farmer of this order.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onBackClick) {
                                Text("Go Back")
                            }
                        }
                    }
                }
                return@Box
            }

            if (state.isLoadingInitial && state.messages.isEmpty()) {
                // Skeleton loading state
                ChatSkeletonView()
            } else if (!state.isLoadingInitial && state.messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Start Order Discussion",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Coordinate harvest readiness, delivery arrangement, and schedule securely inside the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Messages List with LazyColumn(reverseLayout = true)
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_lazy_column")
                ) {
                    itemsIndexed(state.messages, key = { _, msg -> msg.clientNonce ?: msg.id }) { index, msg ->
                        // Calculate Date Separator (Asia/Colombo)
                        val currentDate = parseLocalDateColombo(msg.createdAt)
                        val olderMsgDate = if (index + 1 < state.messages.size) {
                            parseLocalDateColombo(state.messages[index + 1].createdAt)
                        } else null

                        val showDateSeparator = (olderMsgDate == null || currentDate != olderMsgDate)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // If this message belongs to a new day compared to the older message above it, show separator
                            if (showDateSeparator && currentDate != null) {
                                ChatDateSeparator(date = currentDate)
                            }

                            val isMe = when {
                                state.isFarmer && msg.senderRole == "buyer" -> false
                                !state.isFarmer && msg.senderRole == "farmer" -> false
                                state.currentUserId.isNotBlank() && !msg.senderId.isNullOrBlank() && msg.senderId == state.currentUserId -> true
                                state.isFarmer -> msg.senderRole == "farmer"
                                !state.isFarmer -> msg.senderRole == "buyer"
                                else -> msg.isMine == true
                            }

                            // Render message bubble
                            ChatMessageBubble(
                                message = msg,
                                isMe = isMe,
                                isFarmer = state.isFarmer,
                                onRetry = { viewModel.sendMessage(msg) }
                            )
                        }
                    }

                    // Paging loading spinner indicator at top (older end)
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    // Order Context Banner (Placed at the very top of conversation history)
                    item {
                        OrderContextCard(
                            cropName = orderCropName,
                            orderNumber = orderNumber,
                            activeOrder = activeOrder,
                            activeListing = activeListing,
                            isFarmer = state.isFarmer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Floating "New messages" pill when scrolled up
            AnimatedVisibility(
                visible = state.hasUnseenNewMessages,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .clickable {
                            viewModel.clearUnseenMessagesPill()
                            // Scroll to bottom (index 0 in reverseLayout)
                            // Coroutine is triggered on click
                        }
                        .testTag("new_messages_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New messages ↓",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderContextCard(
    cropName: String,
    orderNumber: String,
    activeOrder: com.example.data.models.OrderItem?,
    activeListing: ListingItem?,
    isFarmer: Boolean
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_context_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val photoUrl = activeListing?.photos?.firstOrNull()
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = cropName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cropName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isFarmer) "Your Produce" else "Order Produce",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val price = activeOrder?.pricePerKg ?: activeListing?.pricePerKg ?: 0.0
                val qty = activeOrder?.quantityKg ?: activeListing?.quantityAvailable ?: 0.0
                Text(
                    text = "${formatLkr(price)}/kg • $qty kg • $orderNumber",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "In-App Escrow Protection enabled. Keep chats and agreements here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: MessageItem,
    isMe: Boolean,
    isFarmer: Boolean,
    onRetry: () -> Unit
) {
    if (message.kind == "system" || message.senderId == null) {
        // System centered / muted message
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    } else if (isMe) {
        // Mine: right aligned, green container (Color(0xFF2E7D32))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("my_chat_bubble"),
            horizontalArrangement = Arrangement.End
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 285.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 2.dp
                            )
                        )
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            val timeStr = formatTimeColombo(message.createdAt)
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (message.isSending) {
                                Spacer(modifier = Modifier.width(4.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Delivery failure / retry prompt
                if (message.sendFailed) {
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { onRetry() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.failError ?: "Failed. Tap to retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    } else {
        // Theirs: left aligned, white/surface container, sender label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("counterpart_chat_bubble"),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 285.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 2.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    val roleLabel = if (isFarmer) "🛒 Buyer" else "🧑‍🌾 Farmer"
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isFarmer) MaterialTheme.colorScheme.secondary else Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val timeStr = formatTimeColombo(message.createdAt)
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatDateSeparator(date: LocalDate) {
    val today = LocalDate.now(COLOMBO_ZONE)
    val yesterday = today.minusDays(1)

    val label = when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChatSkeletonView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {}

        repeat(5) { i ->
            val isRight = (i % 2 == 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(if (i % 3 == 0) 220.dp else 160.dp)
                        .height(48.dp)
                ) {}
            }
        }
    }
}

private fun parseLocalDateColombo(isoString: String?): LocalDate? {
    if (isoString.isNullOrBlank()) return null
    return try {
        val instant = Instant.parse(isoString)
        instant.atZone(COLOMBO_ZONE).toLocalDate()
    } catch (_: Exception) {
        null
    }
}

private fun formatTimeColombo(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(isoString)
        val time = instant.atZone(COLOMBO_ZONE).toLocalTime()
        time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    } catch (_: Exception) {
        ""
    }
}
