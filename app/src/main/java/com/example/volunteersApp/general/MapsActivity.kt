package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Entry point Activity for the location selection flow.
 * Passes the EventId to the Compose-based MapsScreen.
 */
class MapsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract EventId passed from HostEventFormFragment or CreateEventActivity
        val eventId = intent.getStringExtra("EventId") ?: run {
            // Fallback or finish if ID is missing
            finish()
            return
        }

        setContent {
            VolunteersAppTheme {
                MapsScreen(
                    eventId = eventId,
                    onBack = { finish() },
                    onSuccess = {
                        // After saving location, navigate to completion screen
                        // Depending on your architecture, this might be a Fragment or Activity.
                        // Here we use the established flow to HostFinal.
                        val intent = Intent(this, MapsActivity::class.java) // Adjust if HostFinal is now a Fragment
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}
