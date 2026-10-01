package com.example.ui.screens.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.OrderItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.formatLkr

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersListScreen(
    orders: List<OrderItem>,
    isFarmer: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOrderClick: (OrderItem) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }

    val inquiryCount = remember(orders) { orders.count { it.status == "inquiry" } }
    val regularOrdersCount = remember(orders) { orders.count { it.status != "inquiry" } }

    val filteredOrders = remember(orders, selectedFilter) {
        when (selectedFilter) {
            "Inquiries" -> orders.filter { it.status == "inquiry" }
            "Orders" -> orders.filter { it.status != "inquiry" }
            else -> orders
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isFarmer) "Farmer Orders" else "My Orders",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs (All / Orders / Listing Inquiries) with perfect alignment
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
                            Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("All (${orders.size})") },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("filter_orders_all")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "Orders",
                        onClick = { selectedFilter = "Orders" },
                        leadingIcon = {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Orders ($regularOrdersCount)") },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("filter_orders_regular")
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
                            .testTag("filter_orders_inquiries")
                    )
                }
            }

            if (filteredOrders.isEmpty() && !isLoading) {
                EmptyStateView(
                    icon = if (selectedFilter == "Inquiries") Icons.Filled.QuestionAnswer else if (isFarmer) Icons.Filled.LocalShipping else Icons.Filled.ShoppingBag,
                    title = if (selectedFilter == "Inquiries") "No Listing Inquiries Yet" else if (isFarmer) "No Orders Received Yet" else "No Orders Placed Yet",
                    message = if (selectedFilter == "Inquiries") {
                        if (isFarmer) "Inquiries sent by buyers regarding your produce will appear here." else "Inquiries you send to farmers regarding produce will appear here."
                    } else if (isFarmer) {
                        "Incoming orders from buyers will appear here for your review and fulfillment."
                    } else {
                        "Browse fresh harvest from Sri Lankan farmers in the Marketplace and place an order."
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("orders_list")
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderSummaryCard(
                            order = order,
                            isFarmer = isFarmer,
                            onClick = { onOrderClick(order) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderSummaryCard(
    order: OrderItem,
    isFarmer: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("order_item_${order.orderNumber}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.orderNumber,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                OrderStatusChip(status = order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${order.quantityKg} kg of ${order.cropName}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Delivery method: ${if (order.deliveryMethod == "buyer_arranged") "Buyer Pickup" else "Farmer Delivery"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isFarmer) "Farmer Payout" else "Total Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatLkr(if (isFarmer) order.farmerPayoutAmount else order.totalAmount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View Details",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
