package com.example.volunteersApp.chat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class ChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This line allows composables to control the window insets (like for the keyboard).
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val chatId = intent.getStringExtra("CHAT_ID")
        val otherUserId = intent.getStringExtra("OTHER_USER_ID")

        if (chatId == null || otherUserId == null) {
            Toast.makeText(this, "Could not open chat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Create the ViewModel using a factory
        val viewModel: ChatViewModel by viewModels {
            ChatViewModelFactory(chatId)
        }

        setContent {
            VolunteersAppTheme {
                ChatScreen(
                    chatId = chatId, // The fix is here
                    viewModel = viewModel, // Pass the viewModel to the screen
                    otherUserId = otherUserId,
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}

// Factory to create the ChatViewModel with the chatId
class ChatViewModelFactory(private val chatId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
