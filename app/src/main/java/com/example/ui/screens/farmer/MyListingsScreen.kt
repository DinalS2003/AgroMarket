package com.example.ui.screens.farmer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ListingItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatLkr
import com.example.ui.screens.marketplace.CropThumbnail
import com.example.viewmodel.FarmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    farmerId: String,
    viewModel: FarmerViewModel,
    onAddListingClick: () -> Unit,
    onEditListingClick: (ListingItem) -> Unit,
    onViewInquiriesClick: (ListingItem) -> Unit = {}
) {
    val listings by viewModel.myListings.collectAsState()
    val isLoading by viewModel.isLoadingListings.collectAsState()

    LaunchedEffect(farmerId) {
        viewModel.loadMyListings(farmerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Harvest Listings", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onAddListingClick, modifier = Modifier.testTag("action_add_listing")) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Listing")
                    }
                }
            )
        }
    ) { padding ->
        if (listings.isEmpty() && !isLoading) {
            EmptyStateView(
                icon = Icons.Filled.Inventory2,
                title = "No Crops Listed Yet",
                message = "List your harvest so registered buyers in your district can discover and order directly from you.",
                actionLabel = "Create First Listing",
                onAction = onAddListingClick,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("farmer_listings_list")
            ) {
                items(listings, key = { it.id }) { listing ->
                    FarmerListingItemCard(
                        listing = listing,
                        onQuickUpdate = { newPrice, newQty ->
                            viewModel.quickUpdatePriceAndQty(listing.id, newPrice, newQty, farmerId)
                        },
                        onToggleActive = { active ->
                            viewModel.toggleListingActive(listing.id, active, farmerId)
                        },
                        onEditClick = { onEditListingClick(listing) },
                        onViewInquiriesClick = { onViewInquiriesClick(listing) },
                        onDeleteClick = {
                            viewModel.deleteListing(listing.id, farmerId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FarmerListingItemCard(
    listing: ListingItem,
    onQuickUpdate: (Double, Double) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onViewInquiriesClick: () -> Unit = {},
    onDeleteClick: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var priceText by remember { mutableStateOf(listing.pricePerKg.toString()) }
    var qtyText by remember { mutableStateOf(listing.quantityAvailable.toString()) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Delete Listing?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to permanently delete \"${listing.cropName}\"? This crop will immediately be removed from the buyer marketplace.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_listing_button")
                ) {
                    Text("Delete Crop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Crop photo or thumbnail with count badge
                val photoUrl = listing.photos.firstOrNull()
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEditClick() }
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = listing.cropName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CropThumbnail(
                            photos = listing.photos,
                            cropName = listing.cropName,
                            quantityKg = listing.quantityAvailable
                        )
                    }

                    if (listing.photos.size > 1) {
                        Surface(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(bottomStart = 8.dp),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = "📷 ${listing.photos.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listing.cropName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Harvest: ${listing.harvestDate} • Min: ${listing.minOrderKg} kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (listing.photos.isEmpty()) {
                        Text(
                            text = "+ Add photo",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clickable { onEditClick() }
                                .padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (listing.quantityAvailable > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (listing.quantityAvailable > 0) "Active" else "Sold Out",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (listing.quantityAvailable > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isEditing) {
                // Price & Quantity Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Current Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${formatLkr(listing.pricePerKg)}/kg",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Remaining Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${listing.quantityAvailable} kg",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Row: Inquiries, Edit, and Delete perfectly aligned
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onViewInquiriesClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("inquiries_listing_btn_${listing.id}")
                    ) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Inquiries", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { isEditing = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("edit_listing_btn_${listing.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedIconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_listing_btn_${listing.id}")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete Listing",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // Quick inline edit of price and quantity
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Price (Rs.)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("Stock (kg)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val p = priceText.toDoubleOrNull() ?: listing.pricePerKg
                                val q = qtyText.toDoubleOrNull() ?: listing.quantityAvailable
                                onQuickUpdate(p, q)
                                isEditing = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
