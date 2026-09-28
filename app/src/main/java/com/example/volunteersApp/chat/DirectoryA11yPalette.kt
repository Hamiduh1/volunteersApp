package com.example.volunteersApp.chat

import androidx.compose.ui.graphics.Color

/**
 * User Directory palette aligned with Social Inbox dashboard chrome.
 */
internal object DirectoryA11yPalette {
    val pageGradient = listOf(
        SocialInboxBackground,
        SocialInboxBackgroundAlt.copy(alpha = 0.38f),
        SocialInboxBackground,
    )

    /** Matches SocialInboxTab.Chats hero gradient. */
    val heroGradientStart = InboxBlue.copy(alpha = 0.13f)
    val heroGradientEnd = InboxTeal.copy(alpha = 0.10f)

    val accent = InboxBlue
    val accentLight = InboxCyan
    val accentTeal = InboxTeal

    val spotlightAccent = InboxBlue
    val spotlightChipBackground = SocialInboxSurface
    val spotlightChipBorder = SocialInboxMutedInk.copy(alpha = 0.10f)

    val textPrimary = SocialInboxInk
    val textSecondary = SocialInboxMutedInk
    val textTertiary = SocialInboxMutedInk.copy(alpha = 0.85f)

    val cardSurface = SocialInboxSurface
    val cardSurfaceSoft = SocialInboxSurfaceSoft
    val cardSurfaceAlt = SocialInboxSurfaceSoftAlt
    val cardBorder = InboxBlue.copy(alpha = 0.16f)
    val cardBorderStrong = InboxBlue.copy(alpha = 0.22f)

    val countBadgeBackground = InboxBlue.copy(alpha = 0.12f)
    val countBadgeText = InboxBlue
    val countBadgeBorder = InboxBlue.copy(alpha = 0.16f)

    val filterChipBackground = SocialInboxNeutralChip
    val filterChipBorder = InboxBlue.copy(alpha = 0.16f)

    val successBannerBackground = InboxGreen.copy(alpha = 0.12f)
    val successBannerBorder = InboxGreen.copy(alpha = 0.18f)
    val successBannerIcon = InboxGreen
    val successBannerText = SocialInboxInk

    val metricTotal = InboxBlue
    val metricVisible = InboxTeal
    val metricBlocked = InboxGray
    val metricInvites = InboxOrange

    val inviteButtonContainer = InboxBlue.copy(alpha = 0.12f)
    val inviteButtonContent = InboxBlue
    val invitedChipBackground = InboxTeal.copy(alpha = 0.12f)
    val invitedChipText = InboxTeal

    val letterSectionBackground = SocialInboxSurfaceSoft
    val letterSectionBorder = SocialInboxMutedInk.copy(alpha = 0.08f)
}
