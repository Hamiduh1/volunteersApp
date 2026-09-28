package com.example.volunteersApp.ui.main

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.volunteersApp.wallet.WalletSecurityService
import com.example.volunteersApp.wallet.WalletSecurityGateScreen

object WalletSecurityRoutes {
    private const val GATE_BASE = "wallet_security_gate"
    const val GATE_PATTERN = "wallet_security_gate/{target}"

    const val ENTRY_WALLET = "wallet"
    const val ENTRY_PAYMENTS = "payments"
    const val ENTRY_TRANSACT = "transact"
    const val ENTRY_HISTORY = "transaction_history"
    const val ENTRY_OPERATIONS_PATTERN = "wallet_operations/{mode}"

    const val CONTENT_WALLET = "wallet_home_content"
    const val CONTENT_PAYMENTS = "wallet_payments_content"
    const val CONTENT_TRANSACT = "wallet_transact_content"
    const val CONTENT_HISTORY = "wallet_history_content"
    const val CONTENT_OPERATIONS_PATTERN = "wallet_operations_content/{mode}"

    fun walletOperationsContent(mode: String): String = "wallet_operations_content/$mode"

    fun gateRouteForTarget(targetRoute: String): String = "$GATE_BASE/${Uri.encode(targetRoute)}"

    fun decodeTargetRoute(encoded: String?): String = Uri.decode(encoded.orEmpty())

    fun normalizeTargetRoute(targetRoute: String?): String {
        val candidate = targetRoute?.trim().orEmpty()
        return when {
            candidate == CONTENT_WALLET -> CONTENT_WALLET
            candidate == CONTENT_PAYMENTS -> CONTENT_PAYMENTS
            candidate == CONTENT_TRANSACT -> CONTENT_TRANSACT
            candidate == CONTENT_HISTORY -> CONTENT_HISTORY
            candidate.startsWith("wallet_operations_content/") -> candidate
            else -> CONTENT_WALLET
        }
    }

    fun isWalletFamilyRoute(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        return route == ENTRY_WALLET ||
            route == ENTRY_PAYMENTS ||
            route == ENTRY_TRANSACT ||
            route == ENTRY_HISTORY ||
            route.startsWith("wallet_operations/") ||
            route == CONTENT_WALLET ||
            route == CONTENT_PAYMENTS ||
            route == CONTENT_TRANSACT ||
            route == CONTENT_HISTORY ||
            route.startsWith("wallet_operations_content/") ||
            route.startsWith("wallet_security_gate/") ||
            route == GATE_PATTERN
    }
}

@Composable
fun WalletEntryRedirect(
    navController: NavController,
    targetRoute: String
) {
    val context = LocalContext.current

    LaunchedEffect(targetRoute) {
        val normalizedTarget = WalletSecurityRoutes.normalizeTargetRoute(targetRoute)
        val destination = if (WalletSecurityService.isWalletSessionValid(context)) {
            normalizedTarget
        } else {
            WalletSecurityRoutes.gateRouteForTarget(normalizedTarget)
        }
        navController.navigateReplacingCurrent(destination)
    }

    WalletSecurityLoadingScreen()
}

@Composable
fun WalletSessionGuard(
    navController: NavController,
    targetRoute: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var authorized by remember { mutableStateOf(WalletSecurityService.isWalletSessionValid(context)) }

    fun enforce() {
        if (WalletSecurityService.isWalletSessionValid(context)) {
            authorized = true
            return
        }

        authorized = false
        val normalizedTarget = WalletSecurityRoutes.normalizeTargetRoute(targetRoute)
        navController.navigateReplacingCurrent(WalletSecurityRoutes.gateRouteForTarget(normalizedTarget))
    }

    LaunchedEffect(targetRoute) {
        enforce()
    }

    DisposableEffect(lifecycleOwner, targetRoute) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enforce()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (authorized) {
        content()
    } else {
        WalletSecurityLoadingScreen()
    }
}

private fun NavController.navigateReplacingCurrent(route: String) {
    val currentId = currentBackStackEntry?.destination?.id
    navigate(route) {
        launchSingleTop = true
        if (currentId != null) {
            popUpTo(currentId) {
                inclusive = true
            }
        }
    }
}

@Composable
private fun WalletSecurityLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Inline PIN/biometric gate for hosts that do not use [WalletSecurityRoutes]
 * (Admin hub, Organizer global wallet). Keeps txn-only + full wallet behind the same session rules.
 */
@Composable
fun RequireWalletUnlocked(
    onBack: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var authorized by remember {
        mutableStateOf(WalletSecurityService.isWalletSessionValid(context))
    }

    fun refreshAuth() {
        authorized = WalletSecurityService.isWalletSessionValid(context)
    }

    LaunchedEffect(Unit) {
        refreshAuth()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAuth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (authorized) {
        content()
    } else {
        WalletSecurityGateScreen(
            targetRoute = "inline_wallet",
            onBack = onBack,
            onUnlocked = {
                authorized = true
            }
        )
    }
}
