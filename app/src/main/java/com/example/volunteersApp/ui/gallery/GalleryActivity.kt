package com.example.volunteersApp.ui.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class GalleryActivity : ComponentActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolunteersAppTheme {
                GalleryScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
