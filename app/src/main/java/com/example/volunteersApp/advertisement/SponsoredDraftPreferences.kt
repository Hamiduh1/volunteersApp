package com.example.volunteersApp.advertisement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sponsoredDraftDataStore: DataStore<Preferences> by preferencesDataStore(name = "sponsored_drafts")

private object Keys {
    val newAdTitle = stringPreferencesKey("new_ad_title")
    val newAdDescription = stringPreferencesKey("new_ad_description")
    val newAdTargetUrl = stringPreferencesKey("new_ad_target_url")
    val newAdOwnerPhone = stringPreferencesKey("new_ad_owner_phone")

    val garageTitle = stringPreferencesKey("garage_title")
    val garageDescription = stringPreferencesKey("garage_description")
    val garageShopName = stringPreferencesKey("garage_shop_name")
    val garageContactName = stringPreferencesKey("garage_contact_name")
    val garageContactPhone = stringPreferencesKey("garage_contact_phone")
    val garageContactEmail = stringPreferencesKey("garage_contact_email")
    val garageAddress = stringPreferencesKey("garage_address")
    val garageCity = stringPreferencesKey("garage_city")
    val garageState = stringPreferencesKey("garage_state")
    val garagePostal = stringPreferencesKey("garage_postal")
    val garageLatitude = stringPreferencesKey("garage_latitude")
    val garageLongitude = stringPreferencesKey("garage_longitude")
    val garageSaleType = stringPreferencesKey("garage_sale_type")
    val garageFirstDayMs = stringPreferencesKey("garage_first_day_ms")
    val garageLastDayMs = stringPreferencesKey("garage_last_day_ms")
    val garageOpensMinutes = stringPreferencesKey("garage_opens_minutes")
    val garageClosesMinutes = stringPreferencesKey("garage_closes_minutes")
    val garageCategories = stringPreferencesKey("garage_categories")
    val garagePaymentOptions = stringPreferencesKey("garage_payment_options")
    val garageNotes = stringPreferencesKey("garage_notes")
}

data class NewAdDraft(
    val title: String,
    val description: String,
    val targetUrl: String,
    val ownerPhone: String
)

data class GarageSaleDraft(
    val title: String,
    val description: String,
    val shopName: String,
    val contactName: String,
    val contactPhone: String,
    val contactEmail: String,
    val address: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val latitude: String,
    val longitude: String,
    val saleType: String = "",
    val firstDayMs: String = "",
    val lastDayMs: String = "",
    val opensMinutes: String = "",
    val closesMinutes: String = "",
    val categories: String = "",
    val paymentOptions: String = "",
    val notes: String = ""
)

suspend fun Context.readNewAdDraft(): NewAdDraft? {
    val p = sponsoredDraftDataStore.data.first()
    val title = p[Keys.newAdTitle].orEmpty()
    val description = p[Keys.newAdDescription].orEmpty()
    val targetUrl = p[Keys.newAdTargetUrl].orEmpty()
    val ownerPhone = p[Keys.newAdOwnerPhone].orEmpty()
    if (title.isBlank() && description.isBlank() && targetUrl.isBlank() && ownerPhone.isBlank()) return null
    return NewAdDraft(title, description, targetUrl, ownerPhone)
}

suspend fun Context.writeNewAdDraft(draft: NewAdDraft) {
    sponsoredDraftDataStore.edit { prefs ->
        prefs[Keys.newAdTitle] = draft.title
        prefs[Keys.newAdDescription] = draft.description
        prefs[Keys.newAdTargetUrl] = draft.targetUrl
        prefs[Keys.newAdOwnerPhone] = draft.ownerPhone
    }
}

suspend fun Context.clearNewAdDraft() {
    sponsoredDraftDataStore.edit { prefs ->
        prefs.remove(Keys.newAdTitle)
        prefs.remove(Keys.newAdDescription)
        prefs.remove(Keys.newAdTargetUrl)
        prefs.remove(Keys.newAdOwnerPhone)
    }
}

suspend fun Context.readGarageSaleDraft(): GarageSaleDraft? {
    val p = sponsoredDraftDataStore.data.first()
    val draft = GarageSaleDraft(
        title = p[Keys.garageTitle].orEmpty(),
        description = p[Keys.garageDescription].orEmpty(),
        shopName = p[Keys.garageShopName].orEmpty(),
        contactName = p[Keys.garageContactName].orEmpty(),
        contactPhone = p[Keys.garageContactPhone].orEmpty(),
        contactEmail = p[Keys.garageContactEmail].orEmpty(),
        address = p[Keys.garageAddress].orEmpty(),
        city = p[Keys.garageCity].orEmpty(),
        state = p[Keys.garageState].orEmpty(),
        postalCode = p[Keys.garagePostal].orEmpty(),
        latitude = p[Keys.garageLatitude].orEmpty(),
        longitude = p[Keys.garageLongitude].orEmpty(),
        saleType = p[Keys.garageSaleType].orEmpty(),
        firstDayMs = p[Keys.garageFirstDayMs].orEmpty(),
        lastDayMs = p[Keys.garageLastDayMs].orEmpty(),
        opensMinutes = p[Keys.garageOpensMinutes].orEmpty(),
        closesMinutes = p[Keys.garageClosesMinutes].orEmpty(),
        categories = p[Keys.garageCategories].orEmpty(),
        paymentOptions = p[Keys.garagePaymentOptions].orEmpty(),
        notes = p[Keys.garageNotes].orEmpty()
    )
    val allBlank = listOf(
        draft.title, draft.description, draft.shopName, draft.contactName, draft.contactPhone, draft.contactEmail,
        draft.address, draft.city, draft.state, draft.postalCode, draft.latitude, draft.longitude,
        draft.categories, draft.notes
    ).all { it.isBlank() }
    return if (allBlank) null else draft
}

suspend fun Context.writeGarageSaleDraft(draft: GarageSaleDraft) {
    sponsoredDraftDataStore.edit { prefs ->
        prefs[Keys.garageTitle] = draft.title
        prefs[Keys.garageDescription] = draft.description
        prefs[Keys.garageShopName] = draft.shopName
        prefs[Keys.garageContactName] = draft.contactName
        prefs[Keys.garageContactPhone] = draft.contactPhone
        prefs[Keys.garageContactEmail] = draft.contactEmail
        prefs[Keys.garageAddress] = draft.address
        prefs[Keys.garageCity] = draft.city
        prefs[Keys.garageState] = draft.state
        prefs[Keys.garagePostal] = draft.postalCode
        prefs[Keys.garageLatitude] = draft.latitude
        prefs[Keys.garageLongitude] = draft.longitude
        prefs[Keys.garageSaleType] = draft.saleType
        prefs[Keys.garageFirstDayMs] = draft.firstDayMs
        prefs[Keys.garageLastDayMs] = draft.lastDayMs
        prefs[Keys.garageOpensMinutes] = draft.opensMinutes
        prefs[Keys.garageClosesMinutes] = draft.closesMinutes
        prefs[Keys.garageCategories] = draft.categories
        prefs[Keys.garagePaymentOptions] = draft.paymentOptions
        prefs[Keys.garageNotes] = draft.notes
    }
}

suspend fun Context.clearGarageSaleDraft() {
    sponsoredDraftDataStore.edit { prefs ->
        prefs.remove(Keys.garageTitle)
        prefs.remove(Keys.garageDescription)
        prefs.remove(Keys.garageShopName)
        prefs.remove(Keys.garageContactName)
        prefs.remove(Keys.garageContactPhone)
        prefs.remove(Keys.garageContactEmail)
        prefs.remove(Keys.garageAddress)
        prefs.remove(Keys.garageCity)
        prefs.remove(Keys.garageState)
        prefs.remove(Keys.garagePostal)
        prefs.remove(Keys.garageLatitude)
        prefs.remove(Keys.garageLongitude)
        prefs.remove(Keys.garageSaleType)
        prefs.remove(Keys.garageFirstDayMs)
        prefs.remove(Keys.garageLastDayMs)
        prefs.remove(Keys.garageOpensMinutes)
        prefs.remove(Keys.garageClosesMinutes)
        prefs.remove(Keys.garageCategories)
        prefs.remove(Keys.garagePaymentOptions)
        prefs.remove(Keys.garageNotes)
    }
}
