package com.example.volunteersApp.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class UserDirectoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            VolunteersAppTheme {
                UserDirectoryScreen()
            }
        }
    }
}
