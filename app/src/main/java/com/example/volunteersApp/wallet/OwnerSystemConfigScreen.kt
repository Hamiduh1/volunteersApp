package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class OwnerSystemConfigUiState(
    val maintenanceMode: Boolean = false,
    val allowNewSignups: Boolean = true,
    val enableBlindDate: Boolean = true,
    val enableLiveStreams: Boolean = true,
    val maxUploadMb: String = "10",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

private class OwnerSystemConfigViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow(OwnerSystemConfigUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val snapshot = db.collection("app_config").document("system_config").get().await()
                val data = snapshot.data ?: emptyMap<String, Any>()
                _uiState.update {
                    it.copy(
                        maintenanceMode = data["maintenanceMode"] as? Boolean ?: false,
                        allowNewSignups = data["allowNewSignups"] as? Boolean ?: true,
                        enableBlindDate = data["enableBlindDate"] as? Boolean ?: true,
                        enableLiveStreams = data["enableLiveStreams"] as? Boolean ?: true,
                        maxUploadMb = ((data["maxUploadMb"] as? Number)?.toInt() ?: 10).toString(),
                        isLoading = false,
                        statusMessage = "System config loaded."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load system config.") }
            }
        }
    }

    fun onMaintenanceModeChange(value: Boolean) = _uiState.update { it.copy(maintenanceMode = value) }
    fun onAllowNewSignupsChange(value: Boolean) = _uiState.update { it.copy(allowNewSignups = value) }
    fun onEnableBlindDateChange(value: Boolean) = _uiState.update { it.copy(enableBlindDate = value) }
    fun onEnableLiveStreamsChange(value: Boolean) = _uiState.update { it.copy(enableLiveStreams = value) }
    fun onMaxUploadMbChange(value: String) = _uiState.update { it.copy(maxUploadMb = value) }

    fun applyDefaults() {
        _uiState.update {
            it.copy(
                maintenanceMode = false,
                allowNewSignups = true,
                enableBlindDate = true,
                enableLiveStreams = true,
                maxUploadMb = "10"
            )
        }
    }

    fun save() {
        val maxUploadMb = _uiState.value.maxUploadMb.toIntOrNull()
        if (maxUploadMb == null || maxUploadMb <= 0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid max upload size.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, statusMessage = null) }
            try {
                val response = FunctionsClient.callMap(
                    "ownerSaveSystemConfig",
                    mapOf(
                        "maintenanceMode" to _uiState.value.maintenanceMode,
                        "allowNewSignups" to _uiState.value.allowNewSignups,
                        "enableBlindDate" to _uiState.value.enableBlindDate,
                        "enableLiveStreams" to _uiState.value.enableLiveStreams,
                        "maxUploadMb" to maxUploadMb
                    )
                )
                val message = ((response?.get("data") as? Map<*, *>)?.string("message"))
                    ?: response?.string("message")
                    ?: "System config saved."
                _uiState.update { it.copy(isSaving = false, statusMessage = message) }
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to save system config."
                _uiState.update { it.copy(isSaving = false, errorMessage = message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSystemConfigScreen(
    onBack: () -> Unit
) {
    val viewModel: OwnerSystemConfigViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Config", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            uiState.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToggleRow("Maintenance Mode", uiState.maintenanceMode, viewModel::onMaintenanceModeChange)
                    ToggleRow("Allow New Signups", uiState.allowNewSignups, viewModel::onAllowNewSignupsChange)
                    ToggleRow("Enable Blind Date", uiState.enableBlindDate, viewModel::onEnableBlindDateChange)
                    ToggleRow("Enable Live Streams", uiState.enableLiveStreams, viewModel::onEnableLiveStreamsChange)
                    OutlinedTextField(
                        value = uiState.maxUploadMb,
                        onValueChange = viewModel::onMaxUploadMbChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Max Upload (MB)") }
                    )
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving && !uiState.isLoading
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Save System Config")
                }
            }

            Button(
                onClick = viewModel::applyDefaults,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                Text("Apply Recommended Defaults")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
