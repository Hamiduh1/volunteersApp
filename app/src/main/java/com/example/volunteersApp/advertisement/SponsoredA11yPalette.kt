package com.example.volunteersApp.advertisement

import androidx.compose.ui.graphics.Color
import com.example.volunteersApp.wallet.WalletAccent
import com.example.volunteersApp.wallet.WalletAccentContainer
import com.example.volunteersApp.wallet.WalletActionGold
import com.example.volunteersApp.wallet.WalletActionGoldText
import com.example.volunteersApp.wallet.WalletBackground
import com.example.volunteersApp.wallet.WalletCardBorder
import com.example.volunteersApp.wallet.WalletSurface
import com.example.volunteersApp.wallet.WalletTextPrimary
import com.example.volunteersApp.wallet.WalletTextSecondary

/**
 * Sponsored Loop mirrors the light, high-contrast wallet system. Every state has a
 * label and icon, so the blue/gold distinction is never the only way to understand it.
 */
internal object SponsoredA11yPalette {
  /** Community Loop shortcut chip (home horizontal rail). */
  val loopEntryAccent = WalletAccent

  val pageBackground = WalletBackground

  val pageGradient = listOf(
    WalletBackground,
    WalletAccentContainer.copy(alpha = 0.62f),
    WalletBackground,
  )

  /** Kept subtle so content remains readable in bright outdoor conditions. */
  val heroGradient = listOf(
    WalletAccentContainer,
    WalletActionGold.copy(alpha = 0.22f),
  )

  val adsSectionGradient = listOf(
    WalletAccentContainer,
    WalletSurface,
  )

  val garageSectionGradient = listOf(
    WalletActionGold.copy(alpha = 0.20f),
    WalletSurface,
  )

  val accentAds = WalletAccent
  val accentAdsLight = WalletAccentContainer
  val accentGarage = WalletActionGoldText
  val accentGarageLight = WalletActionGold.copy(alpha = 0.24f)

  val textPrimary = WalletTextPrimary
  val textSecondary = WalletTextSecondary
  val textTertiary = WalletTextSecondary.copy(alpha = 0.88f)

  val cardSurface = WalletSurface
  val cardSurfaceSoft = WalletAccentContainer.copy(alpha = 0.48f)
  val cardBorder = WalletCardBorder
  val cardBorderStrong = WalletAccent.copy(alpha = 0.36f)

  val adsChipBackground = WalletAccentContainer
  val adsChipText = WalletAccent
  val adsChipBorder = WalletAccent.copy(alpha = 0.28f)

  val garageChipBackground = WalletActionGold.copy(alpha = 0.24f)
  val garageChipText = WalletActionGoldText
  val garageChipBorder = WalletActionGoldText.copy(alpha = 0.22f)

  val warningBackground = WalletActionGold.copy(alpha = 0.20f)
  val warningBorder = WalletActionGoldText.copy(alpha = 0.26f)
  val warningText = WalletTextPrimary

  val refreshWarningBackground = WalletActionGold.copy(alpha = 0.16f)
  val refreshWarningBorder = WalletActionGoldText.copy(alpha = 0.24f)
  val refreshWarningText = WalletTextPrimary

  val statusBannerBackground = WalletAccentContainer
  val statusBannerBorder = WalletAccent.copy(alpha = 0.30f)
  val statusBannerText = WalletTextPrimary

  val statPillSurface = Color.White.copy(alpha = 0.86f)
  val statPillBorder = WalletCardBorder

  val ownerHeaderSurface = WalletAccentContainer.copy(alpha = 0.50f)
  val ownerHeaderBorder = WalletCardBorder

  val mediaPlaceholderEnd = WalletSurface
  val mediaBadgeBackground = WalletAccent

  val formBackground = WalletBackground
  val formTopBar = WalletSurface
  val formMediaTile = WalletAccentContainer
  val formMediaTileAlt = WalletActionGold.copy(alpha = 0.20f)
  val formMediaHighlight = WalletAccentContainer.copy(alpha = 0.65f)

  val galleryDialogBackground = WalletBackground
  val galleryThumbBackground = WalletAccentContainer.copy(alpha = 0.50f)

  val metricAds = WalletAccent
  val metricGarage = WalletActionGoldText
  val metricStatus = WalletAccent

  val adsButtonGradient = listOf(WalletAccent, WalletAccent)
  val garageButtonGradient = listOf(WalletAccent, WalletAccent)
}
