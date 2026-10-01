package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionManager
import com.example.data.models.City
import com.example.data.models.District
import com.example.data.models.FarmerPrivateData
import com.example.data.models.FarmerRecord
import com.example.data.models.UserProfile
import com.example.data.repository.AgroMarketRepository
import com.example.ui.components.toUserFriendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userId: String = "",
    val profile: UserProfile? = null,
    val farmerRecord: FarmerRecord? = null,
    val farmerPrivate: FarmerPrivateData? = null,
    val phone: String = "",
    val districts: List<District> = emptyList(),
    val cities: List<City> = emptyList(),
    val selectedDistrictId: Int = 1,
    val selectedCityId: Int = 1,
    val editName: String = "",
    val isFarmer: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    // Bank payout editor
    val isBankDialogOpen: Boolean = false,
    val bankName: String = "",
    val bankBranch: String = "",
    val accountHolderName: String = "",
    val accountNumber: String = "",
    val isSavingBank: Boolean = false,
    val bankSaveSuccess: Boolean = false,
    // Farmer Registration Dialog
    val isFarmerDialogOpen: Boolean = false,
    val farmerCultDistrictId: Int = 1,
    val farmerCultCityId: Int = 1,
    val farmerCultCities: List<City> = emptyList(),
    val farmerCultAddress: String = "",
    val farmerCrops: List<String> = emptyList(),
    val farmerCustomCrop: String = "",
    val farmerLandSize: String = "",
    val farmerLandUnit: String = "acres",
    val farmerPickupLandmark: String = "",
    val isRegisteringFarmer: Boolean = false,
    val farmerRegisterSuccess: Boolean = false,
    val farmerRegisterError: String? = null,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val repository: AgroMarketRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val commonCropSuggestions = listOf(
        "Tomato", "Carrot", "Beans", "Cabbage", "Leeks", "Brinjal", "Pumpkin", "Onion",
        "Red Onion", "Big Onion", "Green Chilli", "Potato", "Beetroot", "Banana", "Papaya"
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, userId = userId) }

            val distRes = repository.getDistricts()
            val districts = distRes.getOrDefault(emptyList())

            val profRes = repository.getProfile(userId)
            val profile = profRes.getOrNull()

            val isFarmerSession = sessionManager.isFarmer()
            val farmRes = repository.getFarmerRecord(userId)
            val farmerRecord = farmRes.getOrNull()

            val privRes = repository.getFarmerPrivate(userId)
            val farmerPrivate = privRes.getOrNull()

            val districtId = profile?.districtId ?: sessionManager.getUserDistrictId()
            val citiesRes = repository.getCities(districtId)
            val cities = citiesRes.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    profile = profile,
                    farmerRecord = farmerRecord,
                    farmerPrivate = farmerPrivate,
                    districts = districts,
                    cities = cities,
                    selectedDistrictId = districtId,
                    selectedCityId = profile?.cityId ?: sessionManager.getUserCityId(),
                    editName = profile?.fullName ?: sessionManager.getUserName() ?: "",
                    isFarmer = isFarmerSession || (farmerRecord != null),
                    bankName = farmerPrivate?.bankName ?: "",
                    bankBranch = farmerPrivate?.bankBranch ?: "",
                    accountHolderName = farmerPrivate?.accountHolderName ?: (profile?.fullName ?: ""),
                    accountNumber = farmerPrivate?.accountNumber ?: "",
                    isLoading = false
                )
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(editName = name) }
    }

    fun onDistrictChange(districtId: Int) {
        _uiState.update { it.copy(selectedDistrictId = districtId) }
        viewModelScope.launch {
            val res = repository.getCities(districtId)
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        cities = list,
                        selectedCityId = list.firstOrNull()?.id ?: 1
                    )
                }
            }
        }
    }

    fun onCityChange(cityId: Int) {
        _uiState.update { it.copy(selectedCityId = cityId) }
    }

    fun openBankDialog() {
        val priv = _uiState.value.farmerPrivate
        _uiState.update {
            it.copy(
                isBankDialogOpen = true,
                bankName = priv?.bankName ?: it.bankName,
                bankBranch = priv?.bankBranch ?: it.bankBranch,
                accountHolderName = priv?.accountHolderName ?: it.editName,
                accountNumber = priv?.accountNumber ?: it.accountNumber,
                bankSaveSuccess = false,
                errorMessage = null
            )
        }
    }

    fun closeBankDialog() {
        _uiState.update { it.copy(isBankDialogOpen = false) }
    }

    fun onBankNameChange(name: String) { _uiState.update { it.copy(bankName = name) } }
    fun onBankBranchChange(branch: String) { _uiState.update { it.copy(bankBranch = branch) } }
    fun onAccountHolderChange(holder: String) { _uiState.update { it.copy(accountHolderName = holder) } }
    fun onAccountNumberChange(num: String) { _uiState.update { it.copy(accountNumber = num) } }

    fun saveBankDetails() {
        val s = _uiState.value
        if (s.bankName.isBlank() || s.bankBranch.isBlank() || s.accountHolderName.isBlank() || s.accountNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all bank details.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingBank = true, errorMessage = null) }
            val res = repository.updateFarmerBankDetails(
                bankName = s.bankName,
                bankBranch = s.bankBranch,
                accountHolderName = s.accountHolderName,
                accountNumber = s.accountNumber
            )
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingBank = false,
                        isBankDialogOpen = false,
                        bankSaveSuccess = true,
                        farmerPrivate = (it.farmerPrivate ?: FarmerPrivateData(
                            cultivationAddress = "Farm Address",
                            bankName = s.bankName,
                            bankBranch = s.bankBranch,
                            accountHolderName = s.accountHolderName,
                            accountNumber = s.accountNumber
                        )).copy(
                            bankName = s.bankName,
                            bankBranch = s.bankBranch,
                            accountHolderName = s.accountHolderName,
                            accountNumber = s.accountNumber
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingBank = false,
                        errorMessage = err.toUserFriendlyMessage("Could not update bank details. Please try again.")
                    )
                }
            }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.editName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val res = repository.registerProfile(
                fullName = state.editName.trim(),
                nic = "199000000000",
                phone = "+94770000000",
                districtId = state.selectedDistrictId,
                cityId = state.selectedCityId
            )
            res.onSuccess {
                sessionManager.updateDistrictCity(state.selectedDistrictId, state.selectedCityId)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                loadProfile(state.userId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.toUserFriendlyMessage("Could not update profile. Please try again.")
                    )
                }
            }
        }
    }

    // Farmer Registration Flow from Profile
    fun openFarmerRegistrationDialog() {
        val s = _uiState.value
        val distId = if (s.selectedDistrictId > 0) s.selectedDistrictId else 1
        _uiState.update {
            it.copy(
                isFarmerDialogOpen = true,
                farmerCultDistrictId = distId,
                farmerCultCityId = s.selectedCityId,
                farmerRegisterError = null,
                farmerRegisterSuccess = false
            )
        }
        viewModelScope.launch {
            val res = repository.getCities(distId)
            res.onSuccess { cities ->
                _uiState.update {
                    it.copy(
                        farmerCultCities = cities,
                        farmerCultCityId = cities.firstOrNull()?.id ?: 1
                    )
                }
            }
        }
    }

    fun closeFarmerRegistrationDialog() {
        _uiState.update { it.copy(isFarmerDialogOpen = false, farmerRegisterError = null) }
    }

    fun onFarmerCultDistrictChange(districtId: Int) {
        _uiState.update { it.copy(farmerCultDistrictId = districtId) }
        viewModelScope.launch {
            val res = repository.getCities(districtId)
            res.onSuccess { cities ->
                _uiState.update {
                    it.copy(
                        farmerCultCities = cities,
                        farmerCultCityId = cities.firstOrNull()?.id ?: 1
                    )
                }
            }
        }
    }

    fun onFarmerCultCityChange(cityId: Int) {
        _uiState.update { it.copy(farmerCultCityId = cityId) }
    }

    fun onFarmerCultAddressChange(address: String) {
        _uiState.update { it.copy(farmerCultAddress = address, farmerRegisterError = null) }
    }

    fun onFarmerCustomCropChange(crop: String) {
        _uiState.update { it.copy(farmerCustomCrop = crop) }
    }

    fun addFarmerCrop(crop: String) {
        val trimmed = crop.trim()
        if (trimmed.length in 2..30 && !_uiState.value.farmerCrops.contains(trimmed)) {
            _uiState.update {
                it.copy(
                    farmerCrops = it.farmerCrops + trimmed,
                    farmerCustomCrop = ""
                )
            }
        }
    }

    fun removeFarmerCrop(crop: String) {
        _uiState.update {
            it.copy(farmerCrops = it.farmerCrops - crop)
        }
    }

    fun onFarmerLandSizeChange(size: String) {
        _uiState.update { it.copy(farmerLandSize = size) }
    }

    fun onFarmerLandUnitChange(unit: String) {
        _uiState.update { it.copy(farmerLandUnit = unit) }
    }

    fun onFarmerPickupLandmarkChange(landmark: String) {
        _uiState.update { it.copy(farmerPickupLandmark = landmark) }
    }

    fun submitFarmerRegistration(onSuccess: (() -> Unit)? = null) {
        val s = _uiState.value
        val addr = s.farmerCultAddress.trim()
        if (addr.isBlank()) {
            _uiState.update { it.copy(farmerRegisterError = "Please enter your cultivation or farm address.") }
            return
        }
        if (s.farmerCrops.isEmpty()) {
            _uiState.update { it.copy(farmerRegisterError = "Please select or add at least 1 crop that you harvest.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegisteringFarmer = true, farmerRegisterError = null) }
            val res = repository.registerFarmer(
                cultivationDistrictId = s.farmerCultDistrictId,
                cultivationCityId = s.farmerCultCityId,
                cultivationAddress = addr,
                mainCrops = s.farmerCrops,
                landSize = s.farmerLandSize.toDoubleOrNull(),
                landUnit = s.farmerLandUnit,
                defaultPickupLandmark = s.farmerPickupLandmark.ifBlank { null }
            )

            res.onSuccess {
                sessionManager.setIsFarmer(true)
                sessionManager.setAppMode(com.example.data.models.AppMode.SELLING)
                _uiState.update {
                    it.copy(
                        isRegisteringFarmer = false,
                        isFarmerDialogOpen = false,
                        isFarmer = true,
                        farmerRegisterSuccess = true
                    )
                }
                loadProfile(s.userId)
                onSuccess?.invoke()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRegisteringFarmer = false,
                        farmerRegisterError = err.toUserFriendlyMessage("Could not complete farmer registration. Please try again.")
                    )
                }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            sessionManager.clearSession()
            _uiState.value = ProfileUiState()
            onLoggedOut()
        }
    }
}

