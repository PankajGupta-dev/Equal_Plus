package com.bridgefy.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.model.ConnectionType
import com.bridgefy.app.model.NearbyUser
import com.bridgefy.app.model.UserStatus
import com.bridgefy.app.ui.theme.*

/**
 * WhatsApp-style user card for the Nearby Users screen.
 *
 * Displays:
 * - Avatar with first letter of username and gradient background
 * - Connection status dot (green=connected, amber=connecting, gray=nearby)
 * - Username (never a MAC address)
 * - Status text ("Nearby" / "Connected" / "Connecting...")
 * - Signal strength bars (BLE only)
 * - Transport badge ("BLE" / "WiFi Direct")
 * - Subtle press animation
 */
@Composable
fun UserCard(
    user: NearbyUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with connection status dot
            UserAvatar(user = user)

            Spacer(modifier = Modifier.width(14.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                // Username
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Short device ID for mesh debugging
                Text(
                    text = "ID: ${user.id.take(7)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Status text
                StatusText(status = user.status)

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Transport badge
                    TransportBadge(type = user.connectionType)

                    // Signal strength for BLE
                    user.rssi?.let { rssi ->
                        Spacer(modifier = Modifier.width(8.dp))
                        SignalIndicator(rssi)
                    }
                }
            }

            // Right side: last seen + chevron
            Column(horizontalAlignment = Alignment.End) {
                val elapsed = System.currentTimeMillis() - user.lastSeen
                val timeText = when {
                    elapsed < 60_000 -> "Now"
                    elapsed < 3_600_000 -> "${elapsed / 60_000}m ago"
                    else -> "${elapsed / 3_600_000}h ago"
                }

                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(4.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open chat",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * User avatar: circular with gradient background, first letter of name, and status dot.
 */
@Composable
private fun UserAvatar(user: NearbyUser) {
    val (statusColor, statusLabel) = when (user.status) {
        UserStatus.CONNECTED -> MeshGreen to "Connected"
        UserStatus.CONNECTING -> StatusSending to "Connecting"
        UserStatus.NEARBY -> StatusPending to "Nearby"
    }

    // Animate the connecting status dot
    val infiniteTransition = rememberInfiniteTransition(label = "connectingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val dotColor = if (user.status == UserStatus.CONNECTING) {
        statusColor.copy(alpha = pulseAlpha)
    } else {
        statusColor
    }

    Box {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = when (user.status) {
                            UserStatus.CONNECTED -> listOf(MeshGreenDim, MeshGreen)
                            UserStatus.CONNECTING -> listOf(MeshBlue, MeshBlueBright)
                            UserStatus.NEARBY -> listOf(MeshBlue, MeshBlueBright)
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.take(1).uppercase(),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Connection status dot
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(MeshNavy)
                .padding(2.dp)
                .clip(CircleShape)
                .background(dotColor)
                .align(Alignment.BottomEnd)
        )
    }
}

/**
 * Status text with appropriate coloring.
 */
@Composable
private fun StatusText(status: UserStatus) {
    val (text, color) = when (status) {
        UserStatus.CONNECTED -> "Connected" to MeshGreen
        UserStatus.CONNECTING -> "Connecting..." to StatusSending
        UserStatus.NEARBY -> "Nearby" to TextSecondary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = if (status == UserStatus.CONNECTED) FontWeight.Medium else FontWeight.Normal
    )
}

/**
 * Transport badge pill showing "BLE" or "WiFi Direct".
 */
@Composable
fun TransportBadge(type: ConnectionType) {
    val (color, text) = when (type) {
        ConnectionType.BLE -> BleBadge to "BLE"
        ConnectionType.WIFI_DIRECT -> WiFiBadge to "WiFi Direct"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Legacy transport badge for MeshPeer (backward compatibility).
 */
@Composable
fun TransportBadge(type: com.bridgefy.app.model.TransportType) {
    val (color, text) = when (type) {
        com.bridgefy.app.model.TransportType.BLE -> BleBadge to "BLE"
        com.bridgefy.app.model.TransportType.WIFI_DIRECT -> WiFiBadge to "WiFi"
        com.bridgefy.app.model.TransportType.BOTH -> BothBadge to "Both"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Signal strength bar indicator based on RSSI.
 */
@Composable
fun SignalIndicator(rssi: Int) {
    val bars = when {
        rssi > -50 -> 4
        rssi > -65 -> 3
        rssi > -80 -> 2
        else -> 1
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (i <= bars) MeshGreen else TextMuted.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

// ─── Legacy PeerCard (kept for backward compat if needed) ────

/**
 * Legacy card component for raw MeshPeer display.
 * Use [UserCard] with [NearbyUser] for the new UI.
 */
@Composable
fun PeerCard(
    peer: com.bridgefy.app.model.MeshPeer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MeshBlue, MeshBlueBright)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peer.displayName.take(2).uppercase(),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (peer.isConnected) MeshGreen else StatusPending)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransportBadge(peer.transportType)
                    peer.rssi?.let { rssi ->
                        Spacer(modifier = Modifier.width(8.dp))
                        SignalIndicator(rssi)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val elapsed = System.currentTimeMillis() - peer.lastSeen
                val timeText = when {
                    elapsed < 60_000 -> "Now"
                    elapsed < 3_600_000 -> "${elapsed / 60_000}m ago"
                    else -> "${elapsed / 3_600_000}h ago"
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open chat",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
