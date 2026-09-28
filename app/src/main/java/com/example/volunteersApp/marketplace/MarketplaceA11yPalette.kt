package com.example.volunteersApp.marketplace

import androidx.compose.ui.graphics.Color
import com.example.volunteersApp.wallet.WalletAccent
import com.example.volunteersApp.wallet.WalletAccentContainer
import com.example.volunteersApp.wallet.WalletActionGold
import com.example.volunteersApp.wallet.WalletActionGoldText
import com.example.volunteersApp.wallet.WalletBackground
import com.example.volunteersApp.wallet.WalletCardBorder
import com.example.volunteersApp.wallet.WalletSuccess
import com.example.volunteersApp.wallet.WalletSurface
import com.example.volunteersApp.wallet.WalletTextPrimary
import com.example.volunteersApp.wallet.WalletTextSecondary

/**
 * Marketplace uses the wallet's light, high-contrast system palette.
 * Blue remains the primary action color; labels and icons carry state beyond color alone.
 */
internal object MarketplaceA11yPalette {
  val loopEntryAccent = WalletAccent

  val pageBackgroundTop = WalletBackground
  val pageBackgroundBottom = WalletAccentContainer.copy(alpha = 0.5f)

  val pageGradient = listOf(
    pageBackgroundTop,
    pageBackgroundBottom,
    pageBackgroundTop,
  )

  val cardSurface = WalletSurface
  val cardSurfaceSoft = WalletAccentContainer.copy(alpha = 0.42f)
  val cardBorder = WalletCardBorder
  val cardBorderStrong = WalletAccent.copy(alpha = 0.42f)

  val heroGradientStart = WalletAccent
  val heroGradientEnd = Color(0xFF1D4ED8)

  val accentBlue = WalletAccent
  val accentTeal = WalletAccent
  val accentOrange = WalletActionGold

  val textPrimary = WalletTextPrimary
  val textSecondary = WalletTextSecondary
  val textTertiary = WalletTextSecondary.copy(alpha = 0.85f)

  val priceText = WalletAccent

  val successBannerBackground = WalletSuccess.copy(alpha = 0.12f)
  val successBannerBorder = WalletSuccess.copy(alpha = 0.24f)
  val successBannerIcon = WalletSuccess
  val successBannerText = WalletTextPrimary

  val categoryChipBackground = WalletAccentContainer
  val categoryChipText = WalletAccent
  val categoryChipSelectedBackground = WalletAccentContainer
  val categoryChipSelectedBorder = WalletAccent.copy(alpha = 0.38f)

  val timeChipBackground = Color(0xFFFFF7D6)
  val timeChipText = WalletActionGoldText
  val timeChipBorder = WalletActionGold.copy(alpha = 0.32f)

  val selfBadgeBackground = WalletAccentContainer
  val selfBadgeText = WalletAccent
  val selfBadgeBorder = WalletAccent.copy(alpha = 0.28f)

  val sellerHeaderSurface = WalletAccentContainer.copy(alpha = 0.34f)
  val sellerHeaderBorder = WalletCardBorder

  val chipUnselectedBorder = WalletCardBorder
  val chipUnselectedBackground = Color(0xFFF1F5F9)

  val mediaScrim = WalletTextPrimary.copy(alpha = 0.32f)
  val mediaBadgeBackground = WalletAccent

  val fabGradientStart = WalletAccent
  val fabGradientEnd = Color(0xFF1D4ED8)

  val postButtonGradient = listOf(WalletAccent, Color(0xFF1D4ED8))

  val formBackground = WalletBackground
  val formSectionSurface = WalletSurface
  val formFieldBackground = WalletSurface
  val formFieldBorder = WalletCardBorder
  val formFieldBorderFocused = WalletAccent
}
