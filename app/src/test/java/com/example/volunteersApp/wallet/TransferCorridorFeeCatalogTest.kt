package com.example.volunteersApp.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCorridorFeeCatalogTest {

    @Test
    fun mobileMoneyPayoutScopeSeparatesProviderCoverageFromApprovedDelivery() {
        val documentedCountries = afriexDocumentedMobileMoneyPayoutCountries()
        val approvedCountries = afriexMobileMoneyPayoutLiveCountries()

        assertEquals(23, documentedCountries.size)
        assertTrue("Egypt" in documentedCountries)
        assertTrue("Pakistan" in documentedCountries)
        assertEquals(19, approvedCountries.size)
        assertTrue("Ghana" in approvedCountries)
        assertTrue("Ethiopia" in approvedCountries)
        assertFalse("Egypt" in approvedCountries)
        assertFalse("Pakistan" in approvedCountries)
    }

    @Test
    fun recipientVerificationMatchesTheNineteenCorridorUatScope() {
        val countries = afriexMobileMoneyPayoutLiveCountries()

        assertEquals(19, countries.size)
        countries.forEach { country ->
            assertEquals(
                "Recipient verification must match the payout catalog for $country",
                AfriexRailAvailability.LIVE,
                afriexMobileMoneyRecipientVerificationAvailability(country),
            )
        }
        assertEquals(
            AfriexRailAvailability.UNSUPPORTED,
            afriexMobileMoneyRecipientVerificationAvailability("Burkina Faso"),
        )
    }

    @Test
    fun swiftAlwaysUsesTheFixedProviderRateAndOnlyAcceptsSwiftOwnerMarkup() {
        assertEquals(
            0.25,
            TransferCorridorFeeCatalog.lookupTransferFeeUsd(
                countryRaw = "Ghana",
                route = "SWIFT",
                amountUsd = 100.0,
            ),
            0.001
        )

        assertEquals(
            0.25,
            TransferCorridorFeeCatalog.lookupTransferFeeUsd(
                countryRaw = "Ghana",
                route = "SWIFT",
                amountUsd = 100.0,
                overrides = mapOf("GH_BANK" to (1.75 to 0.50))
            ),
            0.001
        )

        assertEquals(
            3.50,
            TransferCorridorFeeCatalog.lookupTransferFeeUsd(
                countryRaw = "Ghana",
                route = "SWIFT",
                amountUsd = 1200.0,
                overrides = mapOf(
                    "GH_BANK" to (1.75 to 0.50),
                    "GH_SWIFT" to (2.50 to 0.50)
                )
            ),
            0.001
        )
    }

    @Test
    fun bankPayoutsMirrorTheFullProviderCatalogAndKeepUnpricedFeesBlocked() {
        val countries = afriexBankPayoutLiveCountries()

        assertEquals(45, countries.size)
        assertTrue("Cameroon" in countries)
        assertTrue("United States" in countries)
        assertTrue("United Kingdom" in countries)
        assertTrue("China" in countries)
        assertTrue("Pakistan" in countries)
        assertEquals(1.40, TransferCorridorFeeCatalog.lookupTransferFeeUsd("Ghana", "BANK"), 0.001)
        assertEquals(1.60, TransferCorridorFeeCatalog.lookupTransferFeeUsd("Egypt", "BANK"), 0.001)
        val cameroon = TransferCorridorFeeCatalog.adminEditableCorridors()
            .first { it.key == "CM_BANK" }
        assertTrue(cameroon.providerFeeRequired)
    }

    @Test
    fun mobileMoneyPayoutsUseTheConfirmedSchedule1Fees() {
        val expectedFees = mapOf(
            "Ghana" to 0.70,
            "Kenya" to 0.75,
            "Rwanda" to 0.15,
            "Cameroon" to 1.63,
            "Cote d'Ivoire" to 1.20,
            "Senegal" to 1.20,
            "Gambia" to 1.20,
            "Guinea" to 1.80,
            "Madagascar" to 1.95,
            "Malawi" to 1.35,
            "Mozambique" to 1.65,
            "Sierra Leone" to 1.20,
            "Tanzania" to 1.80,
            "Uganda" to 0.75,
            "Zambia" to 1.65,
            "Botswana" to 1.50,
            "Benin" to 1.80,
            "Congo (Brazzaville)" to 1.80,
        )

        expectedFees.forEach { (country, expectedFee) ->
            assertEquals(
                "Unexpected mobile-money fee for $country",
                expectedFee,
                TransferCorridorFeeCatalog.lookupTransferFeeUsd(country, "MOBILE_MONEY"),
                0.001,
            )
        }
        assertTrue(TransferCorridorFeeCatalog.schedule1Defaults["GH_MOBILE_MONEY"]?.providerFeeLocked == true)
        assertEquals(
            "Airtel, MTN",
            TransferCorridorFeeCatalog.schedule1Defaults["GH_MOBILE_MONEY"]?.networks,
        )
        assertEquals(0.0, TransferCorridorFeeCatalog.lookupTransferFeeUsd("Ethiopia", "MOBILE_MONEY"), 0.001)
        val ethiopia = TransferCorridorFeeCatalog.adminEditableCorridors()
            .first { it.key == "ET_MOBILE_MONEY" }
        assertTrue(ethiopia.providerFeeRequired)
        assertEquals("Telebirr, M-Pesa", ethiopia.networks)
        assertEquals(
            2.25,
            TransferCorridorFeeCatalog.lookupTransferFeeUsd(
                countryRaw = "Ethiopia",
                route = "MOBILE_MONEY",
                overrides = mapOf("ET_MOBILE_MONEY" to (1.50 to 0.75))
            ),
            0.001
        )
    }
}
