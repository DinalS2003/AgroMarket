package com.example.ui.screens.marketplace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ListingItem
import com.example.ui.components.*
import com.example.viewmodel.MarketplaceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel,
    onListingClick: (ListingItem) -> Unit,
    onChatClick: (ListingItem) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-refresh when entering marketplace screen to pick up newly added/edited crops
    LaunchedEffect(Unit) {
        viewModel.refreshMarketplace()
    }

    // Scroll to top when page changes
    LaunchedEffect(state.currentPage) {
        listState.animateScrollToItem(0)
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact Header: Sleek Search Bar & Single-Row Filters
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    // Row 1: Compact Search Bar with integrated action icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        androidx.compose.foundation.text.BasicTextField(
                            value = state.cropQuery,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("crop_search_input"),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.cropQuery.isEmpty()) {
                                        Text(
                                            text = "Search crops, produce...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (state.cropQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onQueryChange("") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_search_btn")
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Refresh button inside search bar
                        IconButton(
                            onClick = { viewModel.refreshMarketplace() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("refresh_marketplace_button")
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh Marketplace",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Tune / Price Filter icon button with active badge
                        val hasPriceFilter = (state.minPrice != null || state.maxPrice != null)
                        BadgedBox(
                            badge = {
                                if (hasPriceFilter) {
                                    Badge(modifier = Modifier.size(6.dp))
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleFilterSheet(true) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("price_filter_button")
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Filter Price",
                                    tint = if (hasPriceFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Autocomplete suggestions dropdown (if any)
                    if (state.filteredSuggestions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column {
                                state.filteredSuggestions.forEach { sugg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.onSuggestionSelected(sugg) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Eco, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(sugg, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: Single Streamlined Filter Row (NO DUPLICATE DISTRICT CHIPS!)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Single District Filter Pill: opens the 25-district sheet; shows selected district or All
                        item {
                            val isDistrictSelected = state.selectedDistrictId != null
                            val districtName = if (isDistrictSelected) {
                                state.districts.find { it.id == state.selectedDistrictId }?.name ?: "District"
                            } else {
                                "All Districts"
                            }

                            FilterChip(
                                selected = isDistrictSelected,
                                onClick = { viewModel.toggleDistrictSheet(true) },
                                label = {
                                    Text(
                                        text = "📍 $districtName",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                trailingIcon = {
                                    if (isDistrictSelected) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Clear district filter",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.onDistrictChange(null) }
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.ArrowDropDown,
                                            contentDescription = "Select District",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("select_district_chip")
                            )
                        }

                        // 2. Category Filter Chips
                        val categories = listOf("All", "Vegetables", "Fruits", "Grains & Cereals", "Spices & Herbs")
                        items(categories) { cat ->
                            FilterChip(
                                selected = state.selectedCategory == cat,
                                onClick = { viewModel.onCategoryChange(cat) },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("category_chip_$cat")
                            )
                        }

                        // 3. Price Filter Chip
                        item {
                            val isPriceActive = (state.minPrice != null || state.maxPrice != null)
                            val priceLabel = if (isPriceActive) {
                                "💰 Rs.${state.minPrice?.toInt() ?: 0}-${state.maxPrice?.toInt() ?: "max"}"
                            } else {
                                "💰 Price"
                            }

                            FilterChip(
                                selected = isPriceActive,
                                onClick = { viewModel.toggleFilterSheet(true) },
                                label = { Text(priceLabel, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = if (isPriceActive) {
                                    {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Clear price",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.onPriceFilterChange(null, null) }
                                        )
                                    }
                                } else null
                            )
                        }

                        // 4. Sort Chip (cycles sort or shows current sort)
                        item {
                            val sortLabel = when (state.sortOption) {
                                "rating" -> "★ Top Rated"
                                "price_asc" -> "Price: Low ▾"
                                "price_desc" -> "Price: High ▾"
                                else -> "Newest ▾"
                            }
                            AssistChip(
                                onClick = {
                                    val nextSort = when (state.sortOption) {
                                        "newest" -> "rating"
                                        "rating" -> "price_asc"
                                        "price_asc" -> "price_desc"
                                        else -> "newest"
                                    }
                                    viewModel.onSortChange(nextSort)
                                },
                                label = { Text(sortLabel, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(13.dp))
                                },
                                modifier = Modifier.testTag("sort_chip")
                            )
                        }
                    }

                    // Active filters indicator bar (compact 1-line; shown only if any filter active)
                    val isAnyFilterActive = state.selectedDistrictId != null ||
                            state.selectedCategory != "All" ||
                            state.minPrice != null ||
                            state.maxPrice != null ||
                            state.cropQuery.isNotBlank()

                    if (isAnyFilterActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${state.totalCount} matching crops",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Clear All Filters",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier
                                    .clickable { viewModel.clearAllFilters() }
                                    .padding(vertical = 2.dp)
                                    .testTag("clear_all_filters_button")
                            )
                        }
                    }
                }
            }

            // Main Content Area with PullToRefreshBox
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refreshMarketplace() },
                state = pullToRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (state.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            LoadingSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                    }
                } else if (!state.errorMessage.isNullOrBlank() && !state.errorMessage!!.contains("jwt", ignoreCase = true)) {
                    ErrorRetryView(
                        message = state.errorMessage!!,
                        onRetry = { viewModel.loadListings() }
                    )
                } else if (state.filteredListings.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Filled.Storefront,
                        title = "No Crops Found",
                        message = if (state.selectedDistrictId != null) {
                            val distName = state.districts.find { it.id == state.selectedDistrictId }?.name ?: "this district"
                            "No crops currently listed in $distName. Try selecting 'All Districts' or clearing filters."
                        } else {
                            "No harvest found matching your criteria. Try adjusting or clearing search filters."
                        },
                        actionLabel = "Show All Crops",
                        onAction = { viewModel.clearAllFilters() }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("marketplace_listing_list")
                    ) {
                        // Listing Cards for current page
                        items(state.pagedListings, key = { it.id }) { listing ->
                            ListingCard(
                                listing = listing,
                                onClick = { onListingClick(listing) },
                                onChatClick = { onChatClick(listing) }
                            )
                        }

                        // Complete Pagination Controls Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .testTag("pagination_controls")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val start = if (state.totalCount == 0) 0 else (state.currentPage - 1) * state.pageSize + 1
                                    val end = minOf(state.currentPage * state.pageSize, state.totalCount)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Showing $start - $end of ${state.totalCount} crops",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Items per page selection
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Per page: ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            listOf(4, 8, 12).forEach { size ->
                                                val isCur = state.pageSize == size
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier
                                                        .padding(horizontal = 2.dp)
                                                        .clickable { viewModel.onPageSizeChange(size) }
                                                ) {
                                                    Text(
                                                        text = "$size",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isCur) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Previous / Next Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = viewModel::prevPage,
                                            enabled = state.currentPage > 1,
                                            modifier = Modifier.testTag("prev_page_button")
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Previous")
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = "Page ${state.currentPage} of ${state.totalPages}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }

                                        Button(
                                            onClick = viewModel::nextPage,
                                            enabled = state.currentPage < state.totalPages,
                                            modifier = Modifier.testTag("next_page_button")
                                        ) {
                                            Text("Next")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Numbered page pill jump buttons
                                    if (state.totalPages > 1) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            for (p in 1..state.totalPages) {
                                                val isSelected = (p == state.currentPage)
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier
                                                        .clickable { viewModel.goToPage(p) }
                                                        .testTag("page_chip_$p")
                                                ) {
                                                    Text(
                                                        text = "$p",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // District Selection BottomSheet (All 25 districts of Sri Lanka)
    if (state.isDistrictSheetVisible) {
        var districtSearchQuery by remember { mutableStateOf("") }
        val filteredDistricts = remember(state.districts, districtSearchQuery) {
            if (districtSearchQuery.isBlank()) {
                state.districts
            } else {
                state.districts.filter {
                    it.name.contains(districtSearchQuery, ignoreCase = true) ||
                    it.province.contains(districtSearchQuery, ignoreCase = true)
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleDistrictSheet(false) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Cultivation District",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { viewModel.toggleDistrictSheet(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = districtSearchQuery,
                    onValueChange = { districtSearchQuery = it },
                    placeholder = { Text("Search 25 Sri Lankan districts...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // "All Districts" choice
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.selectedDistrictId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onDistrictChange(null)
                                }
                                .testTag("select_all_districts_item")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = if (state.selectedDistrictId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "All Districts (Island-wide)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (state.selectedDistrictId == null) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        Text(
                                            text = "Show harvest crops from all across Sri Lanka",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.selectedDistrictId == null) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    items(filteredDistricts) { dist ->
                        val isSelected = (state.selectedDistrictId == dist.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onDistrictChange(dist.id)
                                }
                                .testTag("select_district_item_${dist.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = dist.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        Text(
                                            text = "${dist.province} Province",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Price Filter Sheet
    if (state.isFilterSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleFilterSheet(false) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Text(
                    text = "Filter by Price (Rs. / kg)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Price presets
                Text("Quick Price Presets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SuggestionChip(
                            onClick = {
                                viewModel.onPriceFilterChange(null, 300.0)
                                viewModel.toggleFilterSheet(false)
                            },
                            label = { Text("< Rs. 300") }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                viewModel.onPriceFilterChange(null, 500.0)
                                viewModel.toggleFilterSheet(false)
                            },
                            label = { Text("< Rs. 500") }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                viewModel.onPriceFilterChange(300.0, 600.0)
                                viewModel.toggleFilterSheet(false)
                            },
                            label = { Text("Rs. 300 - 600") }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                viewModel.onPriceFilterChange(500.0, null)
                                viewModel.toggleFilterSheet(false)
                            },
                            label = { Text("> Rs. 500") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                var minInput by remember { mutableStateOf(state.minPrice?.toString() ?: "") }
                var maxInput by remember { mutableStateOf(state.maxPrice?.toString() ?: "") }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = minInput,
                        onValueChange = { minInput = it },
                        label = { Text("Min Rs./kg") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("min_price_input")
                    )
                    OutlinedTextField(
                        value = maxInput,
                        onValueChange = { maxInput = it },
                        label = { Text("Max Rs./kg") },
                        placeholder = { Text("1000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("max_price_input")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.onPriceFilterChange(null, null)
                            viewModel.toggleFilterSheet(false)
                        },
                        modifier = Modifier.weight(1f).testTag("reset_price_button")
                    ) {
                        Text("Reset")
                    }

                    Button(
                        onClick = {
                            viewModel.onPriceFilterChange(minInput.toDoubleOrNull(), maxInput.toDoubleOrNull())
                            viewModel.toggleFilterSheet(false)
                        },
                        modifier = Modifier.weight(1f).testTag("apply_price_button")
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

fun getCropVectorIcon(cropName: String): ImageVector {
    val lower = cropName.lowercase()
    return when {
        lower.contains("carrot") || lower.contains("radish") -> Icons.Filled.Spa
        lower.contains("onion") || lower.contains("garlic") || lower.contains("leek") -> Icons.Filled.Grass
        lower.contains("tomato") || lower.contains("brinjal") || lower.contains("eggplant") -> Icons.Filled.Eco
        lower.contains("chilli") || lower.contains("pepper") -> Icons.Filled.LocalFireDepartment
        lower.contains("potato") || lower.contains("manioc") || lower.contains("yam") -> Icons.Filled.Park
        lower.contains("banana") || lower.contains("papaya") || lower.contains("fruit") -> Icons.Filled.Yard
        else -> Icons.Filled.Eco
    }
}

@Composable
fun CropThumbnail(
    photos: List<String>,
    cropName: String,
    quantityKg: Double,
    modifier: Modifier = Modifier
) {
    val photoUrl = photos.firstOrNull()
    val isBulk = quantityKg >= 250.0
    val badgeLabel = if (isBulk) "Bulk" else "Fresh"

    Box(
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Color(0xFFE8F5EE))
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = cropName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val icon = getCropVectorIcon(cropName)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = cropName,
                    tint = androidx.compose.ui.graphics.Color(0xFF1B4D3E),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Dark forest green bottom badge ("Fresh" or "Bulk")
        Surface(
            color = androidx.compose.ui.graphics.Color(0xFF1E4D3E),
            shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .align(Alignment.BottomCenter)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun ListingCard(
    listing: ListingItem,
    onClick: () -> Unit,
    onChatClick: () -> Unit = {}
) {
    val photoUrl = listing.photos.firstOrNull()
    val isBulk = listing.quantityAvailable >= 200.0
    val hasMultiplePhotos = listing.photos.size > 1

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE5E7EB)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("listing_card_${listing.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero Crop Image Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFE8F5EE))
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = listing.cropName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient overlay for contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.40f),
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )
                } else {
                    // Stylized crop vector banner if no photo provided
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color(0xFFE2F3EB),
                                        androidx.compose.ui.graphics.Color(0xFFCEEBDC)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getCropVectorIcon(listing.cropName),
                                contentDescription = listing.cropName,
                                tint = androidx.compose.ui.graphics.Color(0xFF1B4D3E),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Verified Harvest",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = androidx.compose.ui.graphics.Color(0xFF1E4D3E)
                                )
                            )
                        }
                    }
                }

                // Top Left: Fresh / Bulk Status Badge
                Surface(
                    color = if (isBulk) androidx.compose.ui.graphics.Color(0xFF0F5132) else androidx.compose.ui.graphics.Color(0xFF137333),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBulk) Icons.Filled.Inventory2 else Icons.Filled.Spa,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBulk) "Bulk Stock" else "Fresh Harvest",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                }

                // Top Right: Top Farmer or Rating Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (listing.isTopFarmer) {
                        Surface(
                            color = androidx.compose.ui.graphics.Color(0xFFD97706),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Top Farmer",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                )
                            }
                        }
                    }

                    if (listing.ratingAvg > 0) {
                        Surface(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", listing.ratingAvg),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Bottom Left: Delivery readiness badge
                val deliveryText = if (listing.isAvailableNow) "Delivery Ready" else "Harvest: ${listing.harvestDate}"
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (listing.isAvailableNow) Icons.Filled.LocalShipping else Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = deliveryText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Bottom Right: Photo Count Badge if multiple
                if (hasMultiplePhotos) {
                    Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${listing.photos.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Card Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Row 1: Crop Name and Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = listing.cropName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFF0F3E33),
                            fontSize = 16.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    // Price e.g. "Rs. 320/kg"
                    Row(verticalAlignment = Alignment.Bottom) {
                        val priceStr = if (listing.pricePerKg % 1.0 == 0.0) {
                            listing.pricePerKg.toInt().toString()
                        } else {
                            String.format(java.util.Locale.US, "%.1f", listing.pricePerKg)
                        }
                        Text(
                            text = "Rs. $priceStr",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color(0xFF006D5B),
                                fontSize = 17.5.sp
                            )
                        )
                        Text(
                            text = "/kg",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = androidx.compose.ui.graphics.Color(0xFF6B7280),
                                fontSize = 11.5.sp
                            ),
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Farmer & Location
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF007A5E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    val farmerName = listing.farmerFirstName ?: "Farmer"
                    val location = listing.cultivationCityName ?: listing.cultivationDistrictName ?: "Sri Lanka"
                    Text(
                        text = "$farmerName • $location",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF4B5563),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 3: Stock Chips & Order Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val stockFormatted = if (listing.quantityAvailable % 1.0 == 0.0) {
                            "${listing.quantityAvailable.toInt()} kg"
                        } else {
                            "${listing.quantityAvailable} kg"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color(0xFFF3F4F6)
                        ) {
                            Text(
                                text = "Stock: $stockFormatted",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = androidx.compose.ui.graphics.Color(0xFF374151)
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        val minFormatted = if (listing.minOrderKg % 1.0 == 0.0) {
                            "${listing.minOrderKg.toInt()} kg"
                        } else {
                            "${listing.minOrderKg} kg"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color(0xFFF3F4F6)
                        ) {
                            Text(
                                text = "Min: $minFormatted",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = androidx.compose.ui.graphics.Color(0xFF4B5563)
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onChatClick,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Chat with farmer",
                                tint = androidx.compose.ui.graphics.Color(0xFF007A5E),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF006D5B),
                            modifier = Modifier.clickable(onClick = onClick)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "View",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
