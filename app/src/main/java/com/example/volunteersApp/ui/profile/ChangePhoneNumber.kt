package com.example.volunteersApp.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

// Class name is already in PascalCase, which is correct.
class ChangePhoneNumber : ComponentActivity() {

    // Use the idiomatic Kotlin property delegate for ViewModel initialization.
    private val viewModel: ChangePhoneNumberViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the standard, clean trailing lambda syntax for setContent.
        setContent {
            VolunteersAppTheme {
                ChangePhoneNumberScreen(
                    onNavigateUp = { finish() },
                    onSuccess = {
                        Toast.makeText(
                            this,
                            "Phone number changed successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Navigate to main activity after success, using .apply for cleaner code.
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
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
