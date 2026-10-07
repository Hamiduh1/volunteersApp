package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun isCallableMissing(error: Exception): Boolean {
    val code = (error as? FirebaseFunctionsException)?.code
    return code == FirebaseFunctionsException.Code.NOT_FOUND ||
        code == FirebaseFunctionsException.Code.UNIMPLEMENTED ||
        error.message?.contains("NOT_FOUND", ignoreCase = true) == true
}

data class OwnerUserReportItem(
    val id: String,
    val sourceCollection: String,
    val reportedUserName: String,
    val reportedUserEmail: String?,
    val eventName: String?,
    val reason: String,
    val reportingUserDisplayName: String?,
    val reportingUserId: String?,
    val timestamp: Timestamp?,
    /** iOS volunteer issue reports use [createdAt] instead of [timestamp]. */
    val createdAt: Timestamp? = null
)

enum class OwnerUserReportsFilter(val title: String, val collection: String?) {
    ALL("All", null),
    USER_REPORTS("user_reports", FirestoreCollection.USER_REPORTS),
    USER_REPORTS_LEGACY("userReports", FirestoreCollection.USER_REPORTS_LEGACY)
}

data class OwnerUserReportsUiState(
    val isLoading: Boolean = true,
    val reports: List<OwnerUserReportItem> = emptyList(),
    val query: String = "",
    val activeFilter: OwnerUserReportsFilter = OwnerUserReportsFilter.ALL,
    val error: String? = null
) {
    val filteredReports: List<OwnerUserReportItem>
        get() {
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            return reports.filter { report ->
                val matchesFilter = activeFilter.collection == null ||
                    report.sourceCollection == activeFilter.collection
                val matchesQuery = cleanQuery.isEmpty() || listOfNotNull(
                    report.reportedUserName,
                    report.reason,
                    report.reportedUserEmail,
                    report.eventName,
                    report.reportingUserDisplayName
                ).any { it.lowercase(Locale.getDefault()).contains(cleanQuery) }
                matchesFilter && matchesQuery
            }
        }
}

class OwnerUserReportsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val reportListeners = mutableMapOf<String, ListenerRegistration>()
    private val collectionReports = mutableMapOf<String, List<OwnerUserReportItem>>()

    private val _uiState = MutableStateFlow(OwnerUserReportsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenToCollection(FirestoreCollection.USER_REPORTS)
        listenToCollection(FirestoreCollection.USER_REPORTS_LEGACY)
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onFilterChange(filter: OwnerUserReportsFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    private fun reportQueries(collectionName: String): List<Query> =
        when (collectionName) {
            FirestoreCollection.USER_REPORTS -> listOf(
                db.collection(collectionName).orderBy("createdAt", Query.Direction.DESCENDING).limit(200),
                db.collection(collectionName).orderBy("timestamp", Query.Direction.DESCENDING).limit(200),
                db.collection(collectionName).limit(200)
            )
            else -> listOf(
                db.collection(collectionName).orderBy("timestamp", Query.Direction.DESCENDING).limit(200),
                db.collection(collectionName).orderBy("createdAt", Query.Direction.DESCENDING).limit(200),
                db.collection(collectionName).limit(200)
            )
        }

    private fun listenToCollection(collectionName: String) {
        attachReportListener(collectionName = collectionName, queryIndex = 0)
    }

    private fun attachReportListener(collectionName: String, queryIndex: Int) {
        val queries = reportQueries(collectionName)
        if (queryIndex > queries.lastIndex) {
            _uiState.update {
                it.copy(isLoading = false, error = it.error ?: "Failed to load $collectionName (no working query).")
            }
            return
        }
        reportListeners[collectionName]?.remove()
        val registration = queries[queryIndex].addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (queryIndex < queries.lastIndex) {
                    attachReportListener(collectionName = collectionName, queryIndex = queryIndex + 1)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load reports.") }
                }
                return@addSnapshotListener
            }

            val parsed = snapshot?.documents?.map { parseOwnerReportDoc(it, collectionName) } ?: emptyList()

            collectionReports[collectionName] = parsed
            val merged = collectionReports.values
                .flatten()
                .distinctBy { "${it.sourceCollection}:${it.id}" }
                .sortedByDescending { item ->
                    item.timestamp?.toDate() ?: item.createdAt?.toDate() ?: Date(0)
                }

            _uiState.update { it.copy(isLoading = false, reports = merged, error = null) }
        }
        reportListeners[collectionName] = registration
    }

    private fun parseOwnerReportDoc(doc: DocumentSnapshot, collectionName: String): OwnerUserReportItem {
        val titleOrName = sequenceOf(
            doc.getString("reportedUserName"),
            doc.getString("reportedName"),
            doc.getString("title")
        ).firstOrNull { !it.isNullOrBlank() }
        val reasonText = sequenceOf(
            doc.getString("reasonForReport"),
            doc.getString("reason"),
            doc.getString("description")
        ).firstOrNull { !it.isNullOrBlank() }
        return OwnerUserReportItem(
            id = doc.id,
            sourceCollection = collectionName,
            reportedUserName = titleOrName ?: "Unknown",
            reportedUserEmail = doc.getString("reportedUserEmail") ?: doc.getString("reporterEmail"),
            eventName = doc.getString("eventName") ?: doc.getString("category"),
            reason = reasonText ?: "No reason provided",
            reportingUserDisplayName = doc.getString("reportingUserDisplayName"),
            reportingUserId = doc.getString("reportingUserId") ?: doc.getString("reportedByUid"),
            timestamp = doc.getTimestamp("timestamp"),
            createdAt = doc.getTimestamp("createdAt")
        )
    }

    override fun onCleared() {
        reportListeners.values.forEach { it.remove() }
        reportListeners.clear()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerUserReportsScreen(
    onBack: () -> Unit,
    viewModel: OwnerUserReportsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConfigurableRoleResponsibilitiesSection(
                screenKey = RoleResponsibilitiesScreenKey.USER_REPORTS,
                fallbackGuide = RoleResponsibilityGuide(
                    roleTitle = "Reports Review Associate",
                    mission = "Review user complaints and convert reports into safe moderation actions.",
                    responsibilities = listOf(
                        "Validate report reason and context before action.",
                        "Identify repeat offenders and escalate severe abuse quickly.",
                        "Keep reviewer notes objective and policy-aligned.",
                        "Coordinate with support for user-facing follow-up."
                    ),
                    escalationRule = "Escalate violence, exploitation, or legal-risk reports immediately."
                )
            )

            uiState.error?.let {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search reports") }
            )
            OwnerFilterButtonRow(
                options = OwnerUserReportsFilter.values().toList(),
                selected = uiState.activeFilter,
                label = { it.title },
                onSelect = viewModel::onFilterChange
            )

            if (uiState.isLoading && uiState.reports.isEmpty()) {
                OwnerCenteredLoading()
            } else if (uiState.reports.isEmpty()) {
                Text("No user reports yet.")
            } else if (uiState.filteredReports.isEmpty()) {
                Text("No reports match your search.")
            } else {
                Text(
                    "Showing ${uiState.filteredReports.size} of ${uiState.reports.size} report(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.filteredReports, key = { "${it.sourceCollection}:${it.id}" }) { item ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(item.reportedUserName, fontWeight = FontWeight.SemiBold)
                                Text("Reason: ${item.reason}", style = MaterialTheme.typography.bodyMedium)
                                Text("Context: ${item.eventName ?: "-"}", style = MaterialTheme.typography.bodySmall)
                                Text("Reported by: ${item.reportingUserDisplayName ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Source: ${item.sourceCollection}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                item.reportedUserEmail?.takeIf { it.isNotBlank() }?.let {
                                    Text("Email: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Submitted: ${formatDate(item.timestamp ?: item.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class OwnerKycItem(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val emailVerified: Boolean,
    val profileStatus: String,
    val updatedAt: Timestamp?
)

enum class OwnerKycRoleFilter(val title: String) {
    ALL("All"),
    OWNER("Owner"),
    ADMIN("Admin"),
    ORGANIZER("Organizer"),
    EMPLOYER("Employer"),
    VOLUNTEER("Volunteer")
}

data class OwnerKycUiState(
    val isLoading: Boolean = true,
    val items: List<OwnerKycItem> = emptyList(),
    val query: String = "",
    val roleFilter: OwnerKycRoleFilter = OwnerKycRoleFilter.ALL,
    val unverifiedOnly: Boolean = false,
    val error: String? = null
) {
    val filteredItems: List<OwnerKycItem>
        get() {
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            return items.filter { item ->
                val matchesRole = roleFilter == OwnerKycRoleFilter.ALL ||
                    item.role.trim().equals(roleFilter.title, ignoreCase = true)
                val matchesVerified = !unverifiedOnly || !item.emailVerified
                val matchesQuery = cleanQuery.isEmpty() ||
                    listOf(item.name, item.email, item.role)
                        .any { it.lowercase(Locale.getDefault()).contains(cleanQuery) }
                matchesRole && matchesVerified && matchesQuery
            }
        }
}

class OwnerKycReviewViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow(OwnerKycUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onRoleChange(value: OwnerKycRoleFilter) {
        _uiState.update { it.copy(roleFilter = value) }
    }

    fun onUnverifiedOnlyChange(value: Boolean) {
        _uiState.update { it.copy(unverifiedOnly = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = db.collection(FirestoreCollection.USERS).limit(250).get().await()
                val items = snapshot.documents
                    .map { doc ->
                        OwnerKycItem(
                            uid = doc.id,
                            name = doc.getString("name") ?: doc.getString("username") ?: "Unknown",
                            email = doc.getString("email") ?: "",
                            role = doc.getString("role") ?: "volunteer",
                            emailVerified = doc.getBoolean("emailVerified") == true,
                            profileStatus = doc.getString("profileStatus") ?: "unknown",
                            updatedAt = doc.getTimestamp("updatedAt") ?: doc.getTimestamp("createdAt")
                        )
                    }
                    .sortedByDescending { it.updatedAt?.toDate() ?: Date(0) }

                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load KYC records.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerKycReviewScreen(
    onBack: () -> Unit,
    viewModel: OwnerKycReviewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KYC Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConfigurableRoleResponsibilitiesSection(
                screenKey = RoleResponsibilitiesScreenKey.KYC_REVIEW,
                fallbackGuide = RoleResponsibilityGuide(
                    roleTitle = "KYC Review Associate",
                    mission = "Protect the platform by verifying identity and risk indicators.",
                    responsibilities = listOf(
                        "Review account verification state and profile signals.",
                        "Prioritize high-risk or high-volume accounts for deeper checks.",
                        "Document pass/fail rationale clearly for audit readiness.",
                        "Escalate suspicious identity patterns to admin owner."
                    ),
                    escalationRule = "Escalate potential fraud or sanctions concerns immediately."
                )
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search name, email, role") }
            )
            OwnerFilterButtonRow(
                options = OwnerKycRoleFilter.values().toList(),
                selected = uiState.roleFilter,
                label = { it.title },
                onSelect = viewModel::onRoleChange
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show unverified only", modifier = Modifier.weight(1f))
                Switch(checked = uiState.unverifiedOnly, onCheckedChange = viewModel::onUnverifiedOnlyChange)
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (uiState.isLoading && uiState.items.isEmpty()) {
                OwnerCenteredLoading()
            } else if (uiState.filteredItems.isEmpty()) {
                Text("No KYC records match these filters.")
            } else {
                Text(
                    "Showing ${uiState.filteredItems.size} of ${uiState.items.size} record(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.filteredItems, key = { it.uid }) { item ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(item.email.ifBlank { "No email" }, style = MaterialTheme.typography.bodySmall)
                            Text("Role: ${item.role}", style = MaterialTheme.typography.bodySmall)
                            Text("Email verified: ${if (item.emailVerified) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                            Text("Profile status: ${item.profileStatus}", style = MaterialTheme.typography.bodySmall)
                            Text("Updated: ${formatDate(item.updatedAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

data class OwnerFeeSettingsUiState(
    val blindDateFeeUsd: String = "10.0",
    val agentAuthorizationFeeUsd: String = "1.0",
    val adPostFeeUsd: String = "1.0",
    val forexProfitMargin: String = "0.010",
    val stripeForexDepositProfitMargin: String = "0.005",
    val mobileMoneyHiddenFeeRate: String = "0.0",
    val eventTicketOwnerFeeRate: String = "0.05",
    val marketplacePlatinumFeeRate: String = "0.02",
    val garageSaleFeeRate: String = "0.02",
    val defaultTransferOwnerFeeUsd: String = "0.0",
    val corridorFees: List<TransferCorridorFeeRow> = TransferCorridorFeeCatalog.adminEditableCorridors(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

class OwnerFeeSettingsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val feeDocRef = db.collection(FirestoreCollection.APP_CONFIG).document(FirestoreAppConfigDocument.FEE_SETTINGS)
    private var listener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(OwnerFeeSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listener = feeDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load fee settings.") }
                return@addSnapshotListener
            }
            val data = snapshot?.data.orEmpty()
            val defaultOwner = (data["defaultTransferOwnerFeeUsd"] as? Number)?.toDouble()
                ?: data["defaultTransferOwnerFeeUsd"]?.toString()?.toDoubleOrNull()
                ?: 0.0
            val rawCorridors = data["transferCorridorFees"] as? Map<*, *>
            val overrides = linkedMapOf<String, Pair<Double, Double>>()
            rawCorridors?.forEach { (rawKey, rawValue) ->
                val key = rawKey?.toString()?.trim().orEmpty()
                if (key.isBlank()) return@forEach
                val entry = rawValue as? Map<*, *> ?: return@forEach
                val provider = (entry["providerFeeUsd"] as? Number)?.toDouble()
                    ?: entry["providerFeeUsd"]?.toString()?.toDoubleOrNull()
                    ?: 0.0
                val owner = (entry["ownerFeeUsd"] as? Number)?.toDouble()
                    ?: entry["ownerFeeUsd"]?.toString()?.toDoubleOrNull()
                    ?: defaultOwner
                overrides[key] = provider to owner
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    blindDateFeeUsd = data["blindDateFeeUsd"]?.toString() ?: it.blindDateFeeUsd,
                    agentAuthorizationFeeUsd = data["agentAuthorizationFeeUsd"]?.toString() ?: it.agentAuthorizationFeeUsd,
                    adPostFeeUsd = data["adPostFeeUsd"]?.toString() ?: it.adPostFeeUsd,
                    forexProfitMargin = data["forexProfitMargin"]?.toString() ?: it.forexProfitMargin,
                    stripeForexDepositProfitMargin = data["stripeForexDepositProfitMargin"]?.toString() ?: it.stripeForexDepositProfitMargin,
                    mobileMoneyHiddenFeeRate = data["mobileMoneyHiddenFeeRate"]?.toString() ?: it.mobileMoneyHiddenFeeRate,
                    eventTicketOwnerFeeRate = data["eventTicketOwnerFeeRate"]?.toString() ?: it.eventTicketOwnerFeeRate,
                    marketplacePlatinumFeeRate = data["marketplacePlatinumFeeRate"]?.toString() ?: it.marketplacePlatinumFeeRate,
                    garageSaleFeeRate = data["garageSaleFeeRate"]?.toString() ?: it.garageSaleFeeRate,
                    defaultTransferOwnerFeeUsd = defaultOwner.toString(),
                    corridorFees = TransferCorridorFeeCatalog.adminEditableCorridors(
                        overrides = overrides,
                        defaultOwnerFeeUsd = defaultOwner
                    )
                )
            }
        }
    }

    fun updateField(field: String, value: String) {
        _uiState.update {
            when (field) {
                "blindDateFeeUsd" -> it.copy(blindDateFeeUsd = value, error = null, message = null)
                "agentAuthorizationFeeUsd" -> it.copy(agentAuthorizationFeeUsd = value, error = null, message = null)
                "adPostFeeUsd" -> it.copy(adPostFeeUsd = value, error = null, message = null)
                "forexProfitMargin" -> it.copy(forexProfitMargin = value, error = null, message = null)
                "stripeForexDepositProfitMargin" -> it.copy(stripeForexDepositProfitMargin = value, error = null, message = null)
                "mobileMoneyHiddenFeeRate" -> it.copy(mobileMoneyHiddenFeeRate = value, error = null, message = null)
                "eventTicketOwnerFeeRate" -> it.copy(eventTicketOwnerFeeRate = value, error = null, message = null)
                "marketplacePlatinumFeeRate" -> it.copy(marketplacePlatinumFeeRate = value, error = null, message = null)
                "garageSaleFeeRate" -> it.copy(garageSaleFeeRate = value, error = null, message = null)
                "defaultTransferOwnerFeeUsd" -> it.copy(defaultTransferOwnerFeeUsd = value, error = null, message = null)
                else -> it
            }
        }
    }

    /** Fills the form only; nothing is written until the owner saves. */
    fun applyDefaults() {
        val defaults = OwnerFeeSettingsUiState()
        _uiState.update {
            it.copy(
                blindDateFeeUsd = defaults.blindDateFeeUsd,
                agentAuthorizationFeeUsd = defaults.agentAuthorizationFeeUsd,
                adPostFeeUsd = defaults.adPostFeeUsd,
                forexProfitMargin = defaults.forexProfitMargin,
                stripeForexDepositProfitMargin = defaults.stripeForexDepositProfitMargin,
                mobileMoneyHiddenFeeRate = defaults.mobileMoneyHiddenFeeRate,
                eventTicketOwnerFeeRate = defaults.eventTicketOwnerFeeRate,
                marketplacePlatinumFeeRate = defaults.marketplacePlatinumFeeRate,
                garageSaleFeeRate = defaults.garageSaleFeeRate,
                defaultTransferOwnerFeeUsd = defaults.defaultTransferOwnerFeeUsd,
                corridorFees = TransferCorridorFeeCatalog.adminEditableCorridors(),
                error = null,
                message = "Recommended defaults applied. Review and save to publish them."
            )
        }
    }

    fun updateCorridorFee(key: String, providerFeeUsd: String?, ownerFeeUsd: String?) {
        _uiState.update { state ->
            val updated = state.corridorFees.map { row ->
                if (row.key != key) return@map row
                row.copy(
                    providerFeeUsd = providerFeeUsd?.toDoubleOrNull() ?: row.providerFeeUsd,
                    ownerFeeUsd = ownerFeeUsd?.toDoubleOrNull() ?: row.ownerFeeUsd,
                    providerFeeRequired = if (providerFeeUsd?.toDoubleOrNull() != null) {
                        false
                    } else {
                        row.providerFeeRequired
                    }
                )
            }
            state.copy(corridorFees = updated, error = null, message = null)
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val blindDateFee = state.blindDateFeeUsd.toDoubleOrNull()
            val agentFee = state.agentAuthorizationFeeUsd.toDoubleOrNull()
            val adFee = state.adPostFeeUsd.toDoubleOrNull()
            val forex = state.forexProfitMargin.toDoubleOrNull()
            val stripeForex = state.stripeForexDepositProfitMargin.toDoubleOrNull()
            val hidden = state.mobileMoneyHiddenFeeRate.toDoubleOrNull()
            val eventOwnerFeeRate = state.eventTicketOwnerFeeRate.toDoubleOrNull()
            val marketplaceFeeRate = state.marketplacePlatinumFeeRate.toDoubleOrNull()
            val garageFeeRate = state.garageSaleFeeRate.toDoubleOrNull()
            val defaultOwnerFee = state.defaultTransferOwnerFeeUsd.toDoubleOrNull()
            if (
                blindDateFee == null ||
                agentFee == null ||
                adFee == null ||
                forex == null ||
                stripeForex == null ||
                hidden == null ||
                eventOwnerFeeRate == null ||
                marketplaceFeeRate == null ||
                garageFeeRate == null ||
                defaultOwnerFee == null
            ) {
                _uiState.update { it.copy(error = "Enter valid numeric values for all fee fields.") }
                return@launch
            }
            if (
                blindDateFee < 0 ||
                agentFee < 0 ||
                adFee < 0 ||
                forex < 0 ||
                stripeForex < 0 ||
                hidden < 0 ||
                eventOwnerFeeRate < 0 ||
                marketplaceFeeRate < 0 ||
                garageFeeRate < 0 ||
                defaultOwnerFee < 0
            ) {
                _uiState.update { it.copy(error = "Fee values cannot be negative.") }
                return@launch
            }
            if (state.corridorFees.any { it.providerFeeUsd < 0 || it.ownerFeeUsd < 0 }) {
                _uiState.update { it.copy(error = "Corridor provider/owner fees cannot be negative.") }
                return@launch
            }
            if (
                forex > 1 ||
                stripeForex > 1 ||
                hidden > 1 ||
                eventOwnerFeeRate > 1 ||
                marketplaceFeeRate > 1 ||
                garageFeeRate > 1
            ) {
                _uiState.update {
                    it.copy(error = "Rate fields must be between 0 and 1 (example: 0.05 for 5%).")
                }
                return@launch
            }

            val corridorPayload = state.corridorFees
                .filterNot { it.providerFeeRequired }
                .associate { row ->
                row.key to mapOf(
                    "iso2" to row.iso2,
                    "country" to row.country,
                    "route" to row.route,
                    "networks" to row.networks,
                    "providerFeeUsd" to row.providerFeeUsd,
                    "ownerFeeUsd" to row.ownerFeeUsd,
                    "fromSchedule1" to row.fromSchedule1
                )
            }

            _uiState.update { it.copy(isSaving = true, error = null, message = null) }
            try {
                val payload = mapOf(
                    "blindDateFeeUsd" to blindDateFee,
                    "agentAuthorizationFeeUsd" to agentFee,
                    "adPostFeeUsd" to adFee,
                    "forexProfitMargin" to forex,
                    "stripeForexDepositProfitMargin" to stripeForex,
                    "mobileMoneyHiddenFeeRate" to hidden,
                    "eventTicketOwnerFeeRate" to eventOwnerFeeRate,
                    "marketplacePlatinumFeeRate" to marketplaceFeeRate,
                    "garageSaleFeeRate" to garageFeeRate,
                    "defaultTransferOwnerFeeUsd" to defaultOwnerFee,
                    "transferCorridorFees" to corridorPayload
                )
                FunctionsClient.callMap(CallableFunction.OWNER_SAVE_FEE_SETTINGS, payload)
                _uiState.update { it.copy(isSaving = false, message = "Fee settings saved.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save fee settings.") }
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        listener = null
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerFeeSettingsScreen(
    onBack: () -> Unit,
    viewModel: OwnerFeeSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message, uiState.error) {
        uiState.message?.let { snackHost.showSnackbar(it) }
        uiState.error?.let { snackHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fee Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = !uiState.isSaving) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            item {
                ConfigurableRoleResponsibilitiesSection(
                    screenKey = RoleResponsibilitiesScreenKey.FEE_SETTINGS,
                    fallbackGuide = RoleResponsibilityGuide(
                        roleTitle = "Pricing Configuration Admin",
                        mission = "Set fee controls that balance growth, trust, and margin.",
                        responsibilities = listOf(
                            "Update fees only after policy and financial impact review.",
                            "Keep values non-negative and within approved ranges.",
                            "Coordinate fee changes with support and release notes.",
                            "Monitor post-change effects on disputes and conversion."
                        ),
                        escalationRule = "Escalate any emergency pricing rollback request to owner."
                    )
                )
            }

            item {
                Text("Platform fees", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
                ConfigNumberField("Blind Date Fee (USD)", uiState.blindDateFeeUsd) {
                    viewModel.updateField("blindDateFeeUsd", it)
                }
            }
            item {
                ConfigNumberField("Agent Authorization Fee (USD)", uiState.agentAuthorizationFeeUsd) {
                    viewModel.updateField("agentAuthorizationFeeUsd", it)
                }
            }
            item {
                ConfigNumberField("Sponsored Ad Fee (USD)", uiState.adPostFeeUsd) {
                    viewModel.updateField("adPostFeeUsd", it)
                }
            }
            item {
                ConfigNumberField("Forex Profit Margin", uiState.forexProfitMargin) {
                    viewModel.updateField("forexProfitMargin", it)
                }
            }
            item {
                ConfigNumberField("Stripe Forex Deposit Margin", uiState.stripeForexDepositProfitMargin) {
                    viewModel.updateField("stripeForexDepositProfitMargin", it)
                }
            }
            item {
                ConfigNumberField("Mobile Money Hidden Fee Rate (legacy)", uiState.mobileMoneyHiddenFeeRate) {
                    viewModel.updateField("mobileMoneyHiddenFeeRate", it)
                }
            }
            item {
                ConfigNumberField("Event Ticket Owner Fee Rate", uiState.eventTicketOwnerFeeRate) {
                    viewModel.updateField("eventTicketOwnerFeeRate", it)
                }
            }
            item {
                ConfigNumberField("Marketplace Platinum Fee Rate", uiState.marketplacePlatinumFeeRate) {
                    viewModel.updateField("marketplacePlatinumFeeRate", it)
                }
            }
            item {
                ConfigNumberField("Garage Sale Fee Rate", uiState.garageSaleFeeRate) {
                    viewModel.updateField("garageSaleFeeRate", it)
                }
            }
            item {
                Text(
                    "Rate fields should be between 0 and 1 (for example, 0.05 = 5%).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    "Transfer corridor fees",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                Text(
                    "Customer quote shows one Transfer fee = Provider (Afriex) + Owner. Confirmed local-bank and SWIFT provider charges are fixed; every enabled Mobile Money corridor needs its MSA provider fee before production quoting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                ConfigNumberField("Default owner fee (USD) when corridor owner is blank", uiState.defaultTransferOwnerFeeUsd) {
                    viewModel.updateField("defaultTransferOwnerFeeUsd", it)
                }
            }

            val momoRows = uiState.corridorFees.filter { it.route == "MOBILE_MONEY" }
            val bankRows = uiState.corridorFees.filter { it.route == "BANK" }
            val swiftRows = uiState.corridorFees.filter { it.route == "SWIFT" }

            item {
                Text("MoMo payout", fontWeight = FontWeight.SemiBold)
            }
            items(momoRows, key = { it.key }) { row ->
                CorridorFeeEditorCard(
                    row = row,
                    onProviderChanged = { viewModel.updateCorridorFee(row.key, providerFeeUsd = it, ownerFeeUsd = null) },
                    onOwnerChanged = { viewModel.updateCorridorFee(row.key, providerFeeUsd = null, ownerFeeUsd = it) }
                )
            }
            item {
                Text("Local bank payout", fontWeight = FontWeight.SemiBold)
            }
            items(bankRows, key = { it.key }) { row ->
                CorridorFeeEditorCard(
                    row = row,
                    onProviderChanged = { viewModel.updateCorridorFee(row.key, providerFeeUsd = it, ownerFeeUsd = null) },
                    onOwnerChanged = { viewModel.updateCorridorFee(row.key, providerFeeUsd = null, ownerFeeUsd = it) }
                )
            }
            item {
                Text("SWIFT payout (USD)", fontWeight = FontWeight.SemiBold)
            }
            item {
                Text(
                    "USD SWIFT provider fee is fixed at 0.25% of the transfer. Set only the optional owner markup here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(swiftRows, key = { it.key }) { row ->
                CorridorFeeEditorCard(
                    row = row,
                    onProviderChanged = { viewModel.updateCorridorFee(row.key, providerFeeUsd = it, ownerFeeUsd = null) },
                    onOwnerChanged = { viewModel.updateCorridorFee(row.key, providerFeeUsd = null, ownerFeeUsd = it) }
                )
            }

            item {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text(if (uiState.isSaving) "Saving..." else "Save Fee Settings")
                }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.applyDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text("Apply Recommended Defaults")
                }
            }
        }
    }
}

@Composable
private fun CorridorFeeEditorCard(
    row: TransferCorridorFeeRow,
    onProviderChanged: (String) -> Unit,
    onOwnerChanged: (String) -> Unit
) {
    var providerText by remember(row.key, row.providerFeeUsd, row.providerFeeRequired) {
        mutableStateOf(if (row.providerFeeRequired) "" else String.format("%.2f", row.providerFeeUsd))
    }
    var ownerText by remember(row.key, row.ownerFeeUsd) {
        mutableStateOf(String.format("%.2f", row.ownerFeeUsd))
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${row.country} · ${row.route.replace('_', ' ')}",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append(row.networks)
                    if (row.fromSchedule1) append(" · Schedule 1")
                    if (row.providerFeeRequired) append(" · MSA provider fee required")
                    if (row.route == "SWIFT") append(" · Provider 0.25% of transfer (fixed)")
                    if (row.route != "SWIFT") {
                        append(" · Total fee $${String.format("%.2f", row.transferFeeUsd)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (row.route == "SWIFT") "0.25%" else providerText,
                    onValueChange = {
                        providerText = it
                        onProviderChanged(it)
                    },
                    label = {
                        Text(
                            when {
                                row.route == "SWIFT" -> "Provider % (fixed)"
                                row.providerFeeLocked -> "Provider $ (fixed)"
                                row.providerFeeRequired -> "MSA provider $ (required)"
                                else -> "Provider $"
                            }
                        )
                    },
                    singleLine = true,
                    readOnly = row.providerFeeLocked,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ownerText,
                    onValueChange = {
                        ownerText = it
                        onOwnerChanged(it)
                    },
                    label = { Text("Owner $") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

data class OwnerSystemConfigUiState(
    val maintenanceMode: Boolean = false,
    val allowNewSignups: Boolean = true,
    val enableBlindDate: Boolean = true,
    val enableLiveStreams: Boolean = true,
    val maxUploadMb: String = "10",
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val message: String? = null,
    val error: String? = null
)

class OwnerSystemConfigViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val docRef = db.collection(FirestoreCollection.APP_CONFIG).document(FirestoreAppConfigDocument.SYSTEM_CONFIG)
    private var listener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(OwnerSystemConfigUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load system config.") }
                return@addSnapshotListener
            }
            val data = snapshot?.data.orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    maintenanceMode = data["maintenanceMode"] as? Boolean ?: it.maintenanceMode,
                    allowNewSignups = data["allowNewSignups"] as? Boolean ?: it.allowNewSignups,
                    enableBlindDate = data["enableBlindDate"] as? Boolean ?: it.enableBlindDate,
                    enableLiveStreams = data["enableLiveStreams"] as? Boolean ?: it.enableLiveStreams,
                    maxUploadMb = data["maxUploadMb"]?.toString() ?: it.maxUploadMb
                )
            }
        }
    }

    fun setMaintenanceMode(value: Boolean) {
        _uiState.update { it.copy(maintenanceMode = value, message = null, error = null) }
    }

    fun setAllowNewSignups(value: Boolean) {
        _uiState.update { it.copy(allowNewSignups = value, message = null, error = null) }
    }

    fun setEnableBlindDate(value: Boolean) {
        _uiState.update { it.copy(enableBlindDate = value, message = null, error = null) }
    }

    fun setEnableLiveStreams(value: Boolean) {
        _uiState.update { it.copy(enableLiveStreams = value, message = null, error = null) }
    }

    fun setMaxUploadMb(value: String) {
        _uiState.update { it.copy(maxUploadMb = value, message = null, error = null) }
    }

    /** Fills the form only; nothing is written until the owner saves. */
    fun applyDefaults() {
        val defaults = OwnerSystemConfigUiState()
        _uiState.update {
            it.copy(
                maintenanceMode = defaults.maintenanceMode,
                allowNewSignups = defaults.allowNewSignups,
                enableBlindDate = defaults.enableBlindDate,
                enableLiveStreams = defaults.enableLiveStreams,
                maxUploadMb = defaults.maxUploadMb,
                error = null,
                message = "Recommended defaults applied. Review and save to publish them."
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val maxUpload = _uiState.value.maxUploadMb.toIntOrNull()
            if (maxUpload == null || maxUpload <= 0) {
                _uiState.update { it.copy(error = "Max upload must be a positive number.") }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, message = null, error = null) }
            try {
                val payload = mapOf(
                    "maintenanceMode" to _uiState.value.maintenanceMode,
                    "allowNewSignups" to _uiState.value.allowNewSignups,
                    "enableBlindDate" to _uiState.value.enableBlindDate,
                    "enableLiveStreams" to _uiState.value.enableLiveStreams,
                    "maxUploadMb" to maxUpload
                )
                try {
                    FunctionsClient.callMap(CallableFunction.OWNER_SAVE_SYSTEM_CONFIG, payload)
                } catch (e: Exception) {
                    if (!isCallableMissing(e)) throw e
                    docRef.set(
                        payload + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }
                _uiState.update { it.copy(isSaving = false, message = "System config saved.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save system config.") }
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        listener = null
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSystemConfigScreen(
    onBack: () -> Unit,
    viewModel: OwnerSystemConfigViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message, uiState.error) {
        uiState.message?.let { snackHost.showSnackbar(it) }
        uiState.error?.let { snackHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Config") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = !uiState.isSaving) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConfigurableRoleResponsibilitiesSection(
                screenKey = RoleResponsibilitiesScreenKey.SYSTEM_CONFIG,
                fallbackGuide = RoleResponsibilityGuide(
                    roleTitle = "System Configuration Admin",
                    mission = "Maintain safe platform defaults and operational toggles.",
                    responsibilities = listOf(
                        "Change maintenance and signup flags with stakeholder notice.",
                        "Keep feature toggles aligned with policy and release readiness.",
                        "Set upload limits based on security and performance constraints.",
                        "Validate config changes before and after save."
                    ),
                    escalationRule = "Escalate any production-impacting misconfiguration immediately."
                )
            )

            ConfigToggleRow("Maintenance Mode", uiState.maintenanceMode, viewModel::setMaintenanceMode)
            ConfigToggleRow("Allow New Signups", uiState.allowNewSignups, viewModel::setAllowNewSignups)
            ConfigToggleRow("Enable Blind Date", uiState.enableBlindDate, viewModel::setEnableBlindDate)
            ConfigToggleRow("Enable Live Streams", uiState.enableLiveStreams, viewModel::setEnableLiveStreams)

            OutlinedTextField(
                value = uiState.maxUploadMb,
                onValueChange = viewModel::setMaxUploadMb,
                label = { Text("Max Upload (MB)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save System Config")
            }
            OutlinedButton(
                onClick = { viewModel.applyDefaults() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                Text("Apply Recommended Defaults")
            }
        }
    }
}

@Composable
private fun <T> OwnerFilterButtonRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            if (option == selected) {
                Button(onClick = { onSelect(option) }) { Text(label(option)) }
            } else {
                OutlinedButton(onClick = { onSelect(option) }) { Text(label(option)) }
            }
        }
    }
}

@Composable
private fun OwnerCenteredLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ConfigNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConfigToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun formatDate(value: Timestamp?): String {
    val date = value?.toDate() ?: return "--"
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
}
