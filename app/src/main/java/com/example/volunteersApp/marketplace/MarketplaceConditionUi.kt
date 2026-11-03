@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.marketplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Optional condition picker; tapping the selected chip clears it so the listing has no condition. */
@Composable
internal fun MarketplaceConditionPicker(
    selected: MarketplaceCondition?,
    onSelected: (MarketplaceCondition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Condition (optional)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MarketplaceA11yPalette.textPrimary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarketplaceCondition.entries.forEach { condition ->
                val isSelected = condition == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(if (isSelected) null else condition) },
                    label = {
                        Text(
                            text = condition.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MarketplaceA11yPalette.categoryChipSelectedBackground,
                        selectedLabelColor = MarketplaceA11yPalette.categoryChipText,
                        selectedLeadingIconColor = MarketplaceA11yPalette.categoryChipText,
                        containerColor = MarketplaceA11yPalette.chipUnselectedBackground,
                        labelColor = MarketplaceA11yPalette.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MarketplaceA11yPalette.chipUnselectedBorder,
                        selectedBorderColor = MarketplaceA11yPalette.categoryChipSelectedBorder
                    )
                )
            }
        }
    }
}

/** Renders nothing for listings without a condition (pre-condition listings stay badge-free). */
@Composable
internal fun MarketplaceConditionBadge(condition: String?) {
    val resolved = MarketplaceCondition.fromValue(condition) ?: return
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MarketplaceA11yPalette.successBannerBackground,
        border = BorderStroke(1.dp, MarketplaceA11yPalette.successBannerBorder)
    ) {
        Text(
            text = resolved.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MarketplaceA11yPalette.successBannerText
        )
    }
}
