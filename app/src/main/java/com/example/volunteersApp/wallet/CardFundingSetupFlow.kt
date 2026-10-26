package com.example.volunteersApp.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.functions.FirebaseFunctionsException
import com.stripe.android.model.CardParams
import com.stripe.android.model.ConfirmSetupIntentParams
import com.stripe.android.model.DelicateCardDetailsApi
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.payments.paymentlauncher.PaymentLauncher
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.payments.paymentlauncher.rememberPaymentLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val CARD_VERIFICATION_CANCELLED_MESSAGE = "Card verification was cancelled."

internal class PendingCardFundingSetup(
    val setupIntentId: String,
    val replacingPaymentMethodId: String?,
    val onFinished: (PaymentMethodActionResult) -> Unit
)

class CardFundingSetupFlow internal constructor(
    private val viewModel: PaymentsViewModel,
    private val scope: CoroutineScope,
    private val configurationError: String?,
    private val pending: MutableState<PendingCardFundingSetup?>
) {
    internal var launcher: PaymentLauncher? = null

    val isAwaitingVerification: Boolean
        get() = pending.value != null

    /**
     * Links a card through a Stripe SetupIntent (with 3-D Secure when the bank asks for it).
     * [onLegacyFallback] runs only when the SetupIntent callables are not deployed yet.
     */
    @OptIn(DelicateCardDetailsApi::class)
    fun start(
        cardParams: CardParams,
        cardholderName: String,
        onLegacyFallback: () -> Unit,
        onFinished: (PaymentMethodActionResult) -> Unit
    ) {
        val paymentLauncher = launcher
        if (paymentLauncher == null) {
            onFinished(
                PaymentMethodActionResult(
                    success = false,
                    message = configurationError ?: "Stripe is not configured for card payments yet."
                )
            )
            return
        }
        scope.launch {
            val setupIntent = try {
                viewModel.createCardFundingSetupIntent()
            } catch (e: Exception) {
                if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                    onLegacyFallback()
                } else {
                    onFinished(
                        PaymentMethodActionResult(
                            success = false,
                            message = e.message?.takeIf { it.isNotBlank() } ?: "Could not initialize card verification."
                        )
                    )
                }
                return@launch
            }
            cardParams.name = cardholderName.trim()
            val last4 = cardParams.number?.takeLast(4).orEmpty()
            pending.value = PendingCardFundingSetup(
                setupIntentId = setupIntent.setupIntentId,
                replacingPaymentMethodId = viewModel.cardRelinkCandidateId(
                    last4 = last4,
                    expMonth = cardParams.expMonth,
                    expYear = cardParams.expYear
                ),
                onFinished = onFinished
            )
            paymentLauncher.confirm(
                ConfirmSetupIntentParams.create(
                    PaymentMethodCreateParams.createCard(cardParams),
                    setupIntent.clientSecret
                )
            )
        }
    }

    internal fun onResult(result: PaymentResult) {
        val request = pending.value ?: return
        pending.value = null
        when (result) {
            is PaymentResult.Completed -> scope.launch {
                request.onFinished(
                    viewModel.saveCardFundingMethod(
                        setupIntentId = request.setupIntentId,
                        isDefault = false,
                        replacingPaymentMethodId = request.replacingPaymentMethodId
                    )
                )
            }
            is PaymentResult.Canceled -> request.onFinished(
                PaymentMethodActionResult(success = false, message = CARD_VERIFICATION_CANCELLED_MESSAGE)
            )
            is PaymentResult.Failed -> request.onFinished(
                PaymentMethodActionResult(
                    success = false,
                    message = result.throwable.message?.takeIf { it.isNotBlank() } ?: "Card verification failed."
                )
            )
        }
    }
}

@Composable
fun rememberCardFundingSetupFlow(viewModel: PaymentsViewModel): CardFundingSetupFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val publishableKeyResult = remember { runCatching { ensureStripePaymentConfiguration(context) } }
    val pending = remember { mutableStateOf<PendingCardFundingSetup?>(null) }
    val flow = remember(viewModel, scope) {
        CardFundingSetupFlow(
            viewModel = viewModel,
            scope = scope,
            configurationError = publishableKeyResult.exceptionOrNull()?.message,
            pending = pending
        )
    }
    val publishableKey = publishableKeyResult.getOrNull()
    if (publishableKey != null) {
        flow.launcher = rememberPaymentLauncher(
            publishableKey = publishableKey,
            stripeAccountId = null,
            callback = object : PaymentLauncher.PaymentResultCallback {
                override fun onPaymentResult(paymentResult: PaymentResult) {
                    flow.onResult(paymentResult)
                }
            }
        )
    }
    return flow
}
