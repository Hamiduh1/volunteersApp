package com.example.volunteersApp.wallet

import java.util.Currency
import java.util.Locale

private data class CountryMeta(val iso: String, val dial: String, val currency: String, val networks: List<String>)

private val countryMeta = mapOf(
    "Algeria" to CountryMeta("DZ", "+213", "DZD", listOf("Other")),
    "Angola" to CountryMeta("AO", "+244", "AOA", listOf("Other")),
    "Benin" to CountryMeta("BJ", "+229", "XOF", listOf("MOOV", "MTN")),
    "Botswana" to CountryMeta("BW", "+267", "BWP", listOf("Mascom")),
    "Burkina Faso" to CountryMeta("BF", "+226", "XOF", listOf("Orange")),
    "Burundi" to CountryMeta("BI", "+257", "BIF", listOf("Other")),
    "Cabo Verde" to CountryMeta("CV", "+238", "CVE", listOf("Other")),
    "Cameroon" to CountryMeta("CM", "+237", "XAF", listOf("MTN", "Orange")),
    "Central African Republic" to CountryMeta("CF", "+236", "XAF", listOf("Other")),
    "Chad" to CountryMeta("TD", "+235", "XAF", listOf("Airtel")),
    "Comoros" to CountryMeta("KM", "+269", "KMF", listOf("Other")),
    "Congo" to CountryMeta("CG", "+242", "XAF", listOf("Airtel")),
    "Cote d'Ivoire" to CountryMeta("CI", "+225", "XOF", listOf("MTN", "Orange", "MOOV", "Wave")),
    "Democratic Republic of the Congo" to CountryMeta("CD", "+243", "CDF", listOf("Other")),
    "Djibouti" to CountryMeta("DJ", "+253", "DJF", listOf("Other")),
    "Egypt" to CountryMeta("EG", "+20", "EGP", listOf("Vodafone")),
    "Equatorial Guinea" to CountryMeta("GQ", "+240", "XAF", listOf("Other")),
    "Eritrea" to CountryMeta("ER", "+291", "ERN", listOf("Other")),
    "Eswatini" to CountryMeta("SZ", "+268", "SZL", listOf("Other")),
    "Ethiopia" to CountryMeta("ET", "+251", "ETB", listOf("Telebirr", "M-Pesa")),
    "Gabon" to CountryMeta("GA", "+241", "XAF", listOf("Airtel")),
    "Gambia" to CountryMeta("GM", "+220", "GMD", listOf("Africell")),
    "Ghana" to CountryMeta("GH", "+233", "GHS", listOf("Airtel", "MTN")),
    "Guinea" to CountryMeta("GN", "+224", "GNF", listOf("Orange", "MTN")),
    "Guinea-Bissau" to CountryMeta("GW", "+245", "XOF", listOf("MTN")),
    "Kenya" to CountryMeta("KE", "+254", "KES", listOf("M-Pesa", "Airtel")),
    "Lesotho" to CountryMeta("LS", "+266", "LSL", listOf("Other")),
    "Liberia" to CountryMeta("LR", "+231", "LRD", listOf("Other")),
    "Libya" to CountryMeta("LY", "+218", "LYD", listOf("Other")),
    "Madagascar" to CountryMeta("MG", "+261", "MGA", listOf("MVOLA", "Airtel", "Orange")),
    "Malawi" to CountryMeta("MW", "+265", "MWK", listOf("Airtel", "TNM")),
    "Mali" to CountryMeta("ML", "+223", "XOF", listOf("Orange")),
    "Mauritania" to CountryMeta("MR", "+222", "MRU", listOf("Mauritel")),
    "Mauritius" to CountryMeta("MU", "+230", "MUR", listOf("my.t money")),
    "Morocco" to CountryMeta("MA", "+212", "MAD", listOf("Other")),
    "Mozambique" to CountryMeta("MZ", "+258", "MZN", listOf("Vodacom")),
    "Namibia" to CountryMeta("NA", "+264", "NAD", listOf("MTN")),
    "Niger" to CountryMeta("NE", "+227", "XOF", listOf("Airtel")),
    "Nigeria" to CountryMeta("NG", "+234", "NGN", listOf("MTN", "Glo", "Airtel", "9mobile")),
    "Republic of the Congo" to CountryMeta("CG", "+242", "XAF", listOf("Airtel")),
    "Rwanda" to CountryMeta("RW", "+250", "RWF", listOf("Airtel", "MTN")),
    "Sao Tome and Principe" to CountryMeta("ST", "+239", "STN", listOf("Other")),
    "Senegal" to CountryMeta("SN", "+221", "XOF", listOf("Orange", "Wave", "FreeMoney")),
    "Seychelles" to CountryMeta("SC", "+248", "SCR", listOf("Other")),
    "Sierra Leone" to CountryMeta("SL", "+232", "SLE", listOf("Orange", "Africell")),
    "Somalia" to CountryMeta("SO", "+252", "SOS", listOf("Other")),
    "South Africa" to CountryMeta("ZA", "+27", "ZAR", listOf("MTN", "Vodacom")),
    "South Sudan" to CountryMeta("SS", "+211", "SSP", listOf("Other")),
    "Sudan" to CountryMeta("SD", "+249", "SDG", listOf("MTN")),
    "Tanzania" to CountryMeta("TZ", "+255", "TZS", listOf("Airtel", "Tigo", "Vodacom")),
    "Togo" to CountryMeta("TG", "+228", "XOF", listOf("Togocel")),
    "Tunisia" to CountryMeta("TN", "+216", "TND", listOf("Other")),
    "Uganda" to CountryMeta("UG", "+256", "UGX", listOf("Airtel", "MTN")),
    "Zambia" to CountryMeta("ZM", "+260", "ZMW", listOf("MTN", "Airtel")),
    "Zimbabwe" to CountryMeta("ZW", "+263", "ZWL", listOf("Other"))
)

/**
 * Afriex MOBILE_MONEY institution / collection filter. This is not a delivery
 * allowlist; production delivery also requires the server-side Afriex account
 * configuration for the agreed corridor.
 */
private val afriexMobileMoneySupportedCountryIsos = setOf(
    // Approved payout or live collection
    "BJ", "BW", "CM", "CG", "CI", "ET", "GH", "GM", "GN", "KE", "MG", "MW",
    "MZ", "RW", "SN", "SL", "TZ", "UG", "ZM",
    // Coming soon MM deposit and/or payout
    "BF", "CF", "CD", "ML", "MA", "SS", "TG"
)

/**
 * Afriex Business API corridor availability.
 * Mirror of `ios-migration/afriex/05_SUPPORTED_CURRENCIES_AND_RAILS.md`
 * and https://docs.afriex.com/api-reference/supported-currencies
 */
enum class AfriexRailAvailability {
    LIVE,
    COMING_SOON,
    UNSUPPORTED
}

/** Live MOBILE_MONEY deposit (cash-in / phone verify) countries. */
private val afriexMobileMoneyDepositLiveIsos = setOf(
    "BJ", "CM", "CI", "ET", "KE", "TZ", "UG"
)

/** MOBILE_MONEY deposit listed by Afriex but not ready — show Coming soon. */
private val afriexMobileMoneyDepositComingSoonIsos = setOf(
    "BF", "CG", "GA", "GH", "MW", "MZ", "RW", "SL", "ZM"
)

/** All 23 MOBILE_MONEY payout corridors currently documented by Afriex. */
private val afriexMobileMoneyPayoutProviderCatalogIsos = setOf(
    "BJ", "BW", "CM", "CG", "CI", "EG", "ET", "GA", "GM", "GH", "GN", "GW",
    "KE", "MG", "MW", "MZ", "PK", "RW", "SN", "SL", "TZ", "UG", "ZM"
)

/**
 * Product-enabled MOBILE_MONEY payout corridors. This 19-country subset is the
 * submitted UAT and production-account scope, not the broader provider catalog.
 */
private val afriexMobileMoneyPayoutLiveIsos = setOf(
    "BJ", "BW", "CM", "CG", "CI", "ET", "GH", "GM", "GN", "KE", "MG", "MW",
    "MZ", "RW", "SN", "SL", "TZ", "UG", "ZM"
)

/** Recipient verification matches the 19-country approved payout scope. */
private val afriexMobileMoneyRecipientResolutionIsos = afriexMobileMoneyPayoutLiveIsos

/**
 * MOBILE_MONEY payout unavailable in this product. This includes provider
 * coming-soon rows and provider-listed routes outside the approved 19-country
 * production scope. They remain disabled in the UI instead of disappearing.
 */
private val afriexMobileMoneyPayoutComingSoonIsos = setOf(
    "BF", "CF", "CD", "EG", "GA", "GW", "ML", "MA", "PK", "SS", "TG"
)

/** Provider-documented local BANK_ACCOUNT payout corridors (45 total). */
private val afriexBankPayoutProviderCatalogIsos = setOf(
    "CM", "CI", "EG", "ET", "GH", "KE", "NG", "RW", "SN", "ZA", "UG",
    "US",
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
    "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO",
    "SK", "SI", "ES", "SE", "UA", "GB",
    "CN", "IN", "PK"
)

/**
 * Local-bank countries approved for this product's current Afriex account.
 *
 * The provider documentation is broader than the production agreement. Keep
 * the Android picker aligned with the server's explicit production allowlist
 * so a customer is not allowed to complete a recipient form that Functions
 * must reject before funding. SWIFT remains a separately selected rail.
 */
private val afriexLocalBankProductPayoutIsos = setOf(
    "EG", "GH", "KE", "NG", "US", "ZA"
)

/** Afriex local-bank settlement currencies; SWIFT settlement is always USD. */
private val afriexBankPayoutCurrencyByIso = mapOf(
    "CM" to "XAF", "CI" to "XOF", "EG" to "EGP", "ET" to "ETB", "GH" to "GHS",
    "KE" to "KES", "NG" to "NGN", "RW" to "RWF", "SN" to "XOF", "ZA" to "ZAR",
    "UG" to "UGX", "US" to "USD",
    "AT" to "EUR", "BE" to "EUR", "BG" to "EUR", "HR" to "EUR", "CY" to "EUR",
    "CZ" to "EUR", "DK" to "EUR", "EE" to "EUR", "FI" to "EUR", "FR" to "EUR",
    "DE" to "EUR", "GR" to "EUR", "HU" to "EUR", "IE" to "EUR", "IT" to "EUR",
    "LV" to "EUR", "LT" to "EUR", "LU" to "EUR", "MT" to "EUR", "NL" to "EUR",
    "NO" to "EUR", "PL" to "EUR", "PT" to "EUR", "RO" to "EUR", "SK" to "EUR",
    "SI" to "EUR", "ES" to "EUR", "SE" to "EUR", "UA" to "EUR", "GB" to "GBP",
    "CN" to "CNY", "IN" to "INR", "PK" to "PKR",
)

/** USD SWIFT payout — 100 countries per Afriex docs (2026-07-21). */
private val afriexSwiftPayoutLiveIsos = setOf(
    "DZ", "BJ", "BW", "BF", "CM", "CF", "CG", "CI", "CD", "EG", "ET", "GA", "GM", "GH", "GN", "GW",
    "KE", "MG", "MW", "ML", "MA", "MZ", "NA", "NE", "NG", "RW", "SN", "SL", "ZA", "SS", "TZ", "TG",
    "TN", "UG", "ZM", "ZW",
    "AR", "BR", "CA", "CL", "CO", "HT", "MX", "PE", "US", "UY",
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GI", "GR", "HU", "IE", "IT",
    "LV", "LI", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO", "RU", "SK", "SI", "ES", "SE", "CH",
    "UA", "GB",
    "AU", "BD", "CN", "HK", "ID", "IN", "JP", "KR", "KW", "MY", "NZ", "PH", "PK", "QA", "SA", "SG",
    "TH", "TR", "AE", "VN"
)

private fun displayCountryNameForIso(iso: String): String? {
    val upper = iso.trim().uppercase(Locale.US)
    if (upper.isBlank()) return null
    countryMeta.entries.firstOrNull { (_, meta) -> meta.iso == upper }?.key?.let { return it }
    globalCountryIsoCatalog.entries.firstOrNull { (_, catalogIso) -> catalogIso == upper }?.key?.let { return it }
    return runCatching {
        Locale("", upper).getDisplayCountry(Locale.ENGLISH).trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun countryNamesForIsos(isos: Set<String>): List<String> =
    isos.mapNotNull { displayCountryNameForIso(it) }
        .distinctBy { it.lowercase(Locale.US) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

fun afriexMobileMoneyDepositLiveCountries(): List<String> =
    countryNamesForIsos(afriexMobileMoneyDepositLiveIsos)

fun afriexMobileMoneyDepositComingSoonCountries(): List<String> =
    countryNamesForIsos(afriexMobileMoneyDepositComingSoonIsos)

fun afriexDocumentedMobileMoneyPayoutCountries(): List<String> =
    countryNamesForIsos(afriexMobileMoneyPayoutProviderCatalogIsos)

fun afriexMobileMoneyPayoutLiveCountries(): List<String> =
    countryNamesForIsos(afriexMobileMoneyPayoutLiveIsos)

fun afriexMobileMoneyPayoutComingSoonCountries(): List<String> =
    countryNamesForIsos(afriexMobileMoneyPayoutComingSoonIsos)

fun afriexBankPayoutLiveCountries(): List<String> =
    countryNamesForIsos(afriexLocalBankProductPayoutIsos)

/** iOS parity alias: live MM payout corridors. */
fun liveCorridorCountries(): List<String> = afriexMobileMoneyPayoutLiveCountries()

/** iOS parity alias: MM funding / deposit countries. */
fun mobileMoneyDepositCountries(): List<String> = afriexMobileMoneyDepositLiveCountries()

/** iOS parity alias: local bank fund + payout countries. */
fun bankPayoutCountries(): List<String> = afriexBankPayoutLiveCountries()

/** iOS parity alias: SWIFT payout countries. */
fun swiftPayoutCountries(): List<String> = afriexSwiftPayoutLiveCountries()

/** iOS parity alias: SWIFT − local bank catalog. */
fun swiftOnlyPayoutCountries(): List<String> = afriexSwiftOnlyPayoutLiveCountries()

/** Receive-only bank/SWIFT country union (not for funding checks). */
fun bankRecipientCountries(): List<String> =
    (afriexLocalBankPayoutLiveCountries() + afriexSwiftPayoutLiveCountries())
        .distinct()
        .sorted()

fun afriexSwiftPayoutLiveCountries(): List<String> =
    countryNamesForIsos(afriexSwiftPayoutLiveIsos)

/** Local bank only — excludes SWIFT-only corridors. */
fun afriexLocalBankPayoutLiveCountries(): List<String> =
    afriexBankPayoutLiveCountries()

/**
 * Countries available in the selected bank rail's recipient form.
 *
 * Local Bank is restricted to the product's approved production corridors.
 * SWIFT deliberately returns its complete 100-country USD catalog, rather
 * than a subtraction of the local-bank list: a country can validly support
 * both rails, but the selected rail determines the fields and transfer route.
 */
fun afriexBankRegistrationCountries(swiftRail: Boolean): List<String> =
    if (swiftRail) afriexSwiftPayoutLiveCountries() else afriexLocalBankPayoutLiveCountries()

/** Afriex documents local-bank account-name enquiry only for these corridors. */
fun afriexLocalBankSupportsAccountNameEnquiry(countryRaw: String?): Boolean =
    normalizeGlobalCountryIso(countryRaw) in setOf("GH", "NG")

/**
 * Provider-documented SWIFT corridors without a provider-documented local
 * BANK_ACCOUNT rail (for example UAE and Argentina). This is an explanatory
 * label only; it is intentionally independent of this product's smaller
 * active local-bank rollout list.
 */
fun afriexSwiftOnlyPayoutLiveCountries(): List<String> =
    afriexSwiftPayoutLiveCountries().filter { name ->
        normalizeGlobalCountryIso(name) !in afriexBankPayoutProviderCatalogIsos
    }

fun isAfriexSwiftOnlyPayoutCountry(countryRaw: String?): Boolean =
    isAfriexSwiftPayoutCountry(countryRaw) &&
        normalizeGlobalCountryIso(countryRaw) !in afriexBankPayoutProviderCatalogIsos

/** USD for SWIFT; local currency for BANK_ACCOUNT / mobile money. */
fun resolvedBankPayoutTargetCurrency(countryRaw: String?, destinationRoute: String): String {
    if (destinationRoute.equals("SWIFT", ignoreCase = true)) return "USD"
    val iso = normalizeGlobalCountryIso(countryRaw)
    return afriexBankPayoutCurrencyByIso[iso] ?: resolvedMobileMoneyTargetCurrency(countryRaw)
}

/** Quote / transfer route for a saved beneficiary type (Afriex BANK vs SWIFT vs MoMo). */
fun afriexBeneficiaryDestinationRoute(beneficiaryType: String?): String {
    val type = beneficiaryType.orEmpty().uppercase(Locale.US)
    return when {
        type.contains("SWIFT") -> "SWIFT"
        type.contains("BANK") -> "BANK"
        else -> "MOBILE_MONEY"
    }
}

data class AfriexCorridorCoverageSummary(
    val mobileMoneyDepositLive: Int,
    val mobileMoneyPayoutProviderCatalog: Int,
    val mobileMoneyPayoutLive: Int,
    val mobileMoneyPayoutComingSoon: Int,
    val localBankPayoutLive: Int,
    val swiftPayoutLive: Int,
    val swiftOnlyPayoutLive: Int,
    val docsSyncedAt: String = "2026-09-06",
)

fun afriexCorridorCoverageSummary(): AfriexCorridorCoverageSummary = AfriexCorridorCoverageSummary(
    mobileMoneyDepositLive = afriexMobileMoneyDepositLiveIsos.size,
    mobileMoneyPayoutProviderCatalog = afriexMobileMoneyPayoutProviderCatalogIsos.size,
    mobileMoneyPayoutLive = afriexMobileMoneyPayoutLiveIsos.size,
    mobileMoneyPayoutComingSoon = afriexMobileMoneyPayoutComingSoonIsos.size,
    localBankPayoutLive = afriexLocalBankProductPayoutIsos.size,
    swiftPayoutLive = afriexSwiftPayoutLiveIsos.size,
    swiftOnlyPayoutLive = afriexSwiftOnlyPayoutLiveCountries().size,
)

/** Collection / cash-in allowlist (separate from payout allowlists). */
fun mobileMoneyCollectionAvailability(countryRaw: String?): AfriexRailAvailability =
    afriexMobileMoneyDepositAvailability(countryRaw)

/** Payout allowlist for mobile-money send lanes. */
fun mobileMoneyPayoutAvailability(countryRaw: String?): AfriexRailAvailability =
    afriexMobileMoneyPayoutAvailability(countryRaw)

fun afriexMobileMoneyDepositAvailability(countryRaw: String?): AfriexRailAvailability {
    val iso = normalizeGlobalCountryIso(canonicalMobileMoneyCountry(countryRaw) ?: countryRaw)
        .ifBlank { normalizeGlobalCountryIso(countryRaw) }
    return when {
        iso in afriexMobileMoneyDepositLiveIsos -> AfriexRailAvailability.LIVE
        iso in afriexMobileMoneyDepositComingSoonIsos -> AfriexRailAvailability.COMING_SOON
        else -> AfriexRailAvailability.UNSUPPORTED
    }
}

fun afriexMobileMoneyPayoutAvailability(countryRaw: String?): AfriexRailAvailability {
    val iso = normalizeGlobalCountryIso(canonicalMobileMoneyCountry(countryRaw) ?: countryRaw)
        .ifBlank { normalizeGlobalCountryIso(countryRaw) }
    return when {
        iso in afriexMobileMoneyPayoutLiveIsos -> AfriexRailAvailability.LIVE
        iso in afriexMobileMoneyPayoutComingSoonIsos -> AfriexRailAvailability.COMING_SOON
        else -> AfriexRailAvailability.UNSUPPORTED
    }
}

/** Live availability of Afriex's pre-save mobile-money route verification. */
fun afriexMobileMoneyRecipientVerificationAvailability(countryRaw: String?): AfriexRailAvailability {
    val iso = normalizeGlobalCountryIso(canonicalMobileMoneyCountry(countryRaw) ?: countryRaw)
        .ifBlank { normalizeGlobalCountryIso(countryRaw) }
    return if (iso in afriexMobileMoneyRecipientResolutionIsos) {
        AfriexRailAvailability.LIVE
    } else {
        AfriexRailAvailability.UNSUPPORTED
    }
}

fun afriexBankPayoutAvailability(countryRaw: String?): AfriexRailAvailability {
    val iso = normalizeGlobalCountryIso(countryRaw)
    return when {
        iso in afriexLocalBankProductPayoutIsos -> AfriexRailAvailability.LIVE
        else -> AfriexRailAvailability.UNSUPPORTED
    }
}

fun afriexSwiftPayoutAvailability(countryRaw: String?): AfriexRailAvailability {
    val iso = normalizeGlobalCountryIso(countryRaw)
    return when {
        iso in afriexSwiftPayoutLiveIsos -> AfriexRailAvailability.LIVE
        else -> AfriexRailAvailability.UNSUPPORTED
    }
}

/** True when country supports local BANK_ACCOUNT payout. */
fun isAfriexLocalBankPayoutCountry(countryRaw: String?): Boolean =
    afriexBankPayoutAvailability(countryRaw) == AfriexRailAvailability.LIVE

/** True when country is in the 100-country USD SWIFT list. */
fun isAfriexSwiftPayoutCountry(countryRaw: String?): Boolean =
    afriexSwiftPayoutAvailability(countryRaw) == AfriexRailAvailability.LIVE

/** Countries previously surfaced via Afriex ISO filter (used to merge with backend corridor map). */
internal fun legacyAfriexMobileMoneyCountryNames(): List<String> =
    countryNamesForIsos(afriexMobileMoneySupportedCountryIsos)

/**
 * Default MM country list for Send Money / beneficiaries: live MM payout corridors.
 * Coming-soon corridors are exposed separately for UI labels.
 */
fun mobileMoneyCountries(): List<String> = afriexMobileMoneyPayoutLiveCountries()

/** Active Send Money / registration corridor — matches iOS Uganda default. */
const val ACTIVE_MOBILE_MONEY_CORRIDOR = "Uganda"

/**
 * Prefer Uganda when present in [countries]; otherwise the first entry, then Uganda.
 * Avoids alphabetical defaults (e.g. Benin/Ghana) that mis-route UG numbers.
 */
fun preferredMobileMoneyRegistrationCountry(countries: List<String>): String {
    val trimmed = countries.map { it.trim() }.filter { it.isNotEmpty() }
    return trimmed.firstOrNull { it.equals(ACTIVE_MOBILE_MONEY_CORRIDOR, ignoreCase = true) }
        ?: trimmed.firstOrNull()
        ?: ACTIVE_MOBILE_MONEY_CORRIDOR
}

/** Live + coming-soon MM payout names (for informational lists). */
fun mobileMoneyCountriesIncludingComingSoon(): List<String> =
    (afriexMobileMoneyPayoutLiveCountries() + afriexMobileMoneyPayoutComingSoonCountries())
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

private val globalCountryCurrencyCatalog: Map<String, String> by lazy {
    val collected = linkedMapOf<String, String>()
    Locale.getISOCountries().forEach { iso ->
        val locale = Locale("", iso)
        val countryName = locale.getDisplayCountry(Locale.ENGLISH).trim()
        if (countryName.isBlank()) return@forEach
        val currency = runCatching { Currency.getInstance(locale).currencyCode }
            .getOrNull()
            ?.uppercase(Locale.US)
            ?: return@forEach
        collected[countryName] = currency
    }
    // Keep a predictable fallback entry.
    if (!collected.containsKey("United States")) {
        collected["United States"] = "USD"
    }
    collected.toSortedMap(String.CASE_INSENSITIVE_ORDER).toMap()
}

private val globalCountryIsoCatalog: Map<String, String> by lazy {
    val collected = linkedMapOf<String, String>()
    Locale.getISOCountries().forEach { iso ->
        val locale = Locale("", iso)
        val countryName = locale.getDisplayCountry(Locale.ENGLISH).trim()
        if (countryName.isBlank()) return@forEach
        collected[countryName] = iso.uppercase(Locale.US)
    }
    if (!collected.containsKey("United States")) {
        collected["United States"] = "US"
    }
    collected.toSortedMap(String.CASE_INSENSITIVE_ORDER).toMap()
}

/** Calling prefixes for global bank and SWIFT recipient countries. */
private val globalDialCodeByIso = mapOf(
    "AR" to "+54", "AT" to "+43", "AU" to "+61", "BD" to "+880", "BE" to "+32",
    "BG" to "+359", "BR" to "+55", "CA" to "+1", "CH" to "+41", "CL" to "+56",
    "CN" to "+86", "CO" to "+57", "CY" to "+357", "CZ" to "+420", "DE" to "+49",
    "DK" to "+45", "EE" to "+372", "ES" to "+34", "FI" to "+358", "FR" to "+33",
    "GB" to "+44", "GI" to "+350", "GR" to "+30", "HK" to "+852", "HR" to "+385",
    "HT" to "+509", "HU" to "+36", "ID" to "+62", "IE" to "+353", "IN" to "+91",
    "IT" to "+39", "JP" to "+81", "KR" to "+82", "KW" to "+965", "LI" to "+423",
    "LT" to "+370", "LU" to "+352", "LV" to "+371", "MT" to "+356", "MX" to "+52",
    "MY" to "+60", "NL" to "+31", "NO" to "+47", "NZ" to "+64", "PE" to "+51",
    "PH" to "+63", "PK" to "+92", "PL" to "+48", "PT" to "+351", "QA" to "+974",
    "RO" to "+40", "RU" to "+7", "SA" to "+966", "SE" to "+46", "SG" to "+65",
    "SI" to "+386", "SK" to "+421", "TH" to "+66", "TR" to "+90", "UA" to "+380",
    "AE" to "+971", "US" to "+1", "UY" to "+598", "VN" to "+84",
)

// Stripe-oriented allowlist used by bank-account linking UX.
private val supportedBankCountryIsos = linkedSetOf(
    "AE",
    "AT",
    "AU",
    "BE",
    "BG",
    "BR",
    "CA",
    "CH",
    "CY",
    "CZ",
    "DE",
    "DK",
    "EE",
    "ES",
    "FI",
    "FR",
    "GB",
    "GI",
    "GR",
    "HK",
    "HR",
    "HU",
    "IE",
    "IN",
    "IT",
    "JP",
    "LI",
    "LT",
    "LU",
    "LV",
    "MT",
    "MX",
    "MY",
    "NL",
    "NO",
    "NZ",
    "PL",
    "PT",
    "RO",
    "SE",
    "SG",
    "SI",
    "SK",
    "TH",
    "US"
)

fun globalCountryCurrencyMap(): Map<String, String> = globalCountryCurrencyCatalog

fun globalCountries(): List<String> = globalCountryCurrencyCatalog.keys.toList()

fun supportedBankCountries(): List<String> {
    val names = globalCountryIsoCatalog.entries
        .filter { (_, iso) -> supportedBankCountryIsos.contains(iso.uppercase(Locale.US)) }
        .map { (countryName, _) -> countryName }
    return names.sortedWith(String.CASE_INSENSITIVE_ORDER)
}

fun isSupportedBankCountryIso(countryIso: String): Boolean =
    supportedBankCountryIsos.contains(countryIso.trim().uppercase(Locale.US))

fun defaultSupportedBankCountry(
    countries: List<String> = supportedBankCountries(),
    locale: Locale = Locale.getDefault()
): String {
    if (countries.isEmpty()) return "United States"
    val localeIso = locale.country.trim().uppercase(Locale.US)
    val localeMatch = countries.firstOrNull { country ->
        globalCountryIso(country).uppercase(Locale.US) == localeIso
    }
    if (!localeMatch.isNullOrBlank()) return localeMatch
    return countries.firstOrNull { country ->
        globalCountryIso(country).uppercase(Locale.US) == "US"
    } ?: countries.first()
}

fun globalCountryIso(country: String): String =
    countryMeta[country]?.iso
        ?: globalCountryIsoCatalog[country]
        ?: ""

fun normalizeGlobalCountryIso(rawCountry: String?): String {
    val trimmed = rawCountry?.trim().orEmpty()
    if (trimmed.isBlank()) return ""
    if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
        return trimmed.uppercase(Locale.US)
    }

    globalCountryIsoCatalog.entries.firstOrNull { (countryName, _) ->
        countryName.equals(trimmed, ignoreCase = true)
    }?.value?.let { return it.uppercase(Locale.US) }

    countryMeta.entries.firstOrNull { (countryName, _) ->
        countryName.equals(trimmed, ignoreCase = true)
    }?.value?.iso?.let { return it.uppercase(Locale.US) }

    val iso = Locale.getISOCountries()
        .firstOrNull { code ->
            val locale = Locale("", code)
            locale.getDisplayCountry(Locale.ENGLISH).equals(trimmed, ignoreCase = true) ||
                locale.displayCountry.equals(trimmed, ignoreCase = true)
        }

    return iso?.uppercase(Locale.US) ?: ""
}

fun globalCountryCurrency(country: String): String =
    countryMeta[country]?.currency
        ?: globalCountryCurrencyCatalog[country]
        ?: normalizeGlobalCountryIso(country)
            .takeIf { it.isNotBlank() }
            ?.let { iso ->
                runCatching { Currency.getInstance(Locale("", iso)).currencyCode }
                    .getOrNull()
            }
        ?: "USD"

fun globalCountryDialCode(country: String): String =
    (countryMeta[country]?.dial
        ?.takeIf { it.isNotBlank() && it != "+" }
        ?: globalDialCodeByIso[normalizeGlobalCountryIso(country)])
        .orEmpty()

// --- Backend-aligned mobile money corridors (mirrors Cloud Functions mobileMoneyCurrencyMap) ---

private val mobileMoneyCurrencyMapBackend: Map<String, String> = mapOf(
    "Algeria" to "DZD",
    "Angola" to "AOA",
    "Benin" to "XOF",
    "Botswana" to "BWP",
    "Burkina Faso" to "XOF",
    "Burundi" to "BIF",
    "Cabo Verde" to "CVE",
    "Cameroon" to "XAF",
    "Central African Republic" to "XAF",
    "Chad" to "XAF",
    "Comoros" to "KMF",
    "Congo" to "XAF",
    "Cote d'Ivoire" to "XOF",
    "Democratic Republic of the Congo" to "CDF",
    "Djibouti" to "DJF",
    "Egypt" to "EGP",
    "Equatorial Guinea" to "XAF",
    "Eritrea" to "ERN",
    "Eswatini" to "SZL",
    "Ethiopia" to "ETB",
    "Gabon" to "XAF",
    "Gambia" to "GMD",
    "Ghana" to "GHS",
    "Guinea" to "GNF",
    "Guinea-Bissau" to "XOF",
    "Kenya" to "KES",
    "Lesotho" to "LSL",
    "Liberia" to "LRD",
    "Libya" to "LYD",
    "Madagascar" to "MGA",
    "Malawi" to "MWK",
    "Mali" to "XOF",
    "Mauritania" to "MRU",
    "Mauritius" to "MUR",
    "Morocco" to "MAD",
    "Mozambique" to "MZN",
    "Namibia" to "NAD",
    "Niger" to "XOF",
    "Nigeria" to "NGN",
    "Republic of the Congo" to "XAF",
    "Rwanda" to "RWF",
    "Sao Tome and Principe" to "STN",
    "Senegal" to "XOF",
    "Seychelles" to "SCR",
    "Sierra Leone" to "SLE",
    "Somalia" to "SOS",
    "South Africa" to "ZAR",
    "South Sudan" to "SSP",
    "Sudan" to "SDG",
    "Tanzania" to "TZS",
    "Togo" to "XOF",
    "Tunisia" to "TND",
    "Uganda" to "UGX",
    "Zambia" to "ZMW",
    "Zimbabwe" to "ZWL",
    "Pakistan" to "PKR"
)

private val mobileMoneyCountryAliases: Map<String, String> = mapOf(
    "burkina faso" to "Burkina Faso",
    "burkina-faso" to "Burkina Faso",
    "congo brazzaville" to "Republic of the Congo",
    "congo-brazzaville" to "Republic of the Congo",
    "congo (brazzaville)" to "Republic of the Congo",
    "republic of congo" to "Republic of the Congo",
    "congo republic" to "Republic of the Congo",
    "drc" to "Democratic Republic of the Congo",
    "dr congo" to "Democratic Republic of the Congo",
    "congo kinshasa" to "Democratic Republic of the Congo",
    "democratic republic of congo" to "Democratic Republic of the Congo",
    "democratic republic of the congo" to "Democratic Republic of the Congo",
    "ethopia" to "Ethiopia",
    "guinea conakry" to "Guinea",
    "guinea (conakry)" to "Guinea",
    "ivory coast" to "Cote d'Ivoire",
    "ivorycoast" to "Cote d'Ivoire",
    "cote divoire" to "Cote d'Ivoire",
    "cote d'ivoire" to "Cote d'Ivoire",
    "cote d’ivoire" to "Cote d'Ivoire",
    "sierraleone" to "Sierra Leone"
)

private fun normalizeMobileMoneyCountryKey(value: String): String =
    value.trim().lowercase(Locale.US).replace(Regex("[_-]+"), " ").replace(Regex("\\s+"), " ")

fun canonicalMobileMoneyCountry(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val normalized = normalizeMobileMoneyCountryKey(raw)
    mobileMoneyCountryAliases[normalized]?.let { return it }
    mobileMoneyCurrencyMapBackend.keys.firstOrNull { normalizeMobileMoneyCountryKey(it) == normalized }?.let { return it }
    return null
}

fun resolvedMobileMoneyTargetCurrency(countryRaw: String?): String {
    val canonical = canonicalMobileMoneyCountry(countryRaw) ?: countryRaw?.trim().orEmpty()
    if (canonical.isBlank()) return "USD"
    mobileMoneyCurrencyMapBackend[canonical]?.let { return it.uppercase(Locale.US) }
    countryMeta[canonical]?.currency?.let { return it.uppercase(Locale.US) }
    return globalCountryCurrency(canonical).uppercase(Locale.US)
}

fun mergedMobileMoneyCountryNames(): List<String> = mobileMoneyCountriesIncludingComingSoon()

fun countryDialCode(country: String): String =
    countryMeta[country]?.dial
        ?: globalDialCodeByIso[normalizeGlobalCountryIso(country)]
        ?: "+"

fun countryCurrency(country: String): String = countryMeta[country]?.currency ?: "USD"

fun countryNetworks(country: String): List<String> = countryMeta[country]?.networks ?: listOf("Other")

fun countryFlag(country: String): String =
    (countryMeta[country]?.iso ?: normalizeGlobalCountryIso(country))
        .takeIf { it.length == 2 }
        ?.let(::isoToFlagEmoji)
        .orEmpty()

fun flagFromCountryName(countryName: String): String {
    val direct = countryMeta[countryName]?.iso
    if (!direct.isNullOrBlank()) return isoToFlagEmoji(direct)

    val normalized = countryName.trim().lowercase()
    val iso = Locale.getISOCountries()
        .firstOrNull { code ->
            Locale("", code).displayCountry.lowercase() == normalized
        }
    return if (iso == null) "" else isoToFlagEmoji(iso)
}

fun flagFromIso(iso: String): String {
    return isoToFlagEmoji(iso)
}

private fun isoToFlagEmoji(iso: String): String {
    if (iso.length != 2) return ""
    val upper = iso.uppercase()
    val first = upper[0].code - 'A'.code + 0x1F1E6
    val second = upper[1].code - 'A'.code + 0x1F1E6
    return String(intArrayOf(first, second), 0, 2)
}
