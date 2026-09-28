@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.ui.profile

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

private data class AccountOption(
    val title: String,
    val icon: ImageVector,
    val route: String? = null, // Route for navigation
    val action: (() -> Unit)? = null // Direct action
)

@Composable
private fun rememberAccountOptions(
    viewModel: AccountSettingsViewModel,
    onNavigate: (String) -> Unit
): List<AccountOption> {
    return remember {
        listOf(
            AccountOption("Change Username", Icons.Default.Badge, route = "change_username"),
            AccountOption("Change Password", Icons.Default.Lock, route = "change_password"),
            AccountOption("Change Phone Number", Icons.Default.Phone, route = "change_phone"),
            AccountOption("Remove Profile Picture", Icons.Default.Delete, action = {
                viewModel.removeProfilePicture()
            })
        )
    }
}

@Composable
/**
 * Modern Account Settings Screen with Material 3 design.
 * Features: Account management options, profile settings, security controls, optimized ripple effects.
 */
fun AccountSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: AccountSettingsViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val options = rememberAccountOptions(viewModel, onNavigate)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            val message = when (event) {
                is AccountEvent.Success -> event.message
                is AccountEvent.Error -> event.message
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { AccountSettingsTopAppBar(title = "Account Settings", onNavigateUp = onNavigateUp) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(options) {
                AccountOptionItem(option = it, onNavigate = onNavigate)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun AccountOptionItem(option: AccountOption, onNavigate: (String) -> Unit) {
    var isPressed = remember { mutableStateOf(false) }
    
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed.value) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "optionScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    if (option.route != null) {
                        onNavigate(option.route)
                    } else {
                        option.action?.invoke()
                    }
                },
                onClickLabel = option.title
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null, // Decorative
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = option.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ArrowForwardIos,
            contentDescription = "Go to ${option.title}",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsTopAppBar(
    title: String,
    onNavigateUp: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
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
