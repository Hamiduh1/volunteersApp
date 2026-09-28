package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Shared light palette for wallet hub + send-money flows (accessible contrast).
val WalletBackground = Color(0xFFF7F9FC)
val WalletSurface = Color(0xFFFFFFFF)
val WalletTextPrimary = Color(0xFF172033)
val WalletTextSecondary = Color(0xFF667085)
val WalletCardBorder = Color(0xFFE2E8F0)
val WalletSelectedBorder = Color(0xFF2563EB)
val WalletAccent = Color(0xFF2563EB)
val WalletAccentContainer = Color(0xFFEFF6FF)
val WalletActionGold = Color(0xFFF4C542)
val WalletActionGoldText = Color(0xFF3D2E00)
val WalletSuccess = Color(0xFF2E7D32)

internal val SendMoneyBackground = WalletBackground
internal val SendMoneySurface = WalletSurface
internal val SendMoneyTextPrimary = WalletTextPrimary
internal val SendMoneyTextSecondary = WalletTextSecondary
internal val SendMoneyCardBorder = WalletCardBorder
internal val SendMoneySelectedBorder = WalletSelectedBorder
internal val SendMoneyAccent = WalletAccent
internal val SendMoneyAccentContainer = WalletAccentContainer
internal val SendMoneyActionGold = WalletActionGold
internal val SendMoneyActionGoldText = WalletActionGoldText
internal val MobileMoneyAccent = Color(0xFF087F5B)
internal val MobileMoneyAccentContainer = Color(0xFFE7F7F0)
internal val MobileMoneyStrongContainer = Color(0xFFCEF0E1)
internal val MobileMoneyText = Color(0xFF075C43)
internal val BankAccent = Color(0xFFA16207)
internal val BankAccentContainer = Color(0xFFFFF8EC)
internal val BankStrongContainer = Color(0xFFFEEDED)
internal val BankText = Color(0xFF713F12)

internal val SendMoneyColorScheme = lightColorScheme(
    primary = WalletAccent,
    onPrimary = Color.White,
    primaryContainer = WalletAccentContainer,
    onPrimaryContainer = WalletTextPrimary,
    background = WalletBackground,
    onBackground = WalletTextPrimary,
    surface = WalletSurface,
    onSurface = WalletTextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = WalletTextSecondary,
    outline = WalletCardBorder,
    outlineVariant = WalletCardBorder,
    secondaryContainer = WalletAccentContainer,
    onSecondaryContainer = WalletTextPrimary,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun walletOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WalletTextPrimary,
    unfocusedTextColor = WalletTextPrimary,
    focusedLabelColor = WalletTextSecondary,
    unfocusedLabelColor = WalletTextSecondary,
    focusedContainerColor = WalletSurface,
    unfocusedContainerColor = WalletSurface,
    disabledContainerColor = WalletSurface,
    errorContainerColor = WalletSurface,
    focusedPlaceholderColor = WalletTextSecondary,
    unfocusedPlaceholderColor = WalletTextSecondary,
    focusedBorderColor = WalletSelectedBorder,
    unfocusedBorderColor = WalletCardBorder,
    cursorColor = WalletTextPrimary,
    focusedTrailingIconColor = WalletTextPrimary,
    unfocusedTrailingIconColor = WalletTextSecondary,
)

@Composable
internal fun sendMoneyOutlinedFieldColors() = walletOutlinedFieldColors()

@Composable
internal fun mobileMoneyOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WalletTextPrimary,
    unfocusedTextColor = WalletTextPrimary,
    focusedLabelColor = MobileMoneyAccent,
    unfocusedLabelColor = WalletTextSecondary,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    errorContainerColor = Color.White,
    focusedPlaceholderColor = WalletTextSecondary,
    unfocusedPlaceholderColor = WalletTextSecondary,
    focusedBorderColor = MobileMoneyAccent,
    unfocusedBorderColor = WalletCardBorder,
    cursorColor = MobileMoneyAccent,
    focusedLeadingIconColor = MobileMoneyAccent,
    unfocusedLeadingIconColor = WalletTextSecondary,
    focusedTrailingIconColor = MobileMoneyAccent,
    unfocusedTrailingIconColor = WalletTextSecondary,
)

@Composable
internal fun bankOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WalletTextPrimary,
    unfocusedTextColor = WalletTextPrimary,
    focusedLabelColor = BankAccent,
    unfocusedLabelColor = WalletTextSecondary,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    errorContainerColor = Color.White,
    focusedPlaceholderColor = WalletTextSecondary,
    unfocusedPlaceholderColor = WalletTextSecondary,
    focusedBorderColor = BankAccent,
    unfocusedBorderColor = WalletCardBorder,
    cursorColor = BankAccent,
    focusedLeadingIconColor = BankAccent,
    unfocusedLeadingIconColor = WalletTextSecondary,
    focusedTrailingIconColor = BankAccent,
    unfocusedTrailingIconColor = WalletTextSecondary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSendMoneyBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // Long verification and receipt content must have a bounded viewport so
        // their own scroll containers can reach every field and action.
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.94f),
        containerColor = WalletSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendMoneyBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    WalletSendMoneyBottomSheet(onDismissRequest = onDismissRequest) {
        content()
    }
}
