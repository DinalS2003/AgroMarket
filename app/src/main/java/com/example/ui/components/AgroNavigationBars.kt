package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.AppMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgroTopBar(
    currentMode: AppMode,
    isFarmer: Boolean,
    unreadNotifications: Int,
    onModeToggle: (AppMode) -> Unit,
    onNotificationsClick: () -> Unit,
    onBecomeFarmerClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = "AgroMarket Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AgroMarket",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        actions = {
            if (isFarmer) {
                // Mode Switch Segmented Button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("mode_toggle_container")
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isBuying = currentMode == AppMode.BUYING

                        val buyBg by animateColorAsState(
                            if (isBuying) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "buyBg"
                        )
                        val buyText by animateColorAsState(
                            if (isBuying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "buyText"
                        )

                        val sellBg by animateColorAsState(
                            if (!isBuying) MaterialTheme.colorScheme.secondary else Color.Transparent,
                            label = "sellBg"
                        )
                        val sellText by animateColorAsState(
                            if (!isBuying) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "sellText"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(buyBg)
                                .clickable { onModeToggle(AppMode.BUYING) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("mode_buying_tab")
                        ) {
                            Text(
                                text = "Buying",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = buyText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(sellBg)
                                .clickable { onModeToggle(AppMode.SELLING) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("mode_selling_tab")
                        ) {
                            Text(
                                text = "Selling",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = sellText
                            )
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = onBecomeFarmerClick,
                    modifier = Modifier.padding(end = 4.dp).testTag("become_farmer_button")
                ) {
                    Icon(Icons.Filled.Agriculture, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sell Crops", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }

            // Notification Bell with Badge
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.testTag("notifications_top_action")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotifications > 0) {
                            Badge {
                                Text(
                                    text = if (unreadNotifications > 9) "9+" else "$unreadNotifications",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (unreadNotifications > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Notifications"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun AgroBottomNavigation(
    currentRoute: String,
    currentMode: AppMode,
    unreadNotifications: Int = 0,
    unreadMessages: Int = 0,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("bottom_nav_bar")
    ) {
        if (currentMode == AppMode.BUYING) {
            // Buying mode: Marketplace | My Orders | Messages | Profile (icons only)
            NavigationBarItem(
                selected = currentRoute == "marketplace",
                onClick = { onNavigate("marketplace") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "marketplace") Icons.Filled.Storefront else Icons.Outlined.Storefront,
                        contentDescription = "Marketplace"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_marketplace")
            )

            NavigationBarItem(
                selected = currentRoute == "my_orders",
                onClick = { onNavigate("my_orders") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "my_orders") Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag,
                        contentDescription = "My Orders"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_my_orders")
            )

            NavigationBarItem(
                selected = currentRoute == "messages",
                onClick = { onNavigate("messages") },
                icon = {
                    BadgedBox(
                        badge = {
                            if (unreadMessages > 0) {
                                Badge { Text(text = "$unreadMessages") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (currentRoute == "messages") Icons.Filled.ChatBubble else Icons.Outlined.ChatBubble,
                            contentDescription = "Messages"
                        )
                    }
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_messages")
            )

            NavigationBarItem(
                selected = currentRoute == "profile",
                onClick = { onNavigate("profile") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = "Profile"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_profile")
            )
        } else {
            // Selling mode: My Listings | Farmer Orders | Dashboard | Messages | Profile (icons only)
            NavigationBarItem(
                selected = currentRoute == "my_listings",
                onClick = { onNavigate("my_listings") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "my_listings") Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                        contentDescription = "My Listings"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_my_listings")
            )

            NavigationBarItem(
                selected = currentRoute == "farmer_orders",
                onClick = { onNavigate("farmer_orders") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "farmer_orders") Icons.Filled.LocalShipping else Icons.Outlined.LocalShipping,
                        contentDescription = "Farmer Orders"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_farmer_orders")
            )

            NavigationBarItem(
                selected = currentRoute == "dashboard",
                onClick = { onNavigate("dashboard") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "dashboard") Icons.Filled.Assessment else Icons.Outlined.Assessment,
                        contentDescription = "Dashboard"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_dashboard")
            )

            NavigationBarItem(
                selected = currentRoute == "messages",
                onClick = { onNavigate("messages") },
                icon = {
                    BadgedBox(
                        badge = {
                            if (unreadMessages > 0) {
                                Badge { Text(text = "$unreadMessages") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (currentRoute == "messages") Icons.Filled.ChatBubble else Icons.Outlined.ChatBubble,
                            contentDescription = "Messages"
                        )
                    }
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_messages_selling")
            )

            NavigationBarItem(
                selected = currentRoute == "profile",
                onClick = { onNavigate("profile") },
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = "Profile"
                    )
                },
                alwaysShowLabel = false,
                modifier = Modifier.testTag("nav_profile_selling")
            )
        }
    }
}
