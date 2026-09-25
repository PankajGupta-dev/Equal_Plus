package com.bridgefy.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.mesh.MeshStats
import com.bridgefy.app.ui.theme.*

/**
 * Top status bar showing real-time mesh network health.
 * Displays active peers count, message stats, and a health indicator.
 */
@Composable
fun MeshStatusBar(
    stats: MeshStats,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = MeshNavySurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Peers
            StatusChip(
                icon = Icons.Default.People,
                label = "${stats.activePeers}",
                sublabel = "peers",
                color = if (stats.activePeers > 0) MeshGreen else TextMuted
            )

            // Sent
            StatusChip(
                icon = Icons.Default.Send,
                label = "${stats.messagesSent}",
                sublabel = "sent",
                color = MeshBlueBright
            )

            // Delivered
            StatusChip(
                icon = Icons.Default.DoneAll,
                label = "${stats.messagesDelivered}",
                sublabel = "delivered",
                color = StatusDelivered
            )

            // Pending
            StatusChip(
                icon = Icons.Default.Schedule,
                label = "${stats.pendingMessages}",
                sublabel = "pending",
                color = if (stats.pendingMessages > 0) StatusSending else TextMuted
            )

            // Health dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            stats.activePeers >= 3 -> MeshGreen
                            stats.activePeers >= 1 -> StatusSending
                            else -> StatusFailed
                        }
                    )
            )
        }
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = sublabel,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
