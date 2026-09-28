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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

private data class OwnerFeeSettingsUiState(
    val blindDateFeeUsd: String = "10.0",
    val agentAuthorizationFeeUsd: String = "1.0",
    val forexProfitMargin: String = "0.010",
    val stripeForexDepositProfitMargin: String = "0.005",
    val mobileMoneyHiddenFeeRate: String = "0.0",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

private class OwnerFeeSettingsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow(OwnerFeeSettingsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val snapshot = db.collection("app_config").document("fee_settings").get().await()
                val data = snapshot.data ?: emptyMap<String, Any>()
                _uiState.update {
                    it.copy(
                        blindDateFeeUsd = data.number("blindDateFeeUsd", 10.0).toString(),
                        agentAuthorizationFeeUsd = data.number("agentAuthorizationFeeUsd", 1.0).toString(),
                        forexProfitMargin = data.number("forexProfitMargin", 0.010).toString(),
                        stripeForexDepositProfitMargin = data.number("stripeForexDepositProfitMargin", 0.005).toString(),
                        mobileMoneyHiddenFeeRate = data.number("mobileMoneyHiddenFeeRate", 0.0).toString(),
                        isLoading = false,
                        statusMessage = "Fee settings loaded."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load fee settings.") }
            }
        }
    }

    fun onBlindDateFeeChange(value: String) = _uiState.update { it.copy(blindDateFeeUsd = value) }
    fun onAgentAuthorizationFeeChange(value: String) = _uiState.update { it.copy(agentAuthorizationFeeUsd = value) }
    fun onForexMarginChange(value: String) = _uiState.update { it.copy(forexProfitMargin = value) }
    fun onStripeForexMarginChange(value: String) = _uiState.update { it.copy(stripeForexDepositProfitMargin = value) }
    fun onMobileMoneyFeeChange(value: String) = _uiState.update { it.copy(mobileMoneyHiddenFeeRate = value) }

    fun applyDefaults() {
        _uiState.update {
            it.copy(
                blindDateFeeUsd = "10.0",
                agentAuthorizationFeeUsd = "1.0",
                forexProfitMargin = "0.010",
                stripeForexDepositProfitMargin = "0.005",
                mobileMoneyHiddenFeeRate = "0.0"
            )
        }
    }

    fun save() {
        val state = _uiState.value
        val payload = mapOf(
            "blindDateFeeUsd" to state.blindDateFeeUsd.toDoubleOrNull(),
            "agentAuthorizationFeeUsd" to state.agentAuthorizationFeeUsd.toDoubleOrNull(),
            "forexProfitMargin" to state.forexProfitMargin.toDoubleOrNull(),
            "stripeForexDepositProfitMargin" to state.stripeForexDepositProfitMargin.toDoubleOrNull(),
            "mobileMoneyHiddenFeeRate" to state.mobileMoneyHiddenFeeRate.toDoubleOrNull()
        )
        if (payload.values.any { it == null }) {
            _uiState.update { it.copy(errorMessage = "Enter valid numbers for all fee settings.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, statusMessage = null) }
            try {
                val response = FunctionsClient.callMap(
                    "ownerSaveFeeSettings",
                    payload.mapValues { it.value ?: 0.0 }
                )
                val message = ((response?.get("data") as? Map<*, *>)?.string("message"))
                    ?: response?.string("message")
                    ?: "Fee settings saved."
                _uiState.update { it.copy(isSaving = false, statusMessage = message) }
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to save fee settings."
                _uiState.update { it.copy(isSaving = false, errorMessage = message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerFeeSettingsScreen(
    onBack: () -> Unit
) {
    val viewModel: OwnerFeeSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fee Settings", fontWeight = FontWeight.Bold) },
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
                    Text("Fees", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.blindDateFeeUsd,
                        onValueChange = viewModel::onBlindDateFeeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Blind Date Fee (USD)") }
                    )
                    OutlinedTextField(
                        value = uiState.agentAuthorizationFeeUsd,
                        onValueChange = viewModel::onAgentAuthorizationFeeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Agent Authorization Fee (USD)") }
                    )
                    OutlinedTextField(
                        value = uiState.forexProfitMargin,
                        onValueChange = viewModel::onForexMarginChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Forex Profit Margin (0-1)") }
                    )
                    OutlinedTextField(
                        value = uiState.stripeForexDepositProfitMargin,
                        onValueChange = viewModel::onStripeForexMarginChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Stripe Forex Deposit Margin (0-1)") }
                    )
                    OutlinedTextField(
                        value = uiState.mobileMoneyHiddenFeeRate,
                        onValueChange = viewModel::onMobileMoneyFeeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mobile Money Hidden Fee Rate (0-1)") }
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
                    Text("Save Fee Settings")
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

private fun Map<String, Any>.number(key: String, fallback: Double): Double =
    (this[key] as? Number)?.toDouble() ?: fallback

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
