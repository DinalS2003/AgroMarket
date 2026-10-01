package com.example.ui.screens.farmer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ListingItem
import com.example.ui.components.encodeImageUriToBase64
import com.example.viewmodel.FarmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditListingScreen(
    farmerId: String,
    existingListing: ListingItem?,
    viewModel: FarmerViewModel,
    onBackClick: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.addEditState.collectAsState()
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var showUrlField by remember { mutableStateOf(false) }

    // Standard Android Photo Picker (zero-permission)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val base64Data = encodeImageUriToBase64(context, uri)
            if (!base64Data.isNullOrBlank()) {
                viewModel.addPhotoUrl(base64Data)
            }
        }
    }

    LaunchedEffect(existingListing) {
        viewModel.initAddEditListing(existingListing)
    }

    LaunchedEffect(state.isSavedSuccess) {
        if (state.isSavedSuccess) {
            onSaved()
        }
    }

    val isEditing = (existingListing != null)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && existingListing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Delete Listing?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${existingListing.cropName}\"? This crop will immediately be removed from the marketplace.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteListing(existingListing.id, farmerId) {
                            onSaved()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_listing_top_btn")
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
                title = { Text(if (isEditing) "Edit Harvest Listing" else "List New Harvest", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("action_delete_listing")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Listing", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Button(
                    onClick = { viewModel.saveListing(farmerId) },
                    enabled = !state.isSaving && state.cropName.isNotBlank() && state.quantityKg.isNotBlank() && state.pricePerKg.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .testTag("save_listing_button")
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (isEditing) "Save Changes" else "Publish Listing", fontWeight = FontWeight.Bold)
                    }
                }
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
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Crop Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                        // Crop Name with autocomplete
                        OutlinedTextField(
                            value = state.cropName,
                            onValueChange = viewModel::onCropNameChange,
                            enabled = !isEditing, // Locked after publish
                            label = { Text("Crop Name") },
                            placeholder = { Text("e.g. Fresh Red Tomatoes") },
                            supportingText = {
                                if (isEditing) Text("Crop name is locked after publishing to protect order history.")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("crop_name_field")
                        )

                        // Autocomplete suggestions
                        if (state.filteredSuggestions.isNotEmpty() && !isEditing) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                tonalElevation = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    state.filteredSuggestions.forEach { sugg ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.onCropSuggestionSelected(sugg) }
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Icon(Icons.Filled.Eco, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(sugg, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }

                        // Harvest Date
                        OutlinedTextField(
                            value = state.harvestDate,
                            onValueChange = viewModel::onHarvestDateChange,
                            enabled = !isEditing, // Locked after publish
                            label = { Text("Harvest Date (YYYY-MM-DD)") },
                            placeholder = { Text("2026-10-01") },
                            supportingText = {
                                if (isEditing) Text("Harvest date is locked after publish.")
                                else Text("Buyers can only place orders on or after this date.")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("harvest_date_field")
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pricing & Quantity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.pricePerKg,
                                onValueChange = viewModel::onPriceChange,
                                label = { Text("Price (Rs./kg)") },
                                placeholder = { Text("350.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("price_field")
                            )

                            OutlinedTextField(
                                value = state.quantityKg,
                                onValueChange = viewModel::onQuantityChange,
                                label = { Text("Available (kg)") },
                                placeholder = { Text("200") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("qty_field")
                            )
                        }

                        OutlinedTextField(
                            value = state.minOrderKg,
                            onValueChange = viewModel::onMinOrderChange,
                            label = { Text("Minimum Order (kg)") },
                            placeholder = { Text("5.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = { Text("Smallest batch quantity a buyer can request.") },
                            modifier = Modifier.fillMaxWidth().testTag("min_order_field")
                        )
                    }
                }
            }

            // Photos Section (Optional)
            item {
                val cropPresets = remember {
                    listOf(
                        "Tomato" to "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=800&q=80",
                        "Carrot" to "https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?w=800&q=80",
                        "Potato" to "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=800&q=80",
                        "Beans" to "https://images.unsplash.com/photo-1567375698348-5d9d5ae99de0?w=800&q=80",
                        "Cabbage" to "https://images.unsplash.com/photo-1594282486552-05b4d80fbb9f?w=800&q=80",
                        "Onion" to "https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?w=800&q=80",
                        "Chilli" to "https://images.unsplash.com/photo-1588252303782-cb80119abd6d?w=800&q=80",
                        "Banana" to "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=800&q=80",
                        "Papaya" to "https://images.unsplash.com/photo-1617112848923-cc2234396a8d?w=800&q=80",
                        "Brinjal" to "https://images.unsplash.com/photo-1628773822503-930a84d94b02?w=800&q=80",
                        "Leeks" to "https://images.unsplash.com/photo-1587049352847-4a222e784d38?w=800&q=80"
                    )
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Crop Photos (Optional)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "${state.photos.size}/5",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            "Add real pictures of your harvest to attract buyers. Photos are optional — if none are provided, a verified crop card badge will be shown automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Photo Thumbnails Row
                        if (state.photos.isNotEmpty()) {
                            Text(
                                "Added Photos (Tap any to set as Cover Photo):",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                itemsIndexed(state.photos) { index, photoUrl ->
                                    val isCover = (index == 0)
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (!isCover) viewModel.setCoverPhoto(photoUrl)
                                            }
                                    ) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Harvest Photo $index",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Cover Badge
                                        if (isCover) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Star,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        "COVER",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // Delete button
                                        IconButton(
                                            onClick = { viewModel.removePhoto(photoUrl) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(24.dp)
                                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove photo",
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Select Curated Presets
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Quick-Select Farm Crop Photo:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(cropPresets.size) { i ->
                                    val (name, url) = cropPresets[i]
                                    val isAdded = state.photos.contains(url)
                                    FilterChip(
                                        selected = isAdded,
                                        onClick = {
                                            if (isAdded) {
                                                viewModel.removePhoto(url)
                                            } else if (state.photos.size < 5) {
                                                viewModel.addPhotoUrl(url)
                                            }
                                        },
                                        label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = if (isAdded) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Remove",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Action Buttons: Choose from Gallery & Link URL
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = state.photos.size < 5,
                                modifier = Modifier.weight(1f).height(44.dp).testTag("pick_photo_button")
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Gallery", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = { showUrlField = !showUrlField },
                                enabled = state.photos.size < 5,
                                modifier = Modifier.weight(1f).height(44.dp).testTag("toggle_url_photo_button")
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (showUrlField) "Close URL" else "Add URL", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // URL input field
                        if (showUrlField) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("Image URL") },
                                    placeholder = { Text("https://.../crop.jpg") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("photo_url_input")
                                )
                                Button(
                                    onClick = {
                                        if (urlInput.isNotBlank()) {
                                            viewModel.addPhotoUrl(urlInput.trim())
                                            urlInput = ""
                                            showUrlField = false
                                        }
                                    },
                                    enabled = urlInput.isNotBlank() && state.photos.size < 5,
                                    modifier = Modifier.height(52.dp).testTag("submit_photo_url_button")
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = state.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (isEditing && existingListing != null) {
                item {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_listing_bottom_button")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete This Crop Listing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
