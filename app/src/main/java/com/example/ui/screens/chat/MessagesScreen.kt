package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ConversationItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatLkr
import com.example.ui.components.formatMessageTimestamp
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    userId: String,
    isFarmer: Boolean,
    chatViewModel: ChatViewModel,
    onConversationClick: (ConversationItem) -> Unit,
    onBackClick: () -> Unit
) {
    val conversations by chatViewModel.conversations.collectAsState()
    val isLoading by chatViewModel.isLoadingConversations.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(userId, isFarmer) {
        chatViewModel.loadConversations(userId, isFarmer)
    }

    val filteredList = remember(conversations, selectedFilter) {
        conversations.filter { conv ->
            when (selectedFilter) {
                "Inquiries" -> conv.status == "inquiry"
                "Orders" -> conv.status != "inquiry"
                else -> true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(
                        onClick = { chatViewModel.loadConversations(userId, isFarmer) },
                        modifier = Modifier.testTag("refresh_messages_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Messages")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val inquiryCount = remember(conversations) { conversations.count { it.status == "inquiry" } }
            val ordersCount = remember(conversations) { conversations.count { it.status != "inquiry" } }

            // Filter Tabs (All / Listing Inquiries / Orders) with uniform height and icons
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        leadingIcon = {
                            Icon(Icons.Filled.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("All (${conversations.size})") },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("filter_all_chats")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "Inquiries",
                        onClick = { selectedFilter = "Inquiries" },
                        leadingIcon = {
                            Icon(Icons.Filled.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Listing Inquiries ($inquiryCount)") },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("filter_inquiries")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "Orders",
                        onClick = { selectedFilter = "Orders" },
                        leadingIcon = {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Orders ($ordersCount)") },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("filter_orders")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading && conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredList.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = if (selectedFilter == "Inquiries") "No Listing Inquiries Yet" else "No Messages",
                    message = if (isFarmer) {
                        "When buyers discover your crop listings in the marketplace, their inquiry messages will appear here."
                    } else {
                        "Start an inquiry with a farmer from any crop listing in the marketplace to ask about availability or pricing."
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("conversations_list")
                ) {
                    items(filteredList, key = { it.orderId }) { conv ->
                        ConversationCard(
                            conversation = conv,
                            isFarmer = isFarmer,
                            onClick = { onConversationClick(conv) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationCard(
    conversation: ConversationItem,
    isFarmer: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("conversation_card_${conversation.orderId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Avatar: Crop Photo or Person Icon
            val photoUrl = conversation.listingPhoto
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = conversation.cropName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (conversation.status == "inquiry") Icons.Filled.QuestionAnswer else Icons.Filled.Agriculture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Conversation Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Line 1: Counterpart Name + Crop Badge + Formatted Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = conversation.counterpartName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (conversation.cropName.isNotBlank() && conversation.cropName != "Produce") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = conversation.cropName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val timeFormatted = formatMessageTimestamp(conversation.lastMessageTime)
                    if (timeFormatted.isNotBlank()) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Line 2: Message preview (sent by user or counterpart) + Unread Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isUnread = conversation.unreadCount > 0
                    Text(
                        text = conversation.lastMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "${conversation.unreadCount}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
