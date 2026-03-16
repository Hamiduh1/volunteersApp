package com.example.volunteersApp.jokes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    authorId: String,
    viewModel: JokesViewModel,
    onNavigateUp: () -> Unit
) {
    // We will filter the main jokes list to get posts only from this author
    val uiState by viewModel.uiState.collectAsState()
    val userJokes = uiState.jokes.filter { it.authorId == authorId }

    val pagerState = rememberPagerState(pageCount = { userJokes.size })

    Scaffold(
        containerColor = Color.Black, // TikTok-style background
        topBar = {
            TopAppBar(
                title = {
                    // Get the author's name from the first joke, if available
                    val authorName = userJokes.firstOrNull()?.authorName ?: "User Profile"
                    Text(authorName, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (userJokes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("This user has no posts yet.", color = Color.White)
            }
        } else {
            // A VerticalPager is like a vertical TikTok feed
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding),
                key = { index -> userJokes[index].id } // Use the unique ID as the key
            ) { page ->
                val joke = userJokes[page]
                // We'll create a special version of the card for this full-screen view
                FullScreenJokeView(
                    joke = joke,
                    viewModel = viewModel
                )
            }
        }
    }
}
