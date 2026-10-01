package com.example.ui.screens.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.models.District
import com.example.ui.components.formatLkr
import com.example.viewmodel.OrderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceOrderScreen(
    viewModel: OrderViewModel,
    districts: List<District>,
    onBackClick: () -> Unit,
    onOrderPlaced: (String) -> Unit
) {
    val state by viewModel.placeOrderState.collectAsState()

    LaunchedEffect(state.orderCreatedId) {
        state.orderCreatedId?.let { id ->
            onOrderPlaced(id)
        }
    }

    val listing = state.listing ?: return
    var districtExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Order", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimated Subtotal", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = formatLkr(state.subtotal),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Button(
                        onClick = viewModel::submitOrderRequest,
                        enabled = !state.isSubmitting,
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("submit_order_button")
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send Order Request", fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Harvest snapshot card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(listing.cropName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "Price: ${formatLkr(listing.pricePerKg)}/kg • Available: ${listing.quantityAvailable} kg",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Quantity Input
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Order Quantity (kg)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.quantityKg,
                            onValueChange = viewModel::onQuantityChange,
                            label = { Text("Weight in Kilograms") },
                            placeholder = { Text("e.g. 50") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = state.quantityError != null,
                            supportingText = {
                                if (state.quantityError != null) {
                                    Text(state.quantityError!!, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Min order: ${listing.minOrderKg} kg • Max available: ${listing.quantityAvailable} kg")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("order_qty_input")
                        )
                    }
                }
            }

            // Delivery Method
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Delivery Method",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        // Option 1: Buyer Arranged (Recommended)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.deliveryMethod == "buyer_arranged") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onDeliveryMethodChange("buyer_arranged") }
                                .testTag("radio_buyer_arranged")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.deliveryMethod == "buyer_arranged",
                                    onClick = { viewModel.onDeliveryMethodChange("buyer_arranged") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "I will arrange delivery / pickup",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "Recommended",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "Arrange your own vehicle or courier pickup from the farmer landmark.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Option 2: Farmer Delivery
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.deliveryMethod == "farmer_delivery") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onDeliveryMethodChange("farmer_delivery") }
                                .testTag("radio_farmer_delivery")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.deliveryMethod == "farmer_delivery",
                                    onClick = { viewModel.onDeliveryMethodChange("farmer_delivery") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Request delivery from farmer",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "Farmer will review and quote their delivery fee before you pay.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Farmer Delivery Address fields
                        AnimatedVisibility(visible = state.deliveryMethod == "farmer_delivery") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    "Delivery Destination",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )

                                ExposedDropdownMenuBox(
                                    expanded = districtExpanded,
                                    onExpandedChange = { districtExpanded = !districtExpanded }
                                ) {
                                    val distName = districts.find { it.id == state.deliveryDistrictId }?.name ?: "Select District"
                                    OutlinedTextField(
                                        value = distName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Delivery District") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = districtExpanded,
                                        onDismissRequest = { districtExpanded = false }
                                    ) {
                                        districts.forEach { dist ->
                                            DropdownMenuItem(
                                                text = { Text(dist.name) },
                                                onClick = {
                                                    viewModel.onDeliveryDistrictChange(dist.id)
                                                    districtExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                ExposedDropdownMenuBox(
                                    expanded = cityExpanded,
                                    onExpandedChange = { cityExpanded = !cityExpanded }
                                ) {
                                    val cityName = state.deliveryCities.find { it.id == state.deliveryCityId }?.name ?: "Select Town"
                                    OutlinedTextField(
                                        value = cityName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Delivery Town") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = cityExpanded,
                                        onDismissRequest = { cityExpanded = false }
                                    ) {
                                        state.deliveryCities.forEach { city ->
                                            DropdownMenuItem(
                                                text = { Text(city.name) },
                                                onClick = {
                                                    viewModel.onDeliveryCityChange(city.id)
                                                    cityExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = state.deliveryAddress,
                                    onValueChange = viewModel::onDeliveryAddressChange,
                                    label = { Text("Full Street Delivery Address") },
                                    placeholder = { Text("House/Shop No, Street Name") },
                                    supportingText = {
                                        Text("Visible ONLY to this farmer, and only while order is paid/dispatched.")
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("delivery_address_input")
                                )
                            }
                        }
                    }
                }
            }

            // Requested Date Picker
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Required Delivery / Pickup Date",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.requestedDate,
                            onValueChange = viewModel::onRequestedDateChange,
                            label = { Text("Date (YYYY-MM-DD)") },
                            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                            supportingText = {
                                Text("Must be on or after harvest date: ${listing.harvestDate}")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("requested_date_input")
                        )
                    }
                }
            }

            // Order Terms & Process Note
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Important Ordering Terms",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• The farmer has 12 hours to review and accept your request.\n• Once accepted, you must complete payment within 2 hours.\n• No contact details may be shared outside of in-app chat.\n• Payments are protected in escrow until harvest delivery is completed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().testTag("order_error_banner")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Unable to Complete Request",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = viewModel::clearErrorMessage,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = viewModel::submitOrderRequest,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
