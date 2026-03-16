package com.example.volunteersApp.ui.profile

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSupportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProfileSupportViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SupportUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SupportUiState.Error -> {
                    Text(
                        text = state.message,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                is SupportUiState.Success -> {
                    if (state.items.isEmpty()) {
                        Text(
                            text = "Support options are not available.",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.items, key = { it.text ?: "" }) { item ->
                                ProfileSupportItem(item = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSupportItem(item: SupportItem) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = {
                    Log.d("ProfileSupport", "Clicked on: ${item.text}")
                    Toast.makeText(context, "Clicked: ${item.text}", Toast.LENGTH_SHORT).show()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconResId = getDrawableResourceIdByName(context, item.iconName)
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = item.text,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.text ?: "No Title",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun getDrawableResourceIdByName(context: Context, resourceName: String?): Int {
    if (resourceName.isNullOrEmpty()) {
        return R.drawable.ic_default_placeholder
    }
    return try {
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        if (resourceId == 0) R.drawable.ic_default_placeholder else resourceId
    } catch (e: Exception) {
        Log.e("ResourceResolver", "Failed to get resource ID for: $resourceName", e)
        R.drawable.ic_default_placeholder
    }
}
