package com.example.ui.screens.farmer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.RatingStars
import com.example.ui.components.TopFarmerBadge
import com.example.ui.components.formatLkr
import com.example.viewmodel.FarmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerDashboardScreen(
    farmerId: String,
    viewModel: FarmerViewModel,
    onAddListingClick: () -> Unit
) {
    val state by viewModel.dashboardState.collectAsState()

    LaunchedEffect(farmerId) {
        viewModel.loadDashboard(farmerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Farmer Dashboard", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddListingClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_listing_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Listing")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reliability Score Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Farmer Reliability Score",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (state.stats?.isTopFarmer == true) {
                                TopFarmerBadge()
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            if (state.completedOrdersCount >= 3) {
                                Text(
                                    text = "${state.stats?.reliabilityScore ?: 0.0}%",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            } else {
                                Text(
                                    text = "New Farmer",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(${state.completedOrdersCount} orders completed)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Reliability Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Fulfillment", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${((state.stats?.fulfillmentRate ?: 0.0) * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column {
                                Text("On-Time Rate", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${((state.stats?.onTimeRate ?: 0.0) * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column {
                                Text("Rating", style = MaterialTheme.typography.labelSmall)
                                RatingStars(rating = state.stats?.ratingAvg ?: 0.0, reviewCount = state.stats?.ratingCount)
                            }
                        }
                    }
                }
            }

            // Financial Earnings Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Earnings & Payouts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Earned (Dispatched)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatLkr(state.totalEarnings),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Pending Payout",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatLkr(state.pendingPayouts),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }

                        Text(
                            text = "Pending payouts are transferred to your registered bank account by platform administration within 1-2 business days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Active Listings quick count
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Active Harvest Listings", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text("${state.activeListingsCount} crops currently visible to buyers", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Button(onClick = onAddListingClick) {
                            Text("New")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(70.dp))
            }
        }
    }
}
