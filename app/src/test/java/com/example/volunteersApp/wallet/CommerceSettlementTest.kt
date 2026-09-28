package com.example.volunteersApp.wallet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommerceSettlementTest {

    @Test
    fun pendingCollectionStaysLockedUntilConfirmed() {
        val outcome = parseProviderCollectionOutcome(
            mapOf(
                "paymentStatus" to "pending",
                "success" to true,
                "message" to "Waiting on provider"
            )
        )
        assertTrue(outcome.isPending)
        assertFalse(outcome.isAccessUnlocked)
    }

    @Test
    fun confirmedCollectionUnlocksAccess() {
        val outcome = parseProviderCollectionOutcome(
            mapOf(
                "paymentStatus" to "succeeded",
                "accessUnlocked" to true
            )
        )
        assertFalse(outcome.isPending)
        assertTrue(outcome.isAccessUnlocked)
    }
}
