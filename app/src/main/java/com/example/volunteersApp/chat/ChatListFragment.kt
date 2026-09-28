package com.example.volunteersApp.chat
//remove all the old code from the Fragment and replace
// it with a ComposeView that hosts our new ChatListScreen.
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * This Fragment now acts as a simple host for the Jetpack Compose UI.
 * Its only jobs are to set up the Compose content and handle navigation clicks.
 */
class ChatListFragment : Fragment() {

    private lateinit var viewModel: ChatListViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize the ViewModel, scoped to the parent fragment.
        viewModel = ViewModelProvider(requireParentFragment())[ChatListViewModel::class.java]

        // Create a ComposeView to host our UI.
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the Fragment's view is destroyed to prevent memory leaks.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Set the content of the view to our new composable screen.
            setContent {
                VolunteersAppTheme {
                    ChatListScreen(
                        viewModel = viewModel,
                        onConversationClick = { chatId, otherUserId ->
                            // Handle navigation when a conversation is clicked.
                            val action = ChatInboxFragmentDirections.actionChatInboxFragmentToChatActivity(
                                chatId,
                                otherUserId
                            )
                            findNavController().navigate(action)
                        },
                        onAudioCall = { chatId, otherUserId ->
                            // Start call via Activity intent (no nav action needed)
                            requireContext().startActivity(
                                CallActivity.newIntent(
                                    requireContext(),
                                    chatId,
                                    otherUserId,
                                    CallType.AUDIO
                                )
                            )
                        },
                        onVideoCall = { chatId, otherUserId ->
                            // Start call via Activity intent (no nav action needed)
                            requireContext().startActivity(
                                CallActivity.newIntent(
                                    requireContext(),
                                    chatId,
                                    otherUserId,
                                    CallType.VIDEO
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The ViewModel is now initialized in onCreateView and automatically starts listening.
        // If you ever need to refresh, you could add a public method to the ViewModel.
    }
}
