package com.example.volunteersApp.wallet

/**
 * Confirmed Afriex production Schedule 1 bank and mobile-money payout commercials.
 * Mirror: `ios-migration/afriex/06_SCHEDULE1_TRANSFER_FEES.md`
 * Admin overrides live in `app_config/fee_settings.transferCorridorFees`.
 */
data class TransferCorridorFeeRow(
    val key: String,
    val iso2: String,
    val country: String,
    val route: String,
    val networks: String,
    val providerFeeUsd: Double,
    val ownerFeeUsd: Double,
    val fromSchedule1: Boolean,
    val providerFeeLocked: Boolean = false,
    val providerFeeRequired: Boolean = false,
) {
    val transferFeeUsd: Double get() = providerFeeUsd + ownerFeeUsd
}

object TransferCorridorFeeCatalog {
    private const val swiftProviderFeeRate = 0.0025

    fun corridorKey(iso2: String, route: String): String =
        "${iso2.trim().uppercase()}_${route.trim().uppercase()}"

    private fun pricingKeys(iso2: String, route: String): List<String> {
        return listOf(corridorKey(iso2, route))
    }

    /** Confirmed fixed Schedule 1 payout defaults. */
    val schedule1Defaults: Map<String, TransferCorridorFeeRow> = listOf(
        row("NG", "Nigeria", "BANK", "Bank Account", 0.20, true),
        row("GH", "Ghana", "BANK", "Bank Account", 1.40, true),
        row("KE", "Kenya", "BANK", "Bank Account", 0.25, true),
        row("ZA", "South Africa", "BANK", "Bank Account", 1.20, true),
        row("EG", "Egypt", "BANK", "Bank Account", 1.60, true),
        row("GH", "Ghana", "MOBILE_MONEY", "Airtel, MTN", 0.70, true),
        row("KE", "Kenya", "MOBILE_MONEY", "M-Pesa, Airtel", 0.75, true),
        row("RW", "Rwanda", "MOBILE_MONEY", "Airtel, MTN", 0.15, true),
        row("CM", "Cameroon", "MOBILE_MONEY", "MTN, Orange", 1.63, true),
        row("CI", "Cote d'Ivoire", "MOBILE_MONEY", "MTN, Orange, MOOV, Wave", 1.20, true),
        row("SN", "Senegal", "MOBILE_MONEY", "Orange, Wave, FreeMoney", 1.20, true),
        row("GM", "Gambia", "MOBILE_MONEY", "Africell", 1.20, true),
        row("GN", "Guinea", "MOBILE_MONEY", "Orange, MTN", 1.80, true),
        row("MG", "Madagascar", "MOBILE_MONEY", "MVOLA, Airtel, Orange", 1.95, true),
        row("MW", "Malawi", "MOBILE_MONEY", "Airtel, TNM", 1.35, true),
        row("MZ", "Mozambique", "MOBILE_MONEY", "Vodacom", 1.65, true),
        row("SL", "Sierra Leone", "MOBILE_MONEY", "Orange, Africell", 1.20, true),
        row("TZ", "Tanzania", "MOBILE_MONEY", "Airtel, Tigo, Vodacom", 1.80, true),
        row("UG", "Uganda", "MOBILE_MONEY", "Airtel, MTN", 0.75, true),
        row("ZM", "Zambia", "MOBILE_MONEY", "Airtel, MTN", 1.65, true),
        row("BW", "Botswana", "MOBILE_MONEY", "Mascom", 1.50, true),
        row("BJ", "Benin", "MOBILE_MONEY", "MOOV, MTN", 1.80, true),
        row("CG", "Congo (Brazzaville)", "MOBILE_MONEY", "MTN, Airtel", 1.80, true),
    ).associateBy { it.key }

    // Afriex lists Ethiopia as a payout corridor, but the supplied schedule has no commercial.
    private val unpricedMobileMoneyNetworksByIso = mapOf(
        "ET" to "Telebirr, M-Pesa",
    )
    private fun row(
        iso2: String,
        country: String,
        route: String,
        networks: String,
        providerFeeUsd: Double,
        fromSchedule1: Boolean,
        ownerFeeUsd: Double = 0.0
    ) = TransferCorridorFeeRow(
        key = corridorKey(iso2, route),
        iso2 = iso2,
        country = country,
        route = route,
        networks = networks,
        providerFeeUsd = providerFeeUsd,
        ownerFeeUsd = ownerFeeUsd,
        fromSchedule1 = fromSchedule1,
        providerFeeLocked = fromSchedule1,
    )

    /**
     * Admin dashboard rows: fixed Schedule 1 terms plus provider catalog rows.
     * Corridors outside Schedule 1 require an agreed provider fee before production quoting.
     */
    fun adminEditableCorridors(
        overrides: Map<String, Pair<Double, Double>> = emptyMap(),
        defaultOwnerFeeUsd: Double = 0.0
    ): List<TransferCorridorFeeRow> {
        val built = linkedMapOf<String, TransferCorridorFeeRow>()

        fun put(iso2: String, country: String, route: String, networks: String) {
            val key = corridorKey(iso2, route)
            val schedule = schedule1Defaults[key]
            val override = overrides[key]
            built[key] = TransferCorridorFeeRow(
                key = key,
                iso2 = iso2,
                country = country,
                route = route,
                networks = schedule?.networks ?: networks,
                providerFeeUsd = schedule?.providerFeeUsd ?: override?.first ?: 0.0,
                ownerFeeUsd = override?.second ?: schedule?.ownerFeeUsd?.takeIf { it > 0.0 } ?: defaultOwnerFeeUsd,
                fromSchedule1 = schedule != null,
                providerFeeLocked = schedule != null || route == "SWIFT",
                providerFeeRequired = (route == "MOBILE_MONEY" || route == "BANK") && schedule == null,
            )
        }

        afriexMobileMoneyPayoutLiveCountries().forEach { name ->
            val iso = normalizeGlobalCountryIso(name)
            if (iso.isBlank()) return@forEach
            put(
                iso,
                name,
                "MOBILE_MONEY",
                unpricedMobileMoneyNetworksByIso[iso] ?: "Mobile Money",
            )
        }

        afriexBankPayoutLiveCountries().forEach { name ->
            val iso = normalizeGlobalCountryIso(name)
            if (iso.isBlank()) return@forEach
            put(iso, name, "BANK", "Local bank account")
        }

        afriexSwiftPayoutLiveCountries().forEach { name ->
            val iso = normalizeGlobalCountryIso(name)
            if (iso.isBlank() || isAfriexLocalBankPayoutCountry(name)) return@forEach
            put(iso, name, "SWIFT", "SWIFT USD (provider fee: 0.25% of transfer)")
        }

        return built.values.sortedWith(
            compareBy<TransferCorridorFeeRow> { it.route }
                .thenBy { it.country.lowercase() }
        )
    }

    /** Customer-facing fallback only; the Firebase quote remains authoritative. */
    fun lookupTransferFeeUsd(
        countryRaw: String?,
        route: String,
        amountUsd: Double = 0.0,
        overrides: Map<String, Pair<Double, Double>> = emptyMap(),
        defaultOwnerFeeUsd: Double = 0.0,
    ): Double {
        val iso = normalizeGlobalCountryIso(countryRaw)
        if (iso.isBlank()) return 0.0
        val keys = pricingKeys(iso, route)
        val schedule = keys.firstNotNullOfOrNull { schedule1Defaults[it] }
        val override = keys.firstNotNullOfOrNull { overrides[it] }
        if (route.equals("SWIFT", ignoreCase = true)) {
            val owner = override?.second ?: defaultOwnerFeeUsd
            return amountUsd.coerceAtLeast(0.0) * swiftProviderFeeRate + owner
        }
        val provider = schedule?.providerFeeUsd ?: override?.first ?: 0.0
        val owner = override?.second ?: schedule?.ownerFeeUsd?.takeIf { it > 0.0 } ?: defaultOwnerFeeUsd
        return provider + owner
    }
}
