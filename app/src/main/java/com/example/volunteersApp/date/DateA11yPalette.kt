package com.example.volunteersApp.date

import androidx.compose.ui.graphics.Color

/** Dating Hub / Discover palette — high contrast, color-blind-friendly blue + orange. */
internal object DateA11yPalette {
    val pageGradient = listOf(
        Color(0xFFF4F7FC),
        Color(0xFFE3ECF8),
        Color(0xFFF8FAFD)
    )

    val heroGradient = listOf(
        Color(0xFFE4EEFC),
        Color(0xFFFFF4E6)
    )

    val accentDiscover = Color(0xFF1A4488)
    val accentDiscoverLight = Color(0xFF3A6BB5)
    val accentBlindDate = Color(0xFFB45309)
    val accentBlindDateLight = Color(0xFFD97706)

    val textPrimary = Color(0xFF0E1A2B)
    val textSecondary = Color(0xFF2E4058)
    val textTertiary = Color(0xFF4A5F78)

    val cardSurface = Color(0xFFFFFFFF)
    val cardSurfaceSoft = Color(0xFFEEF3FA)
    val cardBorder = Color(0xFF9AADCA)
    val cardBorderStrong = Color(0xFF5C7394)

    val chipBackground = Color(0xFFE8F0FC)
    val chipText = Color(0xFF14325E)
    val chipBorder = Color(0xFF1A4488)

    val photoPlaceholder = Color(0xFFDCE6F4)
    val mediaBadge = Color(0xFF152E4D).copy(alpha = 0.88f)

    val successBannerBackground = Color(0xFFE4EEFC)
    val successBannerBorder = Color(0xFF1A4488)
    val successBannerText = Color(0xFF102A52)
}
