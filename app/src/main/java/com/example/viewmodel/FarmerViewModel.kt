package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiClient
import com.example.data.models.CropSuggestion
import com.example.data.models.FarmerStats
import com.example.data.models.ListingItem
import com.example.data.models.OrderItem
import com.example.data.repository.AgroMarketRepository
import com.example.ui.components.toUserFriendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class FarmerDashboardUiState(
    val stats: FarmerStats? = null,
    val totalEarnings: Double = 0.0,
    val pendingPayouts: Double = 0.0,
    val completedOrdersCount: Int = 0,
    val activeListingsCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class AddEditListingUiState(
    val listingId: String? = null,
    val cropName: String = "",
    val cropSuggestions: List<CropSuggestion> = emptyList(),
    val filteredSuggestions: List<String> = emptyList(),
    val quantityKg: String = "",
    val pricePerKg: String = "",
    val minOrderKg: String = "",
    val harvestDate: String = "",
    val photos: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val errorMessage: String? = null
)

class FarmerViewModel(
    private val repository: AgroMarketRepository
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(FarmerDashboardUiState())
    val dashboardState: StateFlow<FarmerDashboardUiState> = _dashboardState.asStateFlow()

    private val _myListings = MutableStateFlow<List<ListingItem>>(emptyList())
    val myListings: StateFlow<List<ListingItem>> = _myListings.asStateFlow()

    private val _addEditState = MutableStateFlow(AddEditListingUiState())
    val addEditState: StateFlow<AddEditListingUiState> = _addEditState.asStateFlow()

    private val _isLoadingListings = MutableStateFlow(false)
    val isLoadingListings: StateFlow<Boolean> = _isLoadingListings.asStateFlow()

    fun loadDashboard(farmerId: String) {
        viewModelScope.launch {
            _dashboardState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Stats
            val statsRes = repository.getFarmerStats(farmerId)
            val stats = statsRes.getOrNull()

            // 2. Orders summary for earnings & pending payouts
            val ordersRes = repository.getFarmerOrders(farmerId)
            val orders = ordersRes.getOrDefault(emptyList())

            var totalEarned = 0.0
            var pendingPayouts = 0.0

            for (o in orders) {
                if (o.status == "completed" && o.payoutStatus == "paid_out") {
                    totalEarned += o.payoutAmount
                } else if (o.status == "completed" && o.payoutStatus == "pending") {
                    pendingPayouts += o.payoutAmount
                }
            }

            // 3. Listings count
            val listingsRes = repository.getFarmerListings(farmerId)
            val activeCount = listingsRes.getOrDefault(emptyList()).count { it.quantityAvailable > 0 }

            _dashboardState.update {
                it.copy(
                    stats = stats,
                    totalEarnings = totalEarned,
                    pendingPayouts = pendingPayouts,
                    completedOrdersCount = stats?.completedOrders ?: 0,
                    activeListingsCount = activeCount,
                    isLoading = false
                )
            }
        }
    }

    fun loadMyListings(farmerId: String) {
        viewModelScope.launch {
            _isLoadingListings.value = true
            val res = repository.getFarmerListings(farmerId)
            res.onSuccess { list ->
                _myListings.value = list
            }
            _isLoadingListings.value = false
        }
    }

    fun quickUpdatePriceAndQty(listingId: String, newPrice: Double, newQty: Double, farmerId: String) {
        viewModelScope.launch {
            repository.updateListing(listingId, quantity = newQty, pricePerKg = newPrice)
            loadMyListings(farmerId)
        }
    }

    fun toggleListingActive(listingId: String, isActive: Boolean, farmerId: String) {
        viewModelScope.launch {
            repository.updateListing(listingId, isActive = isActive)
            loadMyListings(farmerId)
        }
    }

    // Add / Edit Listing
    fun initAddEditListing(listing: ListingItem? = null) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        if (listing != null) {
            _addEditState.value = AddEditListingUiState(
                listingId = listing.id,
                cropName = listing.cropName,
                quantityKg = listing.quantityAvailable.toString(),
                pricePerKg = listing.pricePerKg.toString(),
                minOrderKg = listing.minOrderKg.toString(),
                harvestDate = listing.harvestDate,
                photos = listing.photos
            )
        } else {
            _addEditState.value = AddEditListingUiState(
                harvestDate = todayStr,
                minOrderKg = "5.0"
            )
        }

        // Suggestions
        viewModelScope.launch {
            val res = repository.getCropSuggestions()
            res.onSuccess { list ->
                _addEditState.update { it.copy(cropSuggestions = list) }
            }
        }
    }

    fun onCropNameChange(name: String) {
        _addEditState.update { state ->
            val matching = if (name.isNotBlank()) {
                state.cropSuggestions
                    .map { it.displayName }
                    .filter { it.contains(name, ignoreCase = true) }
                    .take(5)
            } else {
                emptyList()
            }
            state.copy(cropName = name, filteredSuggestions = matching)
        }
    }

    fun onCropSuggestionSelected(sugg: String) {
        _addEditState.update {
            it.copy(cropName = sugg, filteredSuggestions = emptyList())
        }
    }

    fun onQuantityChange(qty: String) {
        _addEditState.update { it.copy(quantityKg = qty) }
    }

    fun onPriceChange(price: String) {
        _addEditState.update { it.copy(pricePerKg = price) }
    }

    fun onMinOrderChange(min: String) {
        _addEditState.update { it.copy(minOrderKg = min) }
    }

    fun onHarvestDateChange(date: String) {
        _addEditState.update { it.copy(harvestDate = date) }
    }

    fun addPhotoUrl(url: String) {
        val clean = url.trim()
        if (clean.isNotBlank() && _addEditState.value.photos.size < 5) {
            _addEditState.update { it.copy(photos = it.photos + clean) }
        }
    }

    fun removePhoto(url: String) {
        _addEditState.update { it.copy(photos = it.photos - url) }
    }

    fun removePhotoAt(index: Int) {
        _addEditState.update { state ->
            val list = state.photos.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
            }
            state.copy(photos = list)
        }
    }

    fun setCoverPhoto(url: String) {
        _addEditState.update { state ->
            val list = state.photos.toMutableList()
            if (list.remove(url)) {
                list.add(0, url)
            }
            state.copy(photos = list)
        }
    }

    fun saveListing(farmerId: String) {
        val state = _addEditState.value
        val name = state.cropName.trim()
        val qty = state.quantityKg.toDoubleOrNull()
        val price = state.pricePerKg.toDoubleOrNull()
        val minOrder = state.minOrderKg.toDoubleOrNull()

        if (name.length < 2 || name.length > 40) {
            _addEditState.update { it.copy(errorMessage = "Crop name must be between 2 and 40 characters") }
            return
        }
        if (qty == null || qty <= 0) {
            _addEditState.update { it.copy(errorMessage = "Please enter valid quantity (kg)") }
            return
        }
        if (price == null || price <= 0) {
            _addEditState.update { it.copy(errorMessage = "Please enter valid price per kg") }
            return
        }
        if (minOrder == null || minOrder <= 0) {
            _addEditState.update { it.copy(errorMessage = "Please enter valid minimum order (kg)") }
            return
        }
        if (minOrder > qty) {
            _addEditState.update { it.copy(errorMessage = "Minimum order cannot exceed available quantity") }
            return
        }
        if (state.harvestDate.isBlank()) {
            _addEditState.update { it.copy(errorMessage = "Harvest date is required") }
            return
        }

        viewModelScope.launch {
            _addEditState.update { it.copy(isSaving = true, errorMessage = null) }

            val res = if (state.listingId != null) {
                repository.updateListing(
                    listingId = state.listingId,
                    quantity = qty,
                    pricePerKg = price,
                    minOrderKg = minOrder,
                    photos = state.photos
                )
            } else {
                repository.createListing(
                    cropName = name,
                    quantity = qty,
                    pricePerKg = price,
                    minOrderKg = minOrder,
                    harvestDate = state.harvestDate,
                    photos = state.photos
                )
            }

            res.onSuccess {
                _addEditState.update { it.copy(isSaving = false, isSavedSuccess = true) }
                loadMyListings(farmerId)
            }.onFailure { err ->
                val raw = err.message ?: ""
                val friendly = err.toUserFriendlyMessage("Failed to save listing")
                val isJwtExpired = raw.contains("jwt", ignoreCase = true) ||
                        raw.contains("PGRST301", ignoreCase = true) ||
                        raw.contains("token expired", ignoreCase = true) ||
                        raw.contains("401", ignoreCase = true) ||
                        friendly.contains("jwt", ignoreCase = true)

                if (isJwtExpired) {
                    ApiClient.notifySessionExpired()
                }

                _addEditState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = if (isJwtExpired || friendly.isBlank()) null else friendly
                    )
                }
            }
        }
    }

    fun deleteListing(listingId: String, farmerId: String, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoadingListings.value = true
            repository.deleteListing(listingId)
            loadMyListings(farmerId)
            loadDashboard(farmerId)
            _isLoadingListings.value = false
            onDeleted?.invoke()
        }
    }
}
