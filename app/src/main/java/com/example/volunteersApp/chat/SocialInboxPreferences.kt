package com.example.volunteersApp.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.socialInboxDataStore: DataStore<Preferences> by preferencesDataStore(name = "social_inbox")

/**
 * Keys aligned with iOS `UserDefaults` / `community.socialInbox.*` naming.
 */
private object SocialInboxPreferenceKeys {
    val selectedTab = stringPreferencesKey("community.socialInbox.selectedTab")
    val invitationStatusFilter = stringPreferencesKey("community.socialInbox.invitationStatusFilter")
    val callTypeFilter = stringPreferencesKey("community.socialInbox.callTypeFilter")
    val callDirectionFilter = stringPreferencesKey("community.socialInbox.callDirectionFilter")
    val inboxSearch = stringPreferencesKey("community.socialInbox.inboxSearch")
    val callSearch = stringPreferencesKey("community.socialInbox.callSearch")
}

data class SocialInboxPersistedState(
    val selectedTab: SocialInboxTab,
    val invitationStatusFilter: InvitationStatusFilter,
    val callTypeFilter: CallHistoryTypeFilter,
    val callDirectionFilter: CallHistoryDirectionFilter,
    val inboxSearch: String,
    val callSearch: String,
)

suspend fun Context.readSocialInboxPersistedState(): SocialInboxPersistedState {
    val p = socialInboxDataStore.data.first()
    return SocialInboxPersistedState(
        selectedTab = p[SocialInboxPreferenceKeys.selectedTab].toSocialInboxTab(),
        invitationStatusFilter = p[SocialInboxPreferenceKeys.invitationStatusFilter].toInvitationStatusFilter(),
        callTypeFilter = p[SocialInboxPreferenceKeys.callTypeFilter].toCallHistoryTypeFilter(),
        callDirectionFilter = p[SocialInboxPreferenceKeys.callDirectionFilter].toCallHistoryDirectionFilter(),
        inboxSearch = p[SocialInboxPreferenceKeys.inboxSearch].orEmpty(),
        callSearch = p[SocialInboxPreferenceKeys.callSearch].orEmpty(),
    )
}

suspend fun Context.writeSocialInboxSelectedTab(tab: SocialInboxTab) {
    socialInboxDataStore.edit { it[SocialInboxPreferenceKeys.selectedTab] = tab.name }
}

suspend fun Context.writeSocialInboxInvitationFilter(filter: InvitationStatusFilter) {
    socialInboxDataStore.edit { it[SocialInboxPreferenceKeys.invitationStatusFilter] = filter.name }
}

suspend fun Context.writeSocialInboxCallFilters(
    type: CallHistoryTypeFilter,
    direction: CallHistoryDirectionFilter,
) {
    socialInboxDataStore.edit { prefs ->
        prefs[SocialInboxPreferenceKeys.callTypeFilter] = type.name
        prefs[SocialInboxPreferenceKeys.callDirectionFilter] = direction.name
    }
}

suspend fun Context.writeSocialInboxSearches(inboxSearch: String, callSearch: String) {
    socialInboxDataStore.edit { prefs ->
        prefs[SocialInboxPreferenceKeys.inboxSearch] = inboxSearch
        prefs[SocialInboxPreferenceKeys.callSearch] = callSearch
    }
}

private fun String?.toSocialInboxTab(): SocialInboxTab =
    enumValues<SocialInboxTab>().firstOrNull { it.name == this } ?: SocialInboxTab.Chats

private fun String?.toInvitationStatusFilter(): InvitationStatusFilter =
    enumValues<InvitationStatusFilter>().firstOrNull { it.name == this } ?: InvitationStatusFilter.All

private fun String?.toCallHistoryTypeFilter(): CallHistoryTypeFilter =
    enumValues<CallHistoryTypeFilter>().firstOrNull { it.name == this } ?: CallHistoryTypeFilter.All

private fun String?.toCallHistoryDirectionFilter(): CallHistoryDirectionFilter =
    enumValues<CallHistoryDirectionFilter>().firstOrNull { it.name == this } ?: CallHistoryDirectionFilter.All
