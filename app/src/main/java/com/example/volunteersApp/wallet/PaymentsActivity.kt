package com.example.volunteersApp.wallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class PaymentsActivity : ComponentActivity() {
    private val viewModel: PaymentsViewModel by viewModels()
    private var stripeConnectCallback by mutableStateOf<StripeConnectOnboardingCallback?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStripeConnectCallback(intent)

        setContent {
            VolunteersAppTheme {
                PaymentMethodsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    stripeConnectCallback = stripeConnectCallback,
                    onStripeConnectCallbackHandled = { stripeConnectCallback = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStripeConnectCallback(intent)
    }

    private fun handleStripeConnectCallback(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        if (uri.scheme != "volunteersapp" || uri.host != "payments") return
        if (uri.pathSegments.firstOrNull() != "stripe-connect") return

        stripeConnectCallback = when (uri.pathSegments.getOrNull(1)) {
            "complete" -> StripeConnectOnboardingCallback.COMPLETED
            "refresh" -> StripeConnectOnboardingCallback.REFRESH_REQUIRED
            else -> null
        }
    }
}
