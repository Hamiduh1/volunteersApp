package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized SignUpActivity.
 * Hosts the [SignUpScreen] and handles navigation to the Login screen upon successful registration.
 */
class SignUpActivity : ComponentActivity() {

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
                        // After successful sign up, redirect to Login
                        val intent = Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}
