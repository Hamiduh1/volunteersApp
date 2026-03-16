package com.example.volunteersApp.general

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized ForgotPasswordActivity.
 * Hosts the [ForgotPasswordScreen] and handles the navigation back to login.
 */
class ForgotPasswordActivity : ComponentActivity() {

    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolunteersAppTheme {
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBack = {
                        finish()
                    },
                    onSuccess = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        // Optional: finish() if you want to return to login immediately after success
                        // finish()
                    }
                )
            }
        }
    }
}
