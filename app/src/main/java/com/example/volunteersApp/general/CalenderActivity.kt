package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class CalenderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentDate = intent.getStringExtra("current_date") ?: ""

        setContent {
            VolunteersAppTheme {
                CalenderScreen(
                    initialDate = currentDate,
                    onDateSelected = { selectedDate ->
                        val resultIntent = Intent().apply {
                            putExtra("selected_date", selectedDate)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}