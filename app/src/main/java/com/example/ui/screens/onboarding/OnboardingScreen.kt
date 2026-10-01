package com.example.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: AuthViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.onboardingState.collectAsState()

    if (state.isComplete) {
        LaunchedEffect(Unit) {
            onComplete()
        }
        return
    }

    if (state.currentStep == 2) {
        BackHandler {
            viewModel.goBackToStepOne()
        }
    }

    Crossfade(targetState = state.currentStep, label = "onboarding_step") { step ->
        when (step) {
            1 -> StepOnePersonalScreen(viewModel = viewModel)
            2 -> StepTwoFarmScreen(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepOnePersonalScreen(viewModel: AuthViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    var districtExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Create Your Account", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Step 1 of 2: Personal Details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
            item {
                Text(
                    text = "Welcome to AgroMarket! Enter your official details to establish a trusted profile in the agricultural network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Personal Information Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Personal Information",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedTextField(
                            value = state.fullName,
                            onValueChange = viewModel::onFullNameChange,
                            label = { Text("Full Name (Official)") },
                            placeholder = { Text("e.g. Ruwan Karunaratne") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_full_name")
                        )

                        OutlinedTextField(
                            value = state.nic,
                            onValueChange = viewModel::onNicChange,
                            label = { Text("National Identity Card (NIC)") },
                            placeholder = { Text("199012345678 or 123456789V") },
                            singleLine = true,
                            supportingText = {
                                if (state.nicError != null) {
                                    Text(state.nicError!!, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Confidential. Protected under Sri Lankan privacy standards.")
                                }
                            },
                            isError = state.nicError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_nic")
                        )

                        // District Selection
                        ExposedDropdownMenuBox(
                            expanded = districtExpanded,
                            onExpandedChange = { districtExpanded = !districtExpanded }
                        ) {
                            val selectedDistrictName = state.districts.find { it.id == state.selectedDistrictId }?.name ?: "Select District"
                            OutlinedTextField(
                                value = selectedDistrictName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Home District") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("district_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = districtExpanded,
                                onDismissRequest = { districtExpanded = false }
                            ) {
                                state.districts.forEach { dist ->
                                    DropdownMenuItem(
                                        text = { Text(dist.name) },
                                        onClick = {
                                            viewModel.onDistrictSelected(dist.id)
                                            districtExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // City Selection
                        ExposedDropdownMenuBox(
                            expanded = cityExpanded,
                            onExpandedChange = { cityExpanded = !cityExpanded }
                        ) {
                            val selectedCityName = state.cities.find { it.id == state.selectedCityId }?.name ?: "Select Town / DS Division"
                            OutlinedTextField(
                                value = selectedCityName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Home Town / DS Division") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("city_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = cityExpanded,
                                onDismissRequest = { cityExpanded = false }
                            ) {
                                state.cities.forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(city.name) },
                                        onClick = {
                                            viewModel.onCitySelected(city.id)
                                            cityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Role Selection Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "How will you use AgroMarket?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // Buyer option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!state.isFarmerMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                width = if (!state.isFarmerMode) 2.dp else 1.dp,
                                color = if (!state.isFarmerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAccountType(false) }
                                .testTag("select_buyer_role")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !state.isFarmerMode,
                                    onClick = { viewModel.setAccountType(false) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Buyer (Wholesale or Consumer)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Browse listings, purchase fresh produce directly from farmers, and coordinate logistics.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Farmer option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.isFarmerMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                width = if (state.isFarmerMode) 2.dp else 1.dp,
                                color = if (state.isFarmerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAccountType(true) }
                                .testTag("select_farmer_role")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.isFarmerMode,
                                    onClick = { viewModel.setAccountType(true) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Farmer (Agricultural Producer)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "List crops, accept orders, and sell your harvest directly. Payment details can be added later in your profile.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Primary action button
            item {
                if (state.isFarmerMode) {
                    Button(
                        onClick = { viewModel.goToFarmerStep() },
                        enabled = !state.isSubmitting && state.fullName.isNotBlank() && state.nic.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("next_to_farm_details_button")
                    ) {
                        Text("Next: Cultivation Details", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = viewModel::submitOnboarding,
                        enabled = !state.isSubmitting && state.fullName.isNotBlank() && state.nic.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_buyer_onboarding_button")
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Complete Registration", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepTwoFarmScreen(viewModel: AuthViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    var cultDistrictExpanded by remember { mutableStateOf(false) }
    var cultCityExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Farm & Cultivation Details", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Step 2 of 2",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.goBackToStepOne() },
                        modifier = Modifier.testTag("back_to_step_one_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
            // Friendly info banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Agriculture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tell buyers where your harvest is cultivated. Your payment details can be added anytime later under your Profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Location Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Cultivation Location",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // Cultivation District
                        ExposedDropdownMenuBox(
                            expanded = cultDistrictExpanded,
                            onExpandedChange = { cultDistrictExpanded = !cultDistrictExpanded }
                        ) {
                            val distName = state.districts.find { it.id == state.cultivationDistrictId }?.name ?: "Select Cultivation District"
                            OutlinedTextField(
                                value = distName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cultivation District") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cultDistrictExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("cult_district_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = cultDistrictExpanded,
                                onDismissRequest = { cultDistrictExpanded = false }
                            ) {
                                state.districts.forEach { dist ->
                                    DropdownMenuItem(
                                        text = { Text(dist.name) },
                                        onClick = {
                                            viewModel.onCultivationDistrictSelected(dist.id)
                                            cultDistrictExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Cultivation City
                        ExposedDropdownMenuBox(
                            expanded = cultCityExpanded,
                            onExpandedChange = { cultCityExpanded = !cultCityExpanded }
                        ) {
                            val cityName = state.cultivationCities.find { it.id == state.cultivationCityId }?.name ?: "Select Cultivation City"
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cultivation City / DS Division") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cultCityExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("cult_city_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = cultCityExpanded,
                                onDismissRequest = { cultCityExpanded = false }
                            ) {
                                state.cultivationCities.forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(city.name) },
                                        onClick = {
                                            viewModel.onCultivationCitySelected(city.id)
                                            cultCityExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.cultivationAddress,
                            onValueChange = viewModel::onCultivationAddressChange,
                            label = { Text("Cultivation Address") },
                            placeholder = { Text("e.g. Kandy Road, Peradeniya") },
                            supportingText = {
                                Text("Confidential. Only your district and city are shown to buyers.")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cult_address_input")
                        )
                    }
                }
            }

            // Crops Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Main Crops Grown",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Select what you harvest or add your own crops:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick suggestion chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            viewModel.commonCropSuggestions.take(4).forEach { crop ->
                                val isSelected = state.mainCrops.contains(crop)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) viewModel.removeCropChip(crop)
                                        else viewModel.addCropChip(crop)
                                    },
                                    label = { Text(crop) }
                                )
                            }
                        }

                        // Custom Crop Adder
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.customCropInput,
                                onValueChange = viewModel::onCustomCropInputChange,
                                placeholder = { Text("Add another crop...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_crop_input")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.addCropChip(state.customCropInput) },
                                enabled = state.customCropInput.isNotBlank(),
                                modifier = Modifier.testTag("add_crop_button")
                            ) {
                                Text("Add")
                            }
                        }

                        // Selected Chips
                        if (state.mainCrops.isNotEmpty()) {
                            Text(
                                text = "Selected Crops (${state.mainCrops.size}):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.mainCrops.forEach { crop ->
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.removeCropChip(crop) },
                                        label = { Text(crop) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Optional Pickup Landmark Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Pickup Landmark (Optional)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        OutlinedTextField(
                            value = state.defaultPickupLandmark,
                            onValueChange = viewModel::onDefaultPickupLandmarkChange,
                            label = { Text("Default Pickup Location") },
                            placeholder = { Text("e.g. Near clock tower junction") },
                            supportingText = {
                                Text("Shown to buyers for pickup orders only after payment is verified.")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pickup_landmark_input")
                        )
                    }
                }
            }

            // Error display
            if (state.errorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Submit Farmer Button
            item {
                Button(
                    onClick = viewModel::submitOnboarding,
                    enabled = !state.isSubmitting && state.cultivationAddress.isNotBlank() && state.mainCrops.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_farmer_onboarding_button")
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Complete Farmer Registration", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.goBackToStepOne() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Personal Details")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
