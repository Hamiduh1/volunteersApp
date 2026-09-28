package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RoleResponsibilityGuide(
    val roleTitle: String,
    val mission: String,
    val responsibilities: List<String>,
    val escalationRule: String? = null
)

object RoleResponsibilitiesScreenKey {
    const val OWNER_DASHBOARD = "owner_dashboard"
    const val SUPPORT_CONSOLE_ADMIN = "support_console_admin"
    const val SUPPORT_CONSOLE_ASSOCIATE = "support_console_associate"
    const val PAYOUT_QUEUE = "payout_queue"
    const val DISPUTES = "disputes"
    const val USER_REPORTS = "user_reports"
    const val KYC_REVIEW = "kyc_review"
    const val FEE_SETTINGS = "fee_settings"
    const val SYSTEM_CONFIG = "system_config"
}

private object RoleResponsibilitiesRegistry {
    private val db = Firebase.firestore
    private val overridesFlow = MutableStateFlow<Map<String, RoleResponsibilityGuide>>(emptyMap())
    private var listener: ListenerRegistration? = null
    private val lock = Any()

    fun flow(): StateFlow<Map<String, RoleResponsibilityGuide>> {
        ensureListening()
        return overridesFlow.asStateFlow()
    }

    private fun ensureListening() {
        synchronized(lock) {
            if (listener != null) return
            listener = db.collection(FirestoreCollection.APP_CONFIG)
                .document("role_responsibilities")
                .addSnapshotListener { snapshot, _ ->
                    val root = snapshot?.data.orEmpty()
                    val parsed = parseGuides(root)
                    overridesFlow.value = parsed
                }
        }
    }

    private fun parseGuides(root: Map<String, Any>): Map<String, RoleResponsibilityGuide> {
        val candidateScreens = (root["screens"] as? Map<*, *>) ?: root
        val guides = mutableMapOf<String, RoleResponsibilityGuide>()
        candidateScreens.forEach { (keyAny, valueAny) ->
            val key = keyAny as? String ?: return@forEach
            val raw = valueAny as? Map<*, *> ?: return@forEach
            val map = raw.entries.associate { (k, v) -> (k as? String).orEmpty() to v }
            val roleTitle = (map["roleTitle"] as? String)?.trim().orEmpty()
            val mission = (map["mission"] as? String)?.trim().orEmpty()
            if (roleTitle.isEmpty() || mission.isEmpty()) return@forEach

            val responsibilities = (map["responsibilities"] as? List<*>)
                ?.mapNotNull { item -> (item as? String)?.trim()?.takeIf { it.isNotEmpty() } }
                .orEmpty()
            if (responsibilities.isEmpty()) return@forEach

            guides[key] = RoleResponsibilityGuide(
                roleTitle = roleTitle,
                mission = mission,
                responsibilities = responsibilities,
                escalationRule = (map["escalationRule"] as? String)?.trim()?.ifEmpty { null }
            )
        }
        return guides
    }
}

@Composable
fun ConfigurableRoleResponsibilitiesSection(
    screenKey: String,
    fallbackGuide: RoleResponsibilityGuide,
    modifier: Modifier = Modifier
) {
    val overrides by RoleResponsibilitiesRegistry.flow().collectAsState()
    val guide = overrides[screenKey] ?: fallbackGuide
    RoleResponsibilitiesSection(guide = guide, modifier = modifier)
}

@Composable
fun RoleResponsibilitiesSection(
    guide: RoleResponsibilityGuide,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(guide.roleTitle, guide.mission) { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Role Responsibilities",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide role responsibilities" else "Show role responsibilities",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (expanded) {
                Text(
                    text = guide.roleTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = guide.mission,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                guide.responsibilities.forEach { item ->
                    Text(
                        text = "- $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                guide.escalationRule?.takeIf { it.isNotBlank() }?.let { rule ->
                    Text(
                        text = "Escalation: $rule",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
