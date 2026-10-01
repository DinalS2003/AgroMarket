package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.models.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agromarket_session")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRES_AT = stringPreferencesKey("token_expires_at")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_DISTRICT_ID = stringPreferencesKey("user_district_id")
        private val KEY_USER_CITY_ID = stringPreferencesKey("user_city_id")
        private val KEY_IS_FARMER = booleanPreferencesKey("is_farmer")
        private val KEY_APP_MODE = stringPreferencesKey("app_mode")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val KEY_BANK_NAME = stringPreferencesKey("farmer_bank_name")
        private val KEY_BANK_BRANCH = stringPreferencesKey("farmer_bank_branch")
        private val KEY_ACCOUNT_HOLDER = stringPreferencesKey("farmer_account_holder")
        private val KEY_ACCOUNT_NUMBER = stringPreferencesKey("farmer_account_number")
        private val KEY_CULTIVATION_ADDRESS = stringPreferencesKey("farmer_cult_address")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_PASSWORD = stringPreferencesKey("user_password")
    }

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    fun notifySessionExpired() {
        _sessionExpiredEvent.tryEmit(Unit)
    }

    val authTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_AUTH_TOKEN] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val isFarmerFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_FARMER] ?: false }
    val appModeFlow: Flow<AppMode> = context.dataStore.data.map {
        val modeStr = it[KEY_APP_MODE] ?: AppMode.BUYING.name
        try {
            AppMode.valueOf(modeStr)
        } catch (e: Exception) {
            AppMode.BUYING
        }
    }
    val userDistrictIdFlow: Flow<Int> = context.dataStore.data.map {
        it[KEY_USER_DISTRICT_ID]?.toIntOrNull() ?: 1 // Default Colombo
    }

    suspend fun saveAuthSession(
        token: String,
        userId: String,
        phone: String,
        fullName: String,
        districtId: Int,
        cityId: Int,
        isFarmer: Boolean,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null,
        email: String? = null,
        password: String? = null
    ) {
        val cleanDigits = phone.replace(Regex("[^0-9]"), "").let {
            if (it.startsWith("0")) "94" + it.substring(1) else if (it.startsWith("7")) "94$it" else it
        }
        val resolvedEmail = if (!email.isNullOrBlank()) email else "p${cleanDigits}@agromarket.lk"
        val resolvedPassword = if (!password.isNullOrBlank()) password else "AgroPass_${cleanDigits}!"

        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USER_PHONE] = phone
            prefs[KEY_USER_NAME] = fullName
            prefs[KEY_USER_DISTRICT_ID] = districtId.toString()
            prefs[KEY_USER_CITY_ID] = cityId.toString()
            prefs[KEY_IS_FARMER] = isFarmer
            prefs[KEY_USER_EMAIL] = resolvedEmail
            prefs[KEY_USER_PASSWORD] = resolvedPassword
            if (!refreshToken.isNullOrBlank()) {
                prefs[KEY_REFRESH_TOKEN] = refreshToken
            }
            // Keep session valid for long (minimum 30 days if not specified)
            val effectiveSeconds = if (expiresInSeconds != null && expiresInSeconds > 0) expiresInSeconds else 2592000L
            val expiryMillis = System.currentTimeMillis() + (effectiveSeconds * 1000)
            prefs[KEY_TOKEN_EXPIRES_AT] = expiryMillis.toString()
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String? = null, expiresInSeconds: Long? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                prefs[KEY_REFRESH_TOKEN] = refreshToken
            }
            if (expiresInSeconds != null && expiresInSeconds > 0) {
                val expiryMillis = System.currentTimeMillis() + (expiresInSeconds * 1000)
                prefs[KEY_TOKEN_EXPIRES_AT] = expiryMillis.toString()
            }
        }
    }

    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.first()[KEY_REFRESH_TOKEN]
    }

    suspend fun getTokenExpiresAt(): Long? {
        return context.dataStore.data.first()[KEY_TOKEN_EXPIRES_AT]?.toLongOrNull()
    }

    suspend fun setAppMode(mode: AppMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_MODE] = mode.name
        }
    }

    suspend fun setIsFarmer(isFarmer: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_FARMER] = isFarmer
        }
    }

    suspend fun updateDistrictCity(districtId: Int, cityId: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_DISTRICT_ID] = districtId.toString()
            prefs[KEY_USER_CITY_ID] = cityId.toString()
        }
    }

    suspend fun saveDeviceToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_TOKEN] = token
        }
    }

    suspend fun getAuthToken(): String? {
        return context.dataStore.data.first()[KEY_AUTH_TOKEN]
    }

    suspend fun getUserId(): String? {
        return context.dataStore.data.first()[KEY_USER_ID]
    }

    suspend fun getUserName(): String? {
        return context.dataStore.data.first()[KEY_USER_NAME]
    }

    suspend fun getUserPhone(): String? {
        return context.dataStore.data.first()[KEY_USER_PHONE]
    }

    suspend fun getUserDistrictId(): Int {
        return context.dataStore.data.first()[KEY_USER_DISTRICT_ID]?.toIntOrNull() ?: 1
    }

    suspend fun getUserCityId(): Int {
        return context.dataStore.data.first()[KEY_USER_CITY_ID]?.toIntOrNull() ?: 1
    }

    suspend fun isFarmer(): Boolean {
        return context.dataStore.data.first()[KEY_IS_FARMER] ?: false
    }

    suspend fun saveFarmerBankDetails(bankName: String, bankBranch: String, accountHolderName: String, accountNumber: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BANK_NAME] = bankName
            prefs[KEY_BANK_BRANCH] = bankBranch
            prefs[KEY_ACCOUNT_HOLDER] = accountHolderName
            prefs[KEY_ACCOUNT_NUMBER] = accountNumber
        }
    }

    suspend fun saveCultivationAddress(address: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CULTIVATION_ADDRESS] = address
        }
    }

    suspend fun getFarmerBankName(): String = context.dataStore.data.first()[KEY_BANK_NAME] ?: ""
    suspend fun getFarmerBankBranch(): String = context.dataStore.data.first()[KEY_BANK_BRANCH] ?: ""
    suspend fun getFarmerAccountHolder(): String = context.dataStore.data.first()[KEY_ACCOUNT_HOLDER] ?: ""
    suspend fun getFarmerAccountNumber(): String = context.dataStore.data.first()[KEY_ACCOUNT_NUMBER] ?: ""
    suspend fun getCultivationAddress(): String = context.dataStore.data.first()[KEY_CULTIVATION_ADDRESS] ?: ""
    suspend fun getUserEmail(): String? = context.dataStore.data.first()[KEY_USER_EMAIL]
    suspend fun getUserPassword(): String? = context.dataStore.data.first()[KEY_USER_PASSWORD]

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRES_AT)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_PHONE)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_PASSWORD)
            prefs.remove(KEY_IS_FARMER)
            prefs[KEY_APP_MODE] = AppMode.BUYING.name
        }
        _sessionExpiredEvent.tryEmit(Unit)
    }
}
