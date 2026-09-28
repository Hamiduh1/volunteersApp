package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized SignUpActivity.
 * Hosts the [SignUpScreen] and handles navigation to the Login screen upon successful registration.
 */
class SignUpActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHOW_VERIFY_HINT = "extra_show_verify_hint"
    }

    private val viewModel: SignUpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolunteersAppTheme {
                SignUpScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        finish()
                    },
                    onSignUpSuccess = {
                        Toast.makeText(
                            this,
                            "Account created. Verify with email code or phone OTP to continue.",
                            Toast.LENGTH_LONG
                        ).show()
                        // After successful sign up, redirect to code verification screen.
                        val intent = Intent(this, EmailVerificationActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}
