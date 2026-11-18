package com.example.volunteersApp.marketplace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Explicit opt-in for publishing a phone number. Listings are readable by every signed-in member, so the
 * account phone is never published unless this is on and the seller typed a number.
 */
@Composable
fun MarketplaceSharePhoneToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = "Show my phone number on this listing",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MarketplaceA11yPalette.textPrimary
            )
            Text(
                text = "Visible to all members until the item sells. Off keeps your number private.",
                style = MaterialTheme.typography.bodySmall,
                color = MarketplaceA11yPalette.textSecondary
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
