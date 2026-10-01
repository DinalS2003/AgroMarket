package com.example.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.NotificationItem
import com.example.data.repository.AgroMarketRepository
import com.example.ui.components.EmptyStateView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    userId: String,
    repository: AgroMarketRepository,
    onOrderClick: (String) -> Unit
) {
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            isLoading = true
            val res = repository.getNotifications(userId)
            res.onSuccess { notifications = it }
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications & Alerts", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (notifications.isEmpty() && !isLoading) {
            EmptyStateView(
                icon = Icons.Filled.Notifications,
                title = "No Notifications",
                message = "You have no unread updates at this moment.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(padding).testTag("notifications_list")
            ) {
                items(notifications, key = { it.id }) { notif ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.readAt == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (notif.readAt == null) {
                                    scope.launch { repository.markNotificationRead(notif.id) }
                                }
                                notif.orderId?.let { onOrderClick(it) }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (notif.readAt == null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notif.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = notif.body,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notif.createdAt.take(16).replace("T", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
