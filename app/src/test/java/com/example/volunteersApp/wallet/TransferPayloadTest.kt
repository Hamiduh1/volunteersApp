package com.example.volunteersApp.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPayloadTest {
    @Test
    fun mobileMoneyVerificationRepeatsCanonicalRecipientIdentityAtTopLevel() {
        val payload = RecipientBeneficiaryPayload(
            name = "  Jane Doe  ",
            accountNumber = "+256712345678",
            bankCode = "MTN_UG",
            country = " Uganda ",
            mobileNumber = "+256712345678",
            network = " MTN Uganda ",
        )

        val request = payload.toMobileMoneyVerificationRequestMap(
            amount = 10.0,
            reuseSavedRecipientVerification = false,
        )

        assertEquals("MOBILE_MONEY", request["type"])
        assertEquals("Jane Doe", request["name"])
        assertEquals("Uganda", request["country"])
        assertEquals("+256712345678", request["phone"])
        assertEquals("+256712345678", request["mobileNumber"])
        assertEquals("+256712345678", request["accountNumber"])
        assertEquals("MTN Uganda", request["network"])
        assertEquals("MTN_UG", request["institutionCode"])
        assertTrue(request["recipientBeneficiary"] is Map<*, *>)
    }

    @Test
    fun savedVerifiedRecipientCarriesItsIdWhenReuseIsRequested() {
        val payload = RecipientBeneficiaryPayload(
            id = "saved-recipient-id",
            name = "Jane Doe",
            accountNumber = "+256712345678",
            bankCode = "MTN_UG",
            country = "Uganda",
            mobileNumber = "+256712345678",
            network = "MTN Uganda",
        )

        val request = payload.toMobileMoneyVerificationRequestMap(
            amount = 10.0,
            reuseSavedRecipientVerification = true,
        )
        val nested = request["recipientBeneficiary"] as Map<*, *>

        assertEquals("saved-recipient-id", request["beneficiaryId"])
        assertEquals("saved-recipient-id", nested["id"])
        assertEquals(true, request["reuseSavedRecipientVerification"])
    }
}
