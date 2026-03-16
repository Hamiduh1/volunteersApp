package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernized Owner Dashboard.
 * Displays combined revenue from 1% P2P fees and 3% Forex Hidden Margins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    viewModel: SystemRevenueViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Command Center", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- 1. Combined Revenue Card (Gradient) ---
            val heroGradient = Brush.linearGradient(
                colors = listOf(Color(0xFF6200EE), Color(0xFF03DAC5))
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(heroGradient)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TOTAL ACCUMULATED REVENUE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "$${String.format("%,.2f", uiState.totalCollected)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 42.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    // Breakdown Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RevenueMiniStat("1% P2P Fees", "$${String.format("%.2f", uiState.totalCollected * 0.3)}") // Example split
                        VerticalDivider(modifier = Modifier.height(24.dp), color = Color.White.copy(alpha = 0.3f))
                        RevenueMiniStat("3% Forex Spread", "$${String.format("%.2f", uiState.totalCollected * 0.7)}")
                    }
                }
            }

            // --- 2. Live Analytics ---
            Text("Activity Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatSmallCard(
                    label = "Total Payouts",
                    value = uiState.transactionCount.toString(),
                    icon = Icons.Default.Public,
                    modifier = Modifier.weight(1f)
                )

                val lastUpdate = uiState.lastTransactionAt?.toDate()?.let {
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                } ?: "No data"

                StatSmallCard(
                    label = "Last Sync",
                    value = lastUpdate,
                    icon = Icons.Default.Sync,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- 3. Country Performance Preview ---
            Text("Global Reach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PerformanceRow("Uganda (UGX)", "MTN / Airtel", Color(0xFFFBBC05))
                    PerformanceRow("Kenya (KES)", "M-Pesa", Color(0xFF34A853))
                    PerformanceRow("Nigeria (NGN)", "Bank / Card", Color(0xFF4285F4))
                }
            }

            // Information Footer
            Text(
                text = "This dashboard tracks real-time profit from softsolutionstech.com payment gateways.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun RevenueMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun PerformanceRow(country: String, gateway: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(country, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(gateway, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun StatSmallCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}
