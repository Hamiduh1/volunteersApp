package com.example.volunteersApp.ui.profile

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onNavigateUp: () -> Unit,
    viewModel: SupportViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_general_support)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is SupportUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SupportUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error loading support items:\n${state.message}",
                        textAlign = TextAlign.Center
                    )
                }
            }
            is SupportUiState.Success -> {
                if (state.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.no_support_information_available),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        items(state.items, key = { it.text ?: "" }) { item ->
                            SupportListItem(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportListItem(item: SupportItem) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = {
                    Log.d("SupportScreen", "Clicked support item: ${item.text}")
                    Toast.makeText(context, "Clicked: ${item.text}", Toast.LENGTH_SHORT).show()
                }
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconResId = getDrawableResourceIdByName(context, item.iconName)
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.text ?: "No title",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@DrawableRes
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
