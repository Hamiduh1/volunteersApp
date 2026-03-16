package com.example.volunteersApp.events

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.example.volunteersApp.events.EditEventScreen
import com.example.volunteersApp.events.EditEventViewModel
import com.example.volunteersApp.models.EventModel




class EditEventActivity : ComponentActivity() {

    private val viewModel: EditEventViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val eventId = intent.getStringExtra(EXTRA_EDIT_EVENT_ID) ?: run {
            finish()
            return
        }

        setContent {
            VolunteersAppTheme {
                EditEventScreen(
                    eventId = eventId,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_EDIT_EVENT_ID = "edit_event_id"
    }
}

