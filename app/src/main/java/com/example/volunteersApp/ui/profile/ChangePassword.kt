package com.example.volunteersApp.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.general.LoginActivity // Make sure LoginActivity is imported
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

// Class name is already in PascalCase, which is correct.
class ChangePassword : ComponentActivity() {

    // Use the idiomatic Kotlin property delegate for ViewModel initialization.
    private val viewModel: ChangePasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the standard, clean trailing lambda syntax for setContent.
        setContent {
            VolunteersAppTheme {
                ChangePasswordScreen(
                    onNavigateUp = { finish() },
                    onSuccess = {
                        Toast.makeText(
                            this,
                            "Password changed. Please log in again.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Navigate to login screen after successful password change and sign out.
                        val intent = Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}
