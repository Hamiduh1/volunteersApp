package com.example.volunteersApp.general

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data model representing a single Privacy/Security setting item.
 */
data class PrivacySettingItem(
    val title: String,
    val description: String,
    val isEnabled: Boolean = false,
    val type: SettingType
)

enum class SettingType {
    LOCATION_TRACKING,
    DATA_SHARING,
    BIOMETRIC_LOCK,
    TWO_FACTOR_AUTH
}

/**
 * UI State for the Privacy & Security screen.
 */
data class PrivacySecurityUiState(
    val settings: List<PrivacySettingItem> = emptyList(),
    val isLoading: Boolean = false
)

class PrivacySecurityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySecurityUiState())
    val uiState: StateFlow<PrivacySecurityUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // Initial state with standard privacy settings
        _uiState.value = PrivacySecurityUiState(
            settings = listOf(
                PrivacySettingItem(
                    "Location Services",
                    "Allow the app to access your location for local event discovery.",
                    isEnabled = true,
                    type = SettingType.LOCATION_TRACKING
                ),
                PrivacySettingItem(
                    "Data Analytics",
                    "Share anonymous usage data to help us improve the experience.",
                    isEnabled = false,
                    type = SettingType.DATA_SHARING
                ),
                PrivacySettingItem(
                    "Biometric Authentication",
                    "Use fingerprint or face ID to unlock the application.",
                    isEnabled = true,
                    type = SettingType.BIOMETRIC_LOCK
                ),
                PrivacySettingItem(
                    "Two-Factor Authentication",
                    "Add an extra layer of security to your volunteer account.",
                    isEnabled = false,
                    type = SettingType.TWO_FACTOR_AUTH
                )
            )
        )
    }

    fun toggleSetting(type: SettingType) {
        val currentSettings = _uiState.value.settings.toMutableList()
        val index = currentSettings.indexOfFirst { it.type == type }
        if (index != -1) {
            val item = currentSettings[index]
            currentSettings[index] = item.copy(isEnabled = !item.isEnabled)
            _uiState.value = _uiState.value.copy(settings = currentSettings)
        }
    }
}
