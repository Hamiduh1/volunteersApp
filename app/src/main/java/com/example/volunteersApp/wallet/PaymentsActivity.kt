package com.example.volunteersApp.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class PaymentsActivity : ComponentActivity() {
    private val viewModel: PaymentsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start listening for data immediately
        viewModel.fetchPaymentMethods()

        setContent {
            VolunteersAppTheme {
                PaymentMethodsScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
