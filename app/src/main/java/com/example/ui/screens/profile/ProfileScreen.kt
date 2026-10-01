package com.example.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    viewModel: ProfileViewModel,
    onBecomeFarmerClick: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var districtExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }

    var farmerCultDistrictExpanded by remember { mutableStateOf(false) }
    var farmerCultCityExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile & Security", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Info Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Personal Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                        OutlinedTextField(
                            value = state.editName,
                            onValueChange = viewModel::onNameChange,
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                        )

                        // District
                        ExposedDropdownMenuBox(
                            expanded = districtExpanded,
                            onExpandedChange = { districtExpanded = !districtExpanded }
                        ) {
                            val distName = state.districts.find { it.id == state.selectedDistrictId }?.name ?: "District"
                            OutlinedTextField(
                                value = distName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("District") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = districtExpanded,
                                onDismissRequest = { districtExpanded = false }
                            ) {
                                state.districts.forEach { dist ->
                                    DropdownMenuItem(
                                        text = { Text(dist.name) },
                                        onClick = {
                                            viewModel.onDistrictChange(dist.id)
                                            districtExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // City
                        ExposedDropdownMenuBox(
                            expanded = cityExpanded,
                            onExpandedChange = { cityExpanded = !cityExpanded }
                        ) {
                            val cityName = state.cities.find { it.id == state.selectedCityId }?.name ?: "Town"
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Town / DS Division") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = cityExpanded,
                                onDismissRequest = { cityExpanded = false }
                            ) {
                                state.cities.forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(city.name) },
                                        onClick = {
                                            viewModel.onCityChange(city.id)
                                            cityExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = viewModel::saveProfile,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                        ) {
                            Text("Update Profile Details")
                        }
                    }
                }
            }

            // Private Owner-Only Security Card (Phone & NIC)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verified Private Identity", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Text(
                            text = "Your Mobile Number and National Identity Card (NIC) are strictly protected under Section 0.3 Privacy Rules. No other user can ever see these credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Divider()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("NIC (Verified)", style = MaterialTheme.typography.bodyMedium)
                            Text("••••••••••••", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Mobile Number (Verified)", style = MaterialTheme.typography.bodyMedium)
                            Text("Protected", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Farmer Profile & Bank Account
            if (state.isFarmer) {
                item {
                    val priv = state.farmerPrivate
                    val hasBankDetails = !priv?.bankName.isNullOrBlank() && !priv?.accountNumber.isNullOrBlank()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.AccountBalance,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Payout Bank Details",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                TextButton(
                                    onClick = viewModel::openBankDialog,
                                    modifier = Modifier.testTag("edit_bank_details_button")
                                ) {
                                    Text(if (hasBankDetails) "Edit Details" else "Add Details")
                                }
                            }

                            if (hasBankDetails) {
                                Text("Bank: ${priv?.bankName} (${priv?.bankBranch})", style = MaterialTheme.typography.bodyMedium)
                                Text("Account Number: ${priv?.accountNumber}", style = MaterialTheme.typography.bodyMedium)
                                Text("Account Holder: ${priv?.accountHolderName}", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(
                                    text = "No payout account added yet. Add your Sri Lankan bank details so earnings from your crop sales can be directly credited to your account.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = viewModel::openBankDialog,
                                    modifier = Modifier.fillMaxWidth().testTag("add_bank_details_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Bank Account for Payouts")
                                }
                            }

                            if (state.bankSaveSuccess) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Bank details saved successfully.",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Agriculture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Want to sell harvest?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Register as a verified Sri Lankan farmer to list vegetables, grains, and fruits directly to buyers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.openFarmerRegistrationDialog() },
                                modifier = Modifier.fillMaxWidth().testTag("register_as_farmer_button")
                            ) {
                                Icon(Icons.Filled.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Register Farm Details", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (state.farmerRegisterSuccess) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Farmer Registration Complete!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "You are now verified to list and sell your harvest on AgroMarket. Switch to Selling mode from the top bar to manage listings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Error display
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

            // Logout
            item {
                OutlinedButton(
                    onClick = { viewModel.logout(onLoggedOut) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("logout_button")
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout from AgroMarket")
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Bank Details Dialog
    if (state.isBankDialogOpen) {
        val bankScrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = viewModel::closeBankDialog,
            title = {
                Text("Payout Bank Details", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(bankScrollState)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Your bank details are encrypted and securely stored for processing earnings payouts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.bankName,
                        onValueChange = viewModel::onBankNameChange,
                        label = { Text("Bank Name") },
                        placeholder = { Text("e.g. Bank of Ceylon, Commercial Bank") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_bank_name_input")
                    )
                    OutlinedTextField(
                        value = state.bankBranch,
                        onValueChange = viewModel::onBankBranchChange,
                        label = { Text("Branch") },
                        placeholder = { Text("e.g. Kandy, Peradeniya") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_bank_branch_input")
                    )
                    OutlinedTextField(
                        value = state.accountHolderName,
                        onValueChange = viewModel::onAccountHolderChange,
                        label = { Text("Account Holder Name") },
                        placeholder = { Text("Name as in passbook") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_account_holder_input")
                    )
                    OutlinedTextField(
                        value = state.accountNumber,
                        onValueChange = viewModel::onAccountNumberChange,
                        label = { Text("Account Number") },
                        placeholder = { Text("Account number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_account_number_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::saveBankDetails,
                    enabled = !state.isSavingBank && state.bankName.isNotBlank() && state.accountNumber.isNotBlank(),
                    modifier = Modifier.testTag("save_bank_dialog_button")
                ) {
                    if (state.isSavingBank) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Details")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeBankDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Farmer Registration Dialog
    if (state.isFarmerDialogOpen) {
        val farmerScrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = viewModel::closeFarmerRegistrationDialog,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Agriculture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register Farm Details", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(farmerScrollState)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Provide your cultivation location and crop types. Buyers will only see your general district and town.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Cultivation District
                    ExposedDropdownMenuBox(
                        expanded = farmerCultDistrictExpanded,
                        onExpandedChange = { farmerCultDistrictExpanded = !farmerCultDistrictExpanded }
                    ) {
                        val dName = state.districts.find { it.id == state.farmerCultDistrictId }?.name ?: "Select Cultivation District"
                        OutlinedTextField(
                            value = dName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cultivation District") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmerCultDistrictExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = farmerCultDistrictExpanded,
                            onDismissRequest = { farmerCultDistrictExpanded = false }
                        ) {
                            state.districts.forEach { dist ->
                                DropdownMenuItem(
                                    text = { Text(dist.name) },
                                    onClick = {
                                        viewModel.onFarmerCultDistrictChange(dist.id)
                                        farmerCultDistrictExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Cultivation City
                    ExposedDropdownMenuBox(
                        expanded = farmerCultCityExpanded,
                        onExpandedChange = { farmerCultCityExpanded = !farmerCultCityExpanded }
                    ) {
                        val cName = state.farmerCultCities.find { it.id == state.farmerCultCityId }?.name ?: "Select Town / DS Division"
                        OutlinedTextField(
                            value = cName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cultivation Town / DS") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmerCultCityExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = farmerCultCityExpanded,
                            onDismissRequest = { farmerCultCityExpanded = false }
                        ) {
                            state.farmerCultCities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city.name) },
                                    onClick = {
                                        viewModel.onFarmerCultCityChange(city.id)
                                        farmerCultCityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Cultivation Address
                    OutlinedTextField(
                        value = state.farmerCultAddress,
                        onValueChange = viewModel::onFarmerCultAddressChange,
                        label = { Text("Farm / Cultivation Address") },
                        placeholder = { Text("e.g. Village Farm, Kandy Road") },
                        singleLine = false,
                        maxLines = 2,
                        supportingText = { Text("Confidential. Kept secure.") },
                        modifier = Modifier.fillMaxWidth().testTag("farmer_address_input")
                    )

                    // Crops Chips
                    Text(
                        text = "Main Crops Harvested",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    // Common crop chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        viewModel.commonCropSuggestions.take(4).forEach { crop ->
                            val isSelected = state.farmerCrops.contains(crop)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) viewModel.removeFarmerCrop(crop)
                                    else viewModel.addFarmerCrop(crop)
                                },
                                label = { Text(crop, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Custom crop addition
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.farmerCustomCrop,
                            onValueChange = viewModel::onFarmerCustomCropChange,
                            placeholder = { Text("Add another crop...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom_crop_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.addFarmerCrop(state.farmerCustomCrop) },
                            enabled = state.farmerCustomCrop.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }

                    if (state.farmerCrops.isNotEmpty()) {
                        Text(
                            text = "Selected (${state.farmerCrops.size}): ${state.farmerCrops.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Pickup Landmark
                    OutlinedTextField(
                        value = state.farmerPickupLandmark,
                        onValueChange = viewModel::onFarmerPickupLandmarkChange,
                        label = { Text("Default Pickup Location (Optional)") },
                        placeholder = { Text("e.g. Clock tower junction") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.farmerRegisterError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.farmerRegisterError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitFarmerRegistration() },
                    enabled = !state.isRegisteringFarmer && state.farmerCultAddress.isNotBlank() && state.farmerCrops.isNotEmpty(),
                    modifier = Modifier.testTag("submit_farmer_registration_button")
                ) {
                    if (state.isRegisteringFarmer) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Register as Farmer", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeFarmerRegistrationDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

