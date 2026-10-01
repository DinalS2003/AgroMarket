package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiClient
import com.example.data.models.CropSuggestion
import com.example.data.models.District
import com.example.data.models.ListingItem
import com.example.data.repository.AgroMarketRepository
import com.example.ui.components.toUserFriendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class MarketplaceUiState(
    val selectedDistrictId: Int? = null, // null = All Districts (Island-wide)
    val districts: List<District> = emptyList(),
    val cropQuery: String = "",
    val selectedCategory: String = "All", // "All", "Vegetables", "Fruits", "Grains & Cereals", "Spices & Herbs"
    val cropSuggestions: List<CropSuggestion> = emptyList(),
    val filteredSuggestions: List<String> = emptyList(),
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sortOption: String = "newest", // 'newest' | 'price_asc' | 'price_desc' | 'rating'
    val masterListings: List<ListingItem> = emptyList(), // all loaded non-deleted listings
    val allListings: List<ListingItem> = emptyList(),
    val filteredListings: List<ListingItem> = emptyList(),
    val pagedListings: List<ListingItem> = emptyList(),
    val currentPage: Int = 1,
    val pageSize: Int = 4, // 4 items per page for clear pagination
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isFilterSheetVisible: Boolean = false,
    val isDistrictSheetVisible: Boolean = false
)

class MarketplaceViewModel(
    private val repository: AgroMarketRepository,
    initialDistrictId: Int? = null // Defaults to null = All Districts
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketplaceUiState(selectedDistrictId = initialDistrictId))
    val uiState: StateFlow<MarketplaceUiState> = _uiState.asStateFlow()

    init {
        loadDistricts()
        loadSuggestions()
        loadListings()
    }

    fun loadDistricts() {
        viewModelScope.launch {
            val res = repository.getDistricts()
            res.onSuccess { list ->
                _uiState.update { it.copy(districts = list) }
            }
        }
    }

    fun loadSuggestions() {
        viewModelScope.launch {
            val res = repository.getCropSuggestions()
            res.onSuccess { list ->
                _uiState.update { it.copy(cropSuggestions = list) }
            }
        }
    }

    fun onDistrictChange(districtId: Int?) {
        _uiState.update { state ->
            val updated = state.copy(
                selectedDistrictId = districtId,
                currentPage = 1,
                isDistrictSheetVisible = false
            )
            applyFiltersAndPagination(updated)
        }
    }

    fun toggleDistrictSheet(visible: Boolean) {
        _uiState.update { it.copy(isDistrictSheetVisible = visible) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { state ->
            val updated = state.copy(selectedCategory = category, currentPage = 1)
            applyFiltersAndPagination(updated)
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { state ->
            val matching = if (query.isNotBlank()) {
                state.cropSuggestions
                    .map { it.displayName }
                    .filter { it.contains(query, ignoreCase = true) }
                    .take(6)
            } else {
                emptyList()
            }
            val updated = state.copy(cropQuery = query, filteredSuggestions = matching, currentPage = 1)
            applyFiltersAndPagination(updated)
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        val cleanName = suggestion.split("(").firstOrNull()?.trim() ?: suggestion
        _uiState.update { state ->
            val updated = state.copy(cropQuery = cleanName, filteredSuggestions = emptyList(), currentPage = 1)
            applyFiltersAndPagination(updated)
        }
    }

    fun onPriceFilterChange(min: Double?, max: Double?) {
        _uiState.update { state ->
            val updated = state.copy(minPrice = min, maxPrice = max, currentPage = 1)
            applyFiltersAndPagination(updated)
        }
    }

    fun onSortChange(sort: String) {
        _uiState.update { state ->
            val updated = state.copy(sortOption = sort, currentPage = 1)
            applyFiltersAndPagination(updated)
        }
    }

    fun onPageSizeChange(newSize: Int) {
        if (newSize > 0) {
            _uiState.update { state ->
                val updated = state.copy(pageSize = newSize, currentPage = 1)
                applyFiltersAndPagination(updated)
            }
        }
    }

    fun toggleFilterSheet(visible: Boolean) {
        _uiState.update { it.copy(isFilterSheetVisible = visible) }
    }

    fun clearAllFilters() {
        _uiState.update { state ->
            val updated = state.copy(
                selectedDistrictId = null,
                cropQuery = "",
                selectedCategory = "All",
                minPrice = null,
                maxPrice = null,
                sortOption = "newest",
                currentPage = 1,
                filteredSuggestions = emptyList()
            )
            applyFiltersAndPagination(updated)
        }
    }

    fun refreshMarketplace() {
        loadListings(isRefresh = true)
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.currentPage < state.totalPages) {
            goToPage(state.currentPage + 1)
        }
    }

    fun prevPage() {
        val state = _uiState.value
        if (state.currentPage > 1) {
            goToPage(state.currentPage - 1)
        }
    }

    fun goToPage(page: Int) {
        _uiState.update { state ->
            val clamped = page.coerceIn(1, state.totalPages)
            val updated = state.copy(currentPage = clamped)
            val startIdx = (clamped - 1) * updated.pageSize
            val pageSlice = updated.filteredListings.drop(startIdx).take(updated.pageSize)
            updated.copy(pagedListings = pageSlice)
        }
    }

    fun loadListings(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && it.masterListings.isEmpty(),
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            // Always load the latest active listings from repository
            val res = repository.searchListings(
                districtId = null,
                query = null,
                minPrice = null,
                maxPrice = null,
                sort = "newest",
                limit = 200
            )

            res.onSuccess { list ->
                _uiState.update { current ->
                    val stateWithListings = current.copy(
                        masterListings = list,
                        isLoading = false,
                        isRefreshing = false
                    )
                    applyFiltersAndPagination(stateWithListings)
                }
            }.onFailure { err ->
                val raw = err.message ?: ""
                val friendly = err.toUserFriendlyMessage("Failed to load listings")
                val isJwtExpired = raw.contains("jwt", ignoreCase = true) ||
                        raw.contains("PGRST301", ignoreCase = true) ||
                        raw.contains("token expired", ignoreCase = true) ||
                        raw.contains("401", ignoreCase = true) ||
                        friendly.contains("jwt", ignoreCase = true)

                if (isJwtExpired) {
                    ApiClient.notifySessionExpired()
                }

                _uiState.update {
                    it.copy(
                        errorMessage = if (isJwtExpired || friendly.isBlank()) null else friendly,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    private fun applyFiltersAndPagination(state: MarketplaceUiState): MarketplaceUiState {
        var filtered = state.masterListings

        // 1. District Filter (null means "All Districts")
        if (state.selectedDistrictId != null && state.selectedDistrictId > 0) {
            val targetDistrict = state.districts.firstOrNull { it.id == state.selectedDistrictId }
            val targetDistrictName = targetDistrict?.name
            if (targetDistrictName != null) {
                filtered = filtered.filter { item ->
                    item.cultivationDistrictName?.equals(targetDistrictName, ignoreCase = true) == true ||
                    item.cultivationDistrictName?.contains(targetDistrictName, ignoreCase = true) == true
                }
            }
        }

        // 2. Category filter
        if (state.selectedCategory != "All") {
            filtered = when (state.selectedCategory) {
                "Vegetables" -> filtered.filter { item ->
                    val name = item.cropName.lowercase()
                    val key = item.cropNameKey?.lowercase() ?: ""
                    name.contains("tomato") || key.contains("tomato") ||
                    name.contains("carrot") || key.contains("carrot") ||
                    name.contains("beans") || key.contains("beans") ||
                    name.contains("cabbage") || key.contains("cabbage") ||
                    name.contains("leeks") || key.contains("leeks") ||
                    name.contains("brinjal") || key.contains("brinjal") ||
                    name.contains("eggplant") ||
                    name.contains("pumpkin") || key.contains("pumpkin") ||
                    name.contains("beetroot") || key.contains("beetroot") ||
                    name.contains("radish") || name.contains("cucumber") ||
                    name.contains("okra") || name.contains("capsicum") ||
                    name.contains("bell pepper") || name.contains("manioc") ||
                    name.contains("cassava") || name.contains("spinach") ||
                    name.contains("bitter gourd") || name.contains("snake gourd")
                }
                "Fruits" -> filtered.filter { item ->
                    val name = item.cropName.lowercase()
                    val key = item.cropNameKey?.lowercase() ?: ""
                    name.contains("banana") || key.contains("banana") ||
                    name.contains("ambul") || name.contains("kolikuttu") ||
                    name.contains("papaya") || key.contains("papaya") ||
                    name.contains("mango") || name.contains("fruit") ||
                    name.contains("avocado") || name.contains("watermelon") ||
                    name.contains("pineapple") || name.contains("guava") ||
                    name.contains("orange") || name.contains("lime") ||
                    name.contains("lemon") || name.contains("passion") ||
                    name.contains("rambutan") || name.contains("mangosteen")
                }
                "Grains & Cereals" -> filtered.filter { item ->
                    val name = item.cropName.lowercase()
                    val key = item.cropNameKey?.lowercase() ?: ""
                    name.contains("rice") || name.contains("paddy") ||
                    name.contains("corn") || name.contains("maize") ||
                    name.contains("kurakkan") || name.contains("grain") ||
                    name.contains("wheat") || name.contains("dhal") ||
                    name.contains("lentil") || name.contains("cereal") ||
                    name.contains("gram") || name.contains("mung")
                }
                "Spices & Herbs" -> filtered.filter { item ->
                    val name = item.cropName.lowercase()
                    val key = item.cropNameKey?.lowercase() ?: ""
                    name.contains("chilli") || name.contains("chili") || key.contains("chilli") ||
                    name.contains("onion") || key.contains("onion") ||
                    name.contains("potato") || key.contains("potato") ||
                    name.contains("pepper") || name.contains("ginger") ||
                    name.contains("garlic") || name.contains("turmeric") ||
                    name.contains("cinnamon") || name.contains("cardamom") ||
                    name.contains("cloves") || name.contains("curry leaves") ||
                    name.contains("spice")
                }
                else -> filtered
            }
        }

        // 3. Search Query filter (matches crop name, Sinhala/Tamil displayName, district, city, farmer name)
        if (state.cropQuery.isNotBlank()) {
            val q = state.cropQuery.trim().lowercase()
            filtered = filtered.filter { item ->
                item.cropName.lowercase().contains(q) ||
                (item.cropNameKey != null && item.cropNameKey.lowercase().contains(q)) ||
                (item.cultivationDistrictName != null && item.cultivationDistrictName.lowercase().contains(q)) ||
                (item.cultivationCityName != null && item.cultivationCityName.lowercase().contains(q)) ||
                (item.farmerFirstName != null && item.farmerFirstName.lowercase().contains(q))
            }
        }

        // 4. Price filter
        if (state.minPrice != null && state.minPrice > 0) {
            filtered = filtered.filter { it.pricePerKg >= state.minPrice }
        }
        if (state.maxPrice != null && state.maxPrice > 0) {
            filtered = filtered.filter { it.pricePerKg <= state.maxPrice }
        }

        // 5. Sort
        filtered = when (state.sortOption) {
            "price_asc" -> filtered.sortedBy { it.pricePerKg }
            "price_desc" -> filtered.sortedByDescending { it.pricePerKg }
            "rating" -> filtered.sortedWith(compareByDescending<ListingItem> { it.ratingAvg }.thenByDescending { it.reliabilityScore })
            else -> filtered.sortedByDescending { it.createdAt ?: "" }
        }

        // 6. Pagination
        val total = filtered.size
        val pages = maxOf(1, ceil(total.toDouble() / state.pageSize).toInt())
        val validPage = state.currentPage.coerceIn(1, pages)
        val start = (validPage - 1) * state.pageSize
        val paged = filtered.drop(start).take(state.pageSize)

        return state.copy(
            allListings = state.masterListings,
            filteredListings = filtered,
            pagedListings = paged,
            totalCount = total,
            totalPages = pages,
            currentPage = validPage
        )
    }
}
