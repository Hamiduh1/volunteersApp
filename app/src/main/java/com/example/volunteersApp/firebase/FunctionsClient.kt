package com.example.volunteersApp.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

object FunctionsClient {
    private val auth: FirebaseAuth = Firebase.auth
    private const val FUNCTIONS_REGION = "us-central1"
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    private const val MAX_UNAUTH_RETRIES = 2
    // Quotes are read-only. Retrying one transient provider/network failure is safe and
    // prevents an internal-test device from showing a false availability error.
    private const val MAX_QUOTE_TRANSIENT_RETRIES = 1
    private const val AUTH_READY_TIMEOUT_MS = 5000L
    private const val AUTH_READY_POLL_MS = 150L
    private val APP_CHECK_PREFLIGHT_REQUIRED = setOf(
        // Phone OTP functions enforce App Check at runtime.
        CallableFunction.REQUEST_MOBILE_MONEY_PHONE_OTP,
        CallableFunction.VERIFY_MOBILE_MONEY_PHONE_OTP,
        CallableFunction.AGENT_LOOKUP_CUSTOMER_BY_PHONE,
        CallableFunction.AGENT_START_CUSTOMER_WITHDRAWAL,
        CallableFunction.AGENT_VERIFY_CUSTOMER_WITHDRAWAL_OTP,
        CallableFunction.AGENT_REQUEST_CUSTOMER_WITHDRAWAL_APPROVAL,
        CallableFunction.AGENT_GET_CUSTOMER_WITHDRAWAL_SESSION,
        CallableFunction.AGENT_CONFIRM_CUSTOMER_CASH_HANDOVER,
        CallableFunction.AGENT_CANCEL_CUSTOMER_WITHDRAWAL,
        CallableFunction.AGENT_PROCESS_CUSTOMER_DEPOSIT_BY_PHONE,
        CallableFunction.CASH_OUT_OWNER_REVENUE,
        CallableFunction.SYNC_AFRIEX_BUSINESS_WALLET_MIRROR,
        CallableFunction.TOPUP_AFRIEX_SANDBOX_BUSINESS_WALLET,
        CallableFunction.CREATE_AFRIEX_SANDBOX_CHECKOUT_SESSION,
        CallableFunction.GET_AFRIEX_RATES,
        CallableFunction.GET_WALLET_TRANSFER_QUOTE,
        CallableFunction.GET_MOBILE_MONEY_SUPPORTED_COUNTRIES,
        CallableFunction.ENSURE_AFRIEX_CUSTOMER,
        CallableFunction.VERIFY_AFRIEX_CUSTOMER,
        CallableFunction.CREATE_BENEFICIARY_VERIFICATION,
        CallableFunction.APPLY_APPROVED_BENEFICIARY_VERIFICATION,
        CallableFunction.RESOLVE_AFRIEX_ACCOUNT,
        CallableFunction.SAVE_VERIFIED_BENEFICIARY,
        CallableFunction.CREATE_BANK_RECIPIENT_VERIFICATION,
        CallableFunction.APPLY_APPROVED_BANK_RECIPIENT_VERIFICATION,
        CallableFunction.ADMIN_LIST_PAYOUT_REQUESTS,
        CallableFunction.ADMIN_REVERSE_PAYOUT_REQUESTS_CALLABLE,
        CallableFunction.SUPPORT_LIST_USERS,
        CallableFunction.SUPPORT_GET_USER_ACCOUNT_DETAILS,
        CallableFunction.ADMIN_ADD_SUPPORT_ASSOCIATE,
        CallableFunction.GET_VOLUNTEER_LISTINGS,
        CallableFunction.APPLY_FOR_EVENT,
        CallableFunction.APPLY_FOR_JOB,
        CallableFunction.DELETE_ORGANIZER_EVENT,
        CallableFunction.DELETE_EMPLOYER_JOB,
        "adminListDepositRequests",
        // Live Studio callables enforce App Check (parity with Go Live / iOS).
        CallableFunction.GET_AGORA_RTC_TOKEN,
        CallableFunction.CREATE_CALL_SESSION,
        CallableFunction.ENSURE_DATING_ELIGIBILITY,
        CallableFunction.CREATE_LIVE_SHARE_ACCESS_LINK,
        CallableFunction.CREATE_LIVE_REPLAY_ACCESS_LINK,
        CallableFunction.REGISTER_USER_PUSH_TOKENS,
    )

    private suspend fun awaitAuthenticatedUser(timeoutMs: Long = AUTH_READY_TIMEOUT_MS): FirebaseUser {
        var elapsedMs = 0L
        while (elapsedMs <= timeoutMs) {
            auth.currentUser?.let { return it }
            delay(AUTH_READY_POLL_MS)
            elapsedMs += AUTH_READY_POLL_MS
        }
        throw IllegalStateException("You must be logged in.")
    }

    private suspend fun refreshAuthToken(): FirebaseUser {
        val user = awaitAuthenticatedUser()
        val tokenResult = user.getIdToken(true).await()
        Log.d(
            "FunctionsClient",
            "Auth token refreshed: uid=${user.uid}, provider=${tokenResult.signInProvider}, " +
                "issuedAt=${tokenResult.issuedAtTimestamp}, authAt=${tokenResult.authTimestamp}, " +
                "expiresAt=${tokenResult.expirationTimestamp}"
        )
        return user
    }

    private suspend fun logCallableContext(functionName: String, user: FirebaseUser) {
        try {
            val firebaseApp = Firebase.app
            val appCheckToken = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
            Log.d(
                "FunctionsClient",
                "Calling $functionName with uid=${user.uid}, project=${firebaseApp.options.projectId}, " +
                    "appId=${firebaseApp.options.applicationId}, appCheckPresent=${appCheckToken.isNotBlank()}"
            )
        } catch (e: Exception) {
            Log.w("FunctionsClient", "App Check diagnostics unavailable before $functionName", e)
        }
    }

    private suspend fun ensureAppCheckPreflight(
        functionName: String,
        forceRefresh: Boolean = false,
    ) {
        if (functionName !in APP_CHECK_PREFLIGHT_REQUIRED) return

        try {
            val appCheck = FirebaseAppCheck.getInstance()
            var token = appCheck.getAppCheckToken(forceRefresh).await().token.orEmpty()
            if (token.isBlank() && !forceRefresh) {
                token = appCheck.getAppCheckToken(true).await().token.orEmpty()
            }
            if (token.isBlank()) {
                throw IllegalStateException("App Check token is empty.")
            }
            Log.d("FunctionsClient", "App Check preflight passed for $functionName")
        } catch (e: Exception) {
            Log.e("FunctionsClient", "App Check preflight failed for $functionName", e)
            throw IllegalStateException(
                "App Check verification failed for $functionName. Please retry.",
                e
            )
        }
    }

    private suspend fun invokeCallable(functionName: String, data: Any? = null): Any? {
        val callable = functions.getHttpsCallable(functionName)
        return if (data == null) {
            callable.call().await().data
        } else {
            callable.call(data).await().data
        }
    }

    suspend fun callData(functionName: String, data: Any? = null): Any? {
        val user = refreshAuthToken()
        ensureAppCheckPreflight(functionName)
        logCallableContext(functionName, user)
        var unauthenticatedRetries = 0
        var transientQuoteRetries = 0
        while (true) {
            try {
                return invokeCallable(functionName, data)
            } catch (e: FirebaseFunctionsException) {
                Log.e(
                    "FunctionsClient",
                    "callable failed name=$functionName code=${e.code} message=${e.message} details=${e.details}",
                    e
                )
                if (e.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                    if (unauthenticatedRetries >= MAX_UNAUTH_RETRIES) {
                        Log.e(
                            "FunctionsClient",
                            "Cloud Function failed after retries name=$functionName code=${e.code} details=${e.details}",
                            e
                        )
                        throw e
                    }
                    unauthenticatedRetries += 1
                    Log.w(
                        "FunctionsClient",
                        "Retrying $functionName after UNAUTHENTICATED (attempt $unauthenticatedRetries/$MAX_UNAUTH_RETRIES)."
                    )
                    val refreshedUser = refreshAuthToken()
                    // A renewed ID token is not enough when Play Integrity has just issued or
                    // rotated the App Check token on an internal-test device.
                    ensureAppCheckPreflight(functionName, forceRefresh = true)
                    logCallableContext(functionName, refreshedUser)
                    delay(300L * unauthenticatedRetries)
                    continue
                }
                val retryableQuoteFailure = functionName == CallableFunction.GET_WALLET_TRANSFER_QUOTE &&
                    e.code in setOf(
                        FirebaseFunctionsException.Code.UNAVAILABLE,
                        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                        FirebaseFunctionsException.Code.INTERNAL,
                    )
                if (retryableQuoteFailure && transientQuoteRetries < MAX_QUOTE_TRANSIENT_RETRIES) {
                    transientQuoteRetries += 1
                    Log.w(
                        "FunctionsClient",
                        "Retrying live quote after transient ${e.code} (attempt $transientQuoteRetries/$MAX_QUOTE_TRANSIENT_RETRIES)."
                    )
                    delay(750L)
                    continue
                }
                if (e.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                    Log.e(
                        "FunctionsClient",
                        "Cloud Function failed after retries name=$functionName code=${e.code} details=${e.details}",
                        e
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e("FunctionsClient", "Cloud Function call failed: $functionName", e)
                throw e
            }
        }
    }

    /**
     * Calls an App-Check-protected callable that deliberately allows visitors.
     * Firebase attaches an authenticated user automatically when one is present.
     */
    suspend fun callPublicData(functionName: String, data: Any? = null): Any? {
        ensureAppCheckPreflight(functionName)
        try {
            return invokeCallable(functionName, data)
        } catch (e: FirebaseFunctionsException) {
            Log.e(
                "FunctionsClient",
                "Public callable failed name=$functionName code=${e.code} message=${e.message}",
                e
            )
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun callMap(functionName: String, data: Any? = null): Map<String, Any?>? {
        return callData(functionName, data) as? Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun callPublicMap(functionName: String, data: Any? = null): Map<String, Any?>? {
        return callPublicData(functionName, data) as? Map<String, Any?>
    }
}
