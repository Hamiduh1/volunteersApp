package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class RatingActivity : ComponentActivity() {

    private val viewModel: RatingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolunteersAppTheme {
                RatingScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }
}
