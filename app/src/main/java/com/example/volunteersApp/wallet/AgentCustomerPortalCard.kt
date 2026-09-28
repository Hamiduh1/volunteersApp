package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCustomerPortalCard(
    modifier: Modifier = Modifier,
    viewModel: AgentCustomerPortalViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isWithdrawal = state.selectedFlow == AgentCustomerPortalFlowType.WITHDRAWAL

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Customer Collection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Provider-side customer approval and confirmed cash handover are required before settlement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TabRow(
                selectedTabIndex = if (isWithdrawal) 0 else 1,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = isWithdrawal,
                    onClick = { viewModel.selectFlow(AgentCustomerPortalFlowType.WITHDRAWAL) },
                    text = { Text("Collection") }
                )
                Tab(
                    selected = !isWithdrawal,
                    onClick = { viewModel.selectFlow(AgentCustomerPortalFlowType.DEPOSIT) },
                    text = { Text("Agent Deposit") }
                )
            }

            CustomerLookupSection(state = state, viewModel = viewModel)

            if (isWithdrawal) {
                WithdrawalFlowSection(state = state, viewModel = viewModel)
            } else {
                DepositFlowSection(state = state, viewModel = viewModel)
            }

            state.error?.takeIf { it.isNotBlank() }?.let { errorMessage ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            state.message?.takeIf { it.isNotBlank() }?.let { successMessage ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = successMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerLookupSection(
    state: AgentCustomerPortalUiState,
    viewModel: AgentCustomerPortalViewModel
) {
    var countryExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Customer Lookup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        ExposedDropdownMenuBox(
            expanded = countryExpanded,
            onExpandedChange = { countryExpanded = !countryExpanded }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = state.selectedCountry,
                onValueChange = {},
                label = { Text("Country") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded)
                }
            )
            DropdownMenu(
                expanded = countryExpanded,
                onDismissRequest = { countryExpanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                state.availableCountries.forEach { country ->
                    DropdownMenuItem(
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface),
                        text = { Text(country, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            countryExpanded = false
                            viewModel.selectCountry(country)
                        }
                    )
                }
            }
        }

        Text(
            "Network",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.availableNetworks) { network ->
                FilterChip(
                    selected = state.selectedNetwork.equals(network, ignoreCase = true),
                    onClick = { viewModel.selectNetwork(network) },
                    label = { Text(network) }
                )
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.localPhoneInput,
            onValueChange = viewModel::onPhoneInputChanged,
            label = { Text("Customer Phone") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            prefix = {
                val prefix = if (state.dialCode.isBlank()) "+" else state.dialCode
                Text("$prefix ")
            },
            singleLine = true
        )

        Button(
            onClick = viewModel::lookupCustomer,
            enabled = state.canLookupCustomer,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLookupRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Find Customer")
            }
        }

        state.lookupRecord?.let { lookup ->
            LookupSummaryCard(lookup)
        }
    }
}

@Composable
private fun LookupSummaryCard(lookup: AgentCustomerLookupRecord) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Customer",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                lookup.maskedCustomerLabel ?: "Masked record",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            lookup.maskedPhone?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Wallet currency: ${lookup.walletCurrency}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Approval mode: ${lookup.approvalMode.name.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall
            )
            lookup.maxWithdrawableAmount?.let { amount ->
                Text(
                    text = "Max withdrawable: ${formatMoney(amount)} ${lookup.walletCurrency}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            if (lookup.riskFlags.isNotEmpty()) {
                Text(
                    "Risk flags: ${lookup.riskFlags.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun WithdrawalFlowSection(
    state: AgentCustomerPortalUiState,
    viewModel: AgentCustomerPortalViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Divider()
        Text(
            "Customer Collection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.amountInput,
            onValueChange = viewModel::onAmountInputChanged,
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        Button(
            onClick = viewModel::startCustomerPortalWithdrawal,
            enabled = state.canStartCustomerPortalWithdrawal,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isActionRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Send OTP")
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.otpInput,
            onValueChange = viewModel::onOtpInputChanged,
            label = { Text("OTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Button(
            onClick = viewModel::verifyCustomerWithdrawalOtp,
            enabled = state.canVerifyWithdrawalOtp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verify OTP")
        }

        Button(
            onClick = viewModel::requestCustomerApproval,
            enabled = state.canRequestCustomerApproval,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Request Customer Approval")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::refreshWithdrawalSession,
                enabled = state.canRefreshSession,
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh Status")
            }
            OutlinedButton(
                onClick = viewModel::cancelWithdrawalSession,
                enabled = state.canCancelSession,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel Session")
            }
        }

        Button(
            onClick = viewModel::confirmCashHandover,
            enabled = state.canConfirmCashHandover,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm Cash Handover")
        }

        state.activeSession?.let { session ->
            SessionSummaryCard(session)
        }
    }
}

@Composable
private fun DepositFlowSection(
    state: AgentCustomerPortalUiState,
    viewModel: AgentCustomerPortalViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Divider()
        Text(
            "Agent Deposit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.amountInput,
            onValueChange = viewModel::onAmountInputChanged,
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        Button(
            onClick = viewModel::submitCustomerPortalDeposit,
            enabled = state.canSubmitCustomerPortalDeposit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isActionRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Complete Agent Deposit")
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(session: AgentCustomerWithdrawalSessionRecord) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Session Summary",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text("Session ID: ${session.sessionId}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Stage: ${session.rawStageValue ?: session.stage.name.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Status: ${session.rawStatusValue ?: session.status.ifBlank { "Unknown" }}",
                style = MaterialTheme.typography.bodySmall
            )
            session.providerReference?.let { ref ->
                Text("Provider Ref: $ref", style = MaterialTheme.typography.bodySmall)
            }
            session.expiresAtMillis?.let { expiryMs ->
                Text(
                    "Expires: ${dateFormat.format(Date(expiryMs))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (session.localPayoutAmount != null) {
                val currency = session.localPayoutCurrency ?: ""
                Text(
                    "Local payout: ${formatMoney(session.localPayoutAmount)} $currency",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            session.failureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    "Failure reason: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatMoney(value: Double): String = String.format(Locale.US, "%,.2f", value)
