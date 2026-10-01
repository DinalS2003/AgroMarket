package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.City
import com.example.data.models.District
import com.example.data.repository.AgroMarketRepository
import com.example.ui.components.toUserFriendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val phoneInput: String = "",
    val phoneError: String? = null,
    val isOtpSent: Boolean = false,
    val otpCode: String = "",
    val otpError: String? = null,
    val resendCountdown: Int = 0,
    val isLoading: Boolean = false,
    val isSessionReady: Boolean = false,
    val needsOnboarding: Boolean = false,
    val isSuspended: Boolean = false,
    val suspendedReason: String? = null,
    val otpReferenceId: String? = null,
    val otpStatusMessage: String? = null
)

data class OnboardingUiState(
    val currentStep: Int = 1, // 1 = Personal Info, 2 = Farm & Cultivation
    val fullName: String = "",
    val nic: String = "",
    val nicError: String? = null,
    val selectedDistrictId: Int = 1,
    val selectedCityId: Int = 1,
    val districts: List<District> = emptyList(),
    val cities: List<City> = emptyList(),
    val isLoadingCities: Boolean = false,
    // Step 2: Farmer registration
    val isFarmerMode: Boolean = false,
    val cultivationDistrictId: Int = 1,
    val cultivationCityId: Int = 1,
    val cultivationCities: List<City> = emptyList(),
    val cultivationAddress: String = "",
    val mainCrops: List<String> = emptyList(),
    val customCropInput: String = "",
    val landSize: String = "",
    val landUnit: String = "acres",
    val defaultPickupLandmark: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false
)

class AuthViewModel(
    private val repository: AgroMarketRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _onboardingState = MutableStateFlow(OnboardingUiState())
    val onboardingState: StateFlow<OnboardingUiState> = _onboardingState.asStateFlow()

    private var countdownJob: Job? = null

    val commonCropSuggestions = listOf(
        "Tomato", "Carrot", "Beans", "Cabbage", "Leeks", "Brinjal", "Pumpkin", "Onion",
        "Red Onion", "Big Onion", "Green Chilli", "Potato", "Beetroot", "Banana", "Papaya"
    )

    init {
        loadDistrictsAndCities()
    }

    fun resetAuth() {
        countdownJob?.cancel()
        _authState.value = AuthUiState()
        _onboardingState.value = OnboardingUiState()
        loadDistrictsAndCities()
    }

    fun initFarmerOnboarding() {
        _onboardingState.update {
            it.copy(
                currentStep = 2,
                isFarmerMode = true,
                isComplete = false,
                errorMessage = null,
                cultivationDistrictId = if (it.cultivationDistrictId == 1 && it.selectedDistrictId != 1) it.selectedDistrictId else it.cultivationDistrictId
            )
        }
        if (_onboardingState.value.cultivationCities.isEmpty()) {
            onCultivationDistrictSelected(_onboardingState.value.cultivationDistrictId)
        }
    }

    private fun loadDistrictsAndCities() {
        viewModelScope.launch {
            val distRes = repository.getDistricts()
            distRes.onSuccess { list ->
                _onboardingState.update { it.copy(districts = list) }
                if (list.isNotEmpty()) {
                    loadCitiesForDistrict(list.first().id)
                }
            }
        }
    }

    fun onDistrictSelected(districtId: Int) {
        _onboardingState.update { it.copy(selectedDistrictId = districtId) }
        loadCitiesForDistrict(districtId)
    }

    fun onCultivationDistrictSelected(districtId: Int) {
        _onboardingState.update { it.copy(cultivationDistrictId = districtId) }
        viewModelScope.launch {
            val res = repository.getCities(districtId)
            res.onSuccess { cities ->
                _onboardingState.update {
                    it.copy(
                        cultivationCities = cities,
                        cultivationCityId = cities.firstOrNull()?.id ?: 1
                    )
                }
            }
        }
    }

    private fun loadCitiesForDistrict(districtId: Int) {
        viewModelScope.launch {
            _onboardingState.update { it.copy(isLoadingCities = true) }
            val res = repository.getCities(districtId)
            res.onSuccess { cities ->
                _onboardingState.update {
                    it.copy(
                        cities = cities,
                        selectedCityId = cities.firstOrNull()?.id ?: 1,
                        isLoadingCities = false
                    )
                }
            }.onFailure {
                _onboardingState.update { it.copy(isLoadingCities = false) }
            }
        }
    }

    fun onPhoneChange(phone: String) {
        _authState.update { it.copy(phoneInput = phone, phoneError = null) }
    }

    fun resetToPhoneInput() {
        countdownJob?.cancel()
        _authState.update {
            it.copy(
                isOtpSent = false,
                otpCode = "",
                otpError = null,
                phoneError = null,
                resendCountdown = 0,
                otpReferenceId = null,
                otpStatusMessage = null
            )
        }
    }

    fun onOtpChange(code: String) {
        if (code.length <= 6) {
            _authState.update { it.copy(otpCode = code, otpError = null) }
        }
    }

    fun requestOtp() {
        val phone = _authState.value.phoneInput.trim()
        val normalized = repository.normalizeSriLankanPhone(phone)
        if (!normalized.matches(Regex("^\\+947[0-9]{8}$"))) {
            _authState.update { it.copy(phoneError = "Please enter a valid Sri Lankan mobile (e.g. 0771234567)") }
            return
        }

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, phoneError = null) }
            val result = repository.sendOtpWithTextLk(normalized)
            if (result.success) {
                _authState.update {
                    it.copy(
                        isLoading = false,
                        isOtpSent = true,
                        otpCode = "",
                        phoneError = null,
                        otpError = null,
                        resendCountdown = 60,
                        otpReferenceId = result.referenceId,
                        otpStatusMessage = result.displayMessage
                    )
                }
                startCountdown()
            } else {
                // If rejected (due to sender-mask approval, credentials, format, etc.), report real error and do not mark as sent
                _authState.update {
                    it.copy(
                        isLoading = false,
                        isOtpSent = false,
                        otpCode = "",
                        phoneError = result.errorMessage ?: result.displayMessage,
                        otpError = null,
                        resendCountdown = 0,
                        otpReferenceId = null,
                        otpStatusMessage = null
                    )
                }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_authState.value.resendCountdown > 0) {
                delay(1000)
                _authState.update { it.copy(resendCountdown = it.resendCountdown - 1) }
            }
        }
    }

    fun verifyOtp() {
        val code = _authState.value.otpCode.trim()
        if (code.length < 6) {
            _authState.update { it.copy(otpError = "Please enter the 6-digit code") }
            return
        }

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, otpError = null) }
            val phone = repository.normalizeSriLankanPhone(_authState.value.phoneInput)
            val refId = _authState.value.otpReferenceId
            val res = repository.verifyOtp(phone, code, refId)
            res.onSuccess { data ->
                val userObj = data["user"] as? Map<*, *>
                val userId = userObj?.get("id") as? String ?: ""

                // Verify profile
                val profileRes = repository.getProfile(userId)
                val profile = profileRes.getOrNull()

                if (profile != null && profile.isSuspended) {
                    _authState.update {
                        it.copy(
                            isLoading = false,
                            isSuspended = true,
                            suspendedReason = profile.suspendedReason
                        )
                    }
                } else if (profile == null || profile.fullName.isBlank()) {
                    _authState.update {
                        it.copy(isLoading = false, needsOnboarding = true)
                    }
                } else {
                    _authState.update {
                        it.copy(isLoading = false, isSessionReady = true)
                    }
                }
            }.onFailure { err ->
                _authState.update {
                    it.copy(isLoading = false, otpError = err.message ?: "Invalid code. Please check and retry.")
                }
            }
        }
    }

    // Onboarding handlers
    fun onFullNameChange(name: String) {
        _onboardingState.update { it.copy(fullName = name, errorMessage = null) }
    }

    fun onNicChange(nic: String) {
        _onboardingState.update { it.copy(nic = nic.uppercase(), nicError = null, errorMessage = null) }
    }

    fun onCitySelected(cityId: Int) {
        _onboardingState.update { it.copy(selectedCityId = cityId) }
    }

    fun onCultivationCitySelected(cityId: Int) {
        _onboardingState.update { it.copy(cultivationCityId = cityId) }
    }

    fun onCultivationAddressChange(addr: String) {
        _onboardingState.update { it.copy(cultivationAddress = addr) }
    }

    fun toggleFarmerMode(enabled: Boolean) {
        _onboardingState.update { it.copy(isFarmerMode = enabled, errorMessage = null) }
        if (enabled && _onboardingState.value.cultivationCities.isEmpty()) {
            onCultivationDistrictSelected(_onboardingState.value.selectedDistrictId)
        }
    }

    fun setAccountType(isFarmer: Boolean) {
        _onboardingState.update { it.copy(isFarmerMode = isFarmer, errorMessage = null) }
        if (isFarmer && _onboardingState.value.cultivationCities.isEmpty()) {
            onCultivationDistrictSelected(_onboardingState.value.selectedDistrictId)
        }
    }

    fun goToFarmerStep(): Boolean {
        val state = _onboardingState.value
        val name = state.fullName.trim()
        val nic = state.nic.trim().uppercase()

        if (name.length < 2) {
            _onboardingState.update { it.copy(errorMessage = "Please enter your full official name.") }
            return false
        }

        val isValidNic = nic.matches(Regex("^[0-9]{9}[VX]$")) || nic.matches(Regex("^[0-9]{12}$"))
        if (!isValidNic) {
            _onboardingState.update {
                it.copy(
                    nicError = "Invalid NIC format. Must be 9 digits + V/X or 12 digits.",
                    errorMessage = "Please enter a valid National Identity Card (NIC) number."
                )
            }
            return false
        }

        _onboardingState.update {
            it.copy(
                isFarmerMode = true,
                currentStep = 2,
                errorMessage = null,
                nicError = null,
                cultivationDistrictId = if (it.cultivationDistrictId == 1 && it.selectedDistrictId != 1) it.selectedDistrictId else it.cultivationDistrictId
            )
        }
        if (_onboardingState.value.cultivationCities.isEmpty()) {
            onCultivationDistrictSelected(_onboardingState.value.cultivationDistrictId)
        }
        return true
    }

    fun goBackToStepOne() {
        _onboardingState.update { it.copy(currentStep = 1, errorMessage = null) }
    }

    fun addCropChip(crop: String) {
        val trimmed = crop.trim()
        if (trimmed.length in 2..30 && !_onboardingState.value.mainCrops.contains(trimmed) && _onboardingState.value.mainCrops.size < 15) {
            _onboardingState.update {
                it.copy(
                    mainCrops = it.mainCrops + trimmed,
                    customCropInput = ""
                )
            }
        }
    }

    fun removeCropChip(crop: String) {
        _onboardingState.update {
            it.copy(mainCrops = it.mainCrops - crop)
        }
    }

    fun onCustomCropInputChange(input: String) {
        _onboardingState.update { it.copy(customCropInput = input) }
    }

    fun onLandSizeChange(size: String) {
        _onboardingState.update { it.copy(landSize = size) }
    }

    fun onLandUnitChange(unit: String) {
        _onboardingState.update { it.copy(landUnit = unit) }
    }

    fun onDefaultPickupLandmarkChange(landmark: String) {
        _onboardingState.update { it.copy(defaultPickupLandmark = landmark) }
    }

    fun submitOnboarding() {
        val state = _onboardingState.value
        val name = state.fullName.trim()
        val nic = state.nic.trim().uppercase()

        if (name.length < 2) {
            _onboardingState.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }

        // Validate NIC: 9 digits + V/X or 12 digits
        val isValidNic = nic.matches(Regex("^[0-9]{9}[VX]$")) || nic.matches(Regex("^[0-9]{12}$"))
        if (!isValidNic) {
            _onboardingState.update {
                it.copy(
                    nicError = "Invalid NIC format. Must be 9 digits + V/X or 12 digits.",
                    errorMessage = "Please provide a valid National Identity Card number."
                )
            }
            return
        }

        if (state.isFarmerMode) {
            if (state.cultivationAddress.trim().isBlank()) {
                _onboardingState.update { it.copy(errorMessage = "Please enter your cultivation / farm address.") }
                return
            }
            if (state.mainCrops.isEmpty()) {
                _onboardingState.update { it.copy(errorMessage = "Please select at least 1 crop that you grow.") }
                return
            }
            // Bank details are optional at registration and can be added later under Profile!
        }

        viewModelScope.launch {
            _onboardingState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val phone = repository.normalizeSriLankanPhone(_authState.value.phoneInput)

            // Step 1: Register profile
            val profRes = repository.registerProfile(
                fullName = name,
                nic = nic,
                phone = phone,
                districtId = state.selectedDistrictId,
                cityId = state.selectedCityId
            )

            if (profRes.isFailure) {
                _onboardingState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = profRes.exceptionOrNull().toUserFriendlyMessage("Could not save your profile details. Please try again.")
                    )
                }
                return@launch
            }

            // Step 2: Farmer registration if selected
            if (state.isFarmerMode) {
                val farmRes = repository.registerFarmer(
                    cultivationDistrictId = state.cultivationDistrictId,
                    cultivationCityId = state.cultivationCityId,
                    cultivationAddress = state.cultivationAddress,
                    mainCrops = state.mainCrops,
                    landSize = state.landSize.toDoubleOrNull(),
                    landUnit = state.landUnit,
                    defaultPickupLandmark = state.defaultPickupLandmark.ifBlank { null }
                )

                if (farmRes.isFailure) {
                    _onboardingState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = farmRes.exceptionOrNull().toUserFriendlyMessage("Could not complete farmer registration. Please try again.")
                        )
                    }
                    return@launch
                }
            }

            _onboardingState.update { it.copy(isSubmitting = false, isComplete = true) }
            _authState.update { it.copy(isSessionReady = true, needsOnboarding = false) }
        }
    }
}
