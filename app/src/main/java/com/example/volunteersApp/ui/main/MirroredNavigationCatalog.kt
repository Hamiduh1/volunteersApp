package com.example.volunteersApp.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.graphics.vector.ImageVector

data class MirroredNavItem(
    val route: String,
    val label: String
)

object MirroredNavigationCatalog {
    // Shared across Volunteer / Employer / Organizer.
    val communityItems: List<MirroredNavItem> = listOf(
        MirroredNavItem("community_hub", "Community Hub"),
        MirroredNavItem("my_chats", "Social Inbox"),
        MirroredNavItem("browse_users", "User Directory"),
        MirroredNavItem("marketplace", "Marketplace"),
        MirroredNavItem("jokes", "MindLoom"),
        MirroredNavItem("ads", "Sponsored"),
        MirroredNavItem("date_eva", "Date Hub")
    )

    val personalItems: List<MirroredNavItem> = listOf(
        MirroredNavItem("account_settings", "Account Settings"),
        MirroredNavItem("privacy_settings", "Security & Privacy"),
        MirroredNavItem("notification_settings", "Notifications"),
        MirroredNavItem("community_alerts", "Community Alerts")
    )

    val supportItems: List<MirroredNavItem> = listOf(
        MirroredNavItem("support", "Support Center"),
        MirroredNavItem("privacy_policy", "Privacy Policy"),
        MirroredNavItem("terms_conditions", "Terms"),
        MirroredNavItem("aml_cft", "AML/CFT"),
        MirroredNavItem("how_to_use", "How to Use"),
        MirroredNavItem("ai_assistant", "AI Assistant")
    )

    private val sharedTitles: Map<String, String> = mapOf(
        "community_hub" to "Community Hub",
        "my_chats" to "Social Inbox",
        "browse_users" to "User Directory",
        "marketplace" to "Marketplace",
        "jokes" to "MindLoom",
        "ads" to "Sponsored",
        "date_eva" to "Date Hub",
        "account_settings" to "Account Settings",
        "privacy_settings" to "Security & Privacy",
        "notification_settings" to "Notifications",
        "community_alerts" to "Community Alerts",
        "support" to "Support Center",
        "privacy_policy" to "Privacy Policy",
        "terms_conditions" to "Terms & Conditions",
        "aml_cft" to "AML/CFT Questionnaire",
        "how_to_use" to "How to Use",
        "ai_assistant" to "AI Assistant"
    )

    fun titleForRoute(route: String?): String? = route?.let { sharedTitles[it] }

    fun iconForRoute(route: String): ImageVector = when (route) {
        "community_hub" -> Icons.Default.Groups
        "my_chats" -> Icons.Default.Chat
        "browse_users" -> Icons.Default.AccountCircle
        "marketplace" -> Icons.Default.Storefront
        "jokes" -> Icons.Default.SentimentVerySatisfied
        "ads" -> Icons.Default.Campaign
        "date_eva" -> Icons.Default.Event
        "account_settings" -> Icons.Default.ManageAccounts
        "privacy_settings" -> Icons.Default.Lock
        "notification_settings" -> Icons.Default.Notifications
        "community_alerts" -> Icons.Default.Campaign
        "support" -> Icons.Default.SupportAgent
        "privacy_policy" -> Icons.Default.PrivacyTip
        "terms_conditions" -> Icons.Default.Policy
        "aml_cft" -> Icons.Default.GppGood
        "how_to_use" -> Icons.Default.Info
        "ai_assistant" -> Icons.Default.AutoAwesome
        else -> Icons.Default.Groups
    }
}
