package com.example.volunteersApp.ui.profile
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

// Renamed class to follow Kotlin conventions (PascalCase)
class ChangeUserNameActivity : ComponentActivity() {

    // The idiomatic Kotlin way to initialize a ViewModel using the KTX library.
    private val viewModel: ChangeUserNameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the standard, clean setContent lambda syntax.
        setContent {
            VolunteersAppTheme {
                ChangeUserNameScreen(
                    onNavigateUp = { finish() },
                    onSuccess = {
                        Toast.makeText(this, "User name changed successfully!", Toast.LENGTH_SHORT).show()

                        // Navigate to main activity after success
                        val intent = Intent(this, MainActivity::class.java).apply {
                            // Use apply block for cleaner flag setting
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
