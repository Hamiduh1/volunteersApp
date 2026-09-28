package com.example.volunteersApp.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWalletMirrorTest {

    @Test
    fun mirrorBackedBalanceUsesAvailableCents() {
        val snapshot = resolveProviderWalletSnapshotFromMaps(
            walletData = mapOf(
                "provider" to "stripe",
                "providerCustomerId" to "cus_123",
                "availableBalanceCents" to 12_345L,
                "currency" to "usd",
                "walletActive" to true
            ),
            userData = mapOf(
                "name" to "Ada Lovelace",
                "country" to "United States",
                "email" to "ada@example.com",
                "wallet" to mapOf("balance" to 999.0)
            ),
            walletDocumentExists = true
        )

        assertTrue(snapshot.isMirrorBacked)
        assertEquals(123.45, snapshot.balance, 0.001)
        assertEquals(WalletActivationState.ACTIVE, snapshot.activation.state)
        assertTrue(snapshot.activation.isReady)
    }

    @Test
    fun legacyFallbackShowsHistoryBalanceOnlyWhenMirrorMissing() {
        val snapshot = resolveProviderWalletSnapshotFromMaps(
            walletData = emptyMap(),
            userData = mapOf(
                "name" to "Ada Lovelace",
                "country" to "United States",
                "email" to "ada@example.com",
                "wallet" to mapOf("balance" to 42.5, "currency" to "GHS")
            ),
            walletDocumentExists = false
        )

        assertFalse(snapshot.isMirrorBacked)
        assertTrue(snapshot.usedLegacyFallback)
        assertEquals(42.5, snapshot.balance, 0.001)
        assertEquals("GHS", snapshot.currency)
        assertFalse(snapshot.activation.isReady)
    }

    @Test
    fun activationPendingRequiresExplicitWalletFlag() {
        val snapshot = resolveProviderWalletSnapshotFromMaps(
            walletData = mapOf(
                "provider" to "afriex",
                "providerCustomerId" to "cust_abc",
                "availableBalanceCents" to 0L,
                "activationPending" to true
            ),
            userData = mapOf(
                "name" to "Ada Lovelace",
                "country" to "Ghana",
                "phoneNumber" to "+233200000000"
            ),
            walletDocumentExists = true
        )

        assertEquals(WalletActivationState.PENDING, snapshot.activation.state)
        assertFalse(snapshot.activation.isReady)
    }

    @Test
    fun stripeConnectFlagsDoNotActivateGeneralWallet() {
        val snapshot = resolveProviderWalletSnapshotFromMaps(
            walletData = mapOf(
                "provider" to "stripe",
                "providerCustomerId" to "acct_123",
                "payoutsEnabled" to true,
                "chargesEnabled" to true,
                "detailsSubmitted" to true,
                "hasAccount" to true
            ),
            userData = mapOf(
                "name" to "Ada Lovelace",
                "country" to "United States",
                "email" to "ada@example.com"
            ),
            walletDocumentExists = true
        )

        assertEquals(WalletActivationState.ACTIVATE, snapshot.activation.state)
        assertFalse(snapshot.activation.isReady)
    }

    @Test
    fun completeProfileRequiredBeforeActivation() {
        val snapshot = resolveProviderWalletSnapshotFromMaps(
            walletData = emptyMap(),
            userData = mapOf("email" to "ada@example.com"),
            walletDocumentExists = false
        )

        assertEquals(WalletActivationState.COMPLETE_PROFILE, snapshot.activation.state)
    }
}
