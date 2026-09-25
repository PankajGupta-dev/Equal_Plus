package com.bridgefy.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.ui.theme.*
import com.bridgefy.app.ui.viewmodel.LocationPrivacyViewModel

/**
 * Privacy settings screen for location features.
 *
 * Two toggles:
 * - Show my location on my local map (default: true)
 * - Broadcast location to mesh peers (default: false, opt-in)
 *
 * Always-visible notice explaining relay behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPrivacySettingsScreen(
    viewModel: LocationPrivacyViewModel,
    onBack: () -> Unit
) {
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val isLocalMapEnabled by viewModel.isLocalMapEnabled.collectAsState()

    Scaffold(
        containerColor = MeshNavy,
        topBar = {
            TopAppBar(
                title = {
                    Text("Location Privacy", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeshNavyLight)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local map toggle
            PrivacyToggleCard(
                icon = Icons.Default.MyLocation,
                title = "Show My Location on Map",
                description = "Displays your GPS position on your local map. This data never leaves your device.",
                isEnabled = isLocalMapEnabled,
                onToggle = { viewModel.setLocalMapLocation(it) }
            )

            // Mesh broadcast toggle
            PrivacyToggleCard(
                icon = Icons.Default.CellTower,
                title = "Broadcast Location to Mesh",
                description = "Shares your GPS coordinates with nearby peers so they can see you on their map. " +
                        "Disabled by default — your location stays private until you opt in.",
                isEnabled = isBroadcasting,
                onToggle = { viewModel.setBroadcasting(it) }
            )

            // Relay notice
            Surface(
                color = MeshBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MeshBlueBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "About Mesh Relay",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshBlueBright,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "When broadcasting is enabled, your location pings are relayed through " +
                            "intermediate peers (up to 3 hops). This means devices between you and the " +
                            "destination may temporarily hold your coordinates in memory. Pings older " +
                            "than 15 minutes are automatically discarded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) MeshBlueBright else TextMuted,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = MeshBlue,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceDivider
                )
            )
        }
    }
}
