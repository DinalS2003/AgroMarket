package com.example.ui.screens.listing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.example.data.models.ReviewItem
import com.example.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listing: ListingItem,
    reviews: List<ReviewItem>,
    onBackClick: () -> Unit,
    onOrderClick: () -> Unit,
    onChatClick: () -> Unit,
    canDelete: Boolean = false,
    onDeleteClick: (() -> Unit)? = null
) {
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val isAvailableToOrder = todayStr >= listing.harvestDate && listing.quantityAvailable > 0
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }

    if (showDeleteConfirm && onDeleteClick != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Delete Listing?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${listing.cropName}\"? This crop will immediately be removed from the marketplace.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_detail_btn")
                ) {
                    Text("Delete Listing")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing.cropName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canDelete && onDeleteClick != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("action_delete_crop_detail")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Listing", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onChatClick, modifier = Modifier.testTag("top_chat_button")) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat with farmer")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unit Price",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatLkr(listing.pricePerKg)}/kg",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = onChatClick,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("chat_farmer_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onOrderClick,
                        enabled = isAvailableToOrder,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("place_order_button")
                    ) {
                        if (isAvailableToOrder) {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Request Order", fontWeight = FontWeight.Bold)
                        } else {
                            Text("From ${listing.harvestDate}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Photos or Crop Hero Banner Gallery
            item {
                val hasPhotos = listing.photos.isNotEmpty()
                val currentPhotoUrl = if (hasPhotos && selectedPhotoIndex in listing.photos.indices) {
                    listing.photos[selectedPhotoIndex]
                } else null
                val isBulk = listing.quantityAvailable >= 250.0

                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFFE8F5EE)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = currentPhotoUrl,
                                contentDescription = listing.cropName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Subtle gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
                                                androidx.compose.ui.graphics.Color.Transparent,
                                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.40f)
                                            )
                                        )
                                    )
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Eco,
                                    contentDescription = listing.cropName,
                                    tint = androidx.compose.ui.graphics.Color(0xFF1B4D3E),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(0xFF1E4D3E),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isBulk) "Bulk Harvest" else "Fresh Harvest",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Top Left: Status Badge
                        Surface(
                            color = if (isBulk) androidx.compose.ui.graphics.Color(0xFF0F5132) else androidx.compose.ui.graphics.Color(0xFF137333),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (isBulk) "Bulk Stock (250+ kg)" else "Fresh Harvest",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        if (listing.isTopFarmer) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            ) {
                                TopFarmerBadge()
                            }
                        }

                        // Bottom Center: Photo count and indicators if multiple photos
                        if (listing.photos.size > 1) {
                            Surface(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listing.photos.indices.forEach { idx ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (idx == selectedPhotoIndex) 8.dp else 6.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(
                                                    if (idx == selectedPhotoIndex) androidx.compose.ui.graphics.Color.White
                                                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Thumbnail Strip for multiple photos
                    if (listing.photos.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            items(listing.photos.size) { idx ->
                                val thumbUrl = listing.photos[idx]
                                val isSelected = (idx == selectedPhotoIndex)
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedPhotoIndex = idx }
                                ) {
                                    AsyncImage(
                                        model = thumbUrl,
                                        contentDescription = "Thumbnail $idx",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Overview card
            item {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = listing.cropName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Harvest Location: ${listing.cultivationCityName ?: ""}, ${listing.cultivationDistrictName ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HarvestMetricCard(
                            label = "Available Stock",
                            value = "${listing.quantityAvailable} kg",
                            icon = Icons.Filled.Inventory,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        HarvestMetricCard(
                            label = "Min Order",
                            value = "${listing.minOrderKg} kg",
                            icon = Icons.Filled.LocalMall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        HarvestMetricCard(
                            label = "Harvest Date",
                            value = listing.harvestDate,
                            icon = Icons.Filled.CalendarMonth,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Farmer Info Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Farmer ${listing.farmerFirstName ?: "Farmer"}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Verified Cultivator • ${listing.cultivationDistrictName ?: ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (listing.isTopFarmer) {
                                    TopFarmerBadge()
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Reliability Score",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (listing.completedOrders >= 3) {
                                        Text(
                                            text = "${listing.reliabilityScore}%",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    } else {
                                        Text(
                                            text = "New farmer",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Buyer Rating",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    RatingStars(rating = listing.ratingAvg, reviewCount = listing.ratingCount)
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${listing.completedOrders} orders",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = onChatClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("farmer_card_chat_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inquire & Chat with Farmer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Buyer Reviews (${reviews.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (reviews.isEmpty()) {
                item {
                    Text(
                        text = "No reviews yet for this farmer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(reviews) { rev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RatingStars(rating = rev.rating.toDouble())
                                Text(
                                    text = rev.createdAt.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!rev.comment.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = rev.comment,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun HarvestMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
