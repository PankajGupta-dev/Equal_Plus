package com.bridgefy.app.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.mesh.MeshStats
import com.bridgefy.app.ui.theme.*

/**
 * Screen showing detailed mesh network statistics and topology information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshStatsScreen(
    stats: MeshStats,
    deviceId: String,
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        containerColor = MeshNavy,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mesh Statistics",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeshNavy
                )
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
            // Device Identity Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeshNavySurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "This Device",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshBlueBright,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Network Health
            Text(
                text = "Network Health",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.People,
                    value = "${stats.totalPeers}",
                    label = "Total Peers",
                    color = MeshBlue
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Wifi,
                    value = "${stats.activePeers}",
                    label = "Connected",
                    color = MeshGreen
                )
            }

            // Message Stats
            Text(
                text = "Message Statistics",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Send,
                    value = "${stats.messagesSent}",
                    label = "Sent",
                    color = MeshBlueBright
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DoneAll,
                    value = "${stats.messagesDelivered}",
                    label = "Delivered",
                    color = StatusDelivered
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    value = "${stats.pendingMessages}",
                    label = "Pending",
                    color = StatusSending
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Fingerprint,
                    value = "${stats.seenMessageCount}",
                    label = "Dedup Cache",
                    color = BleBadge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
