package com.bridgefy.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.mesh.DiscoveryState
import com.bridgefy.app.mesh.MeshStats
import com.bridgefy.app.model.ConnectionType
import com.bridgefy.app.model.NearbyUser
import com.bridgefy.app.model.UserStatus
import com.bridgefy.app.ui.components.MeshStatusBar
import com.bridgefy.app.ui.components.UserCard
import com.bridgefy.app.ui.theme.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color

/**
 * Main screen showing discovered nearby users and network status.
 *
 * Layout:
 * 1. Top bar: "AlertNet" with hub icon
 * 2. Mesh status bar (peers, messages, etc.)
 * 3. Discovery state banner (animated)
 * 4. Section: "Connected" (if any)
 * 5. Section: "Nearby Users"
 * 6. Empty state when no users found
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(
    connectedUsers: List<NearbyUser>,
    nearbyUsers: List<NearbyUser>,
    meshStats: MeshStats,
    isDiscovering: Boolean,
    discoveryState: DiscoveryState,
    activeSource: ConnectionType?,
    connectingUserId: String? = null,
    connectionError: String? = null,
    onUserClick: (NearbyUser) -> Unit,
    onConnectionErrorDismissed: () -> Unit = {},
    onRefresh: () -> Unit,
    onStatsClick: () -> Unit,
    onCallHistoryClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onLocationSettingsClick: () -> Unit = {},
    onSOSClick: () -> Unit = {},
    sosSending: Boolean = false,
    bottomBar: @Composable () -> Unit = {}
) {
    var showSOSDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar when connection fails
    LaunchedEffect(connectionError) {
        if (connectionError != null) {
            snackbarHostState.showSnackbar(
                message = connectionError,
                duration = SnackbarDuration.Short
            )
            onConnectionErrorDismissed()
        }
    }

    // Find the name of the user being connected to (for the overlay)
    val connectingUserName = remember(connectingUserId, connectedUsers, nearbyUsers) {
        if (connectingUserId == null) null
        else {
            val allUsers = connectedUsers + nearbyUsers
            allUsers.find { it.id == connectingUserId }?.name ?: "device"
        }
    }

    // SOS Confirmation Dialog
    if (showSOSDialog) {
        AlertDialog(
            onDismissRequest = { showSOSDialog = false },
            containerColor = SurfaceCard,
            titleContentColor = StatusFailed,
            textContentColor = TextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusFailed,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Send SOS Alert?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    "This will broadcast your location and an emergency \"HELP ME\" message to ALL devices within a 15-meter radius (both connected and nearby).\n\nOnly use this in real emergencies.",
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSOSDialog = false
                        onSOSClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusFailed
                    )
                ) {
                    Text("SEND SOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSOSDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MeshNavy,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = bottomBar,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = MeshBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BridgeFy",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLocationSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MeshNavy
                )
            )
        },
        floatingActionButton = {
            // Pulsing SOS FAB
            val infiniteTransition = rememberInfiniteTransition(label = "sosPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sosFabPulse"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sosFabAlpha"
            )

            LargeFloatingActionButton(
                onClick = { showSOSDialog = true },
                containerColor = StatusFailed.copy(alpha = pulseAlpha),
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size((72 * pulseScale).dp)
            ) {
                if (sosSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = TextPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SOS",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mesh status bar
            MeshStatusBar(stats = meshStats)

            // Discovery state banner
            DiscoveryBanner(
                discoveryState = discoveryState,
                activeSource = activeSource,
                totalFound = connectedUsers.size + nearbyUsers.size
            )

            Spacer(modifier = Modifier.height(8.dp))

            // User list with pull-to-refresh
            PullToRefreshBox(
                isRefreshing = isDiscovering,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (connectedUsers.isEmpty() && nearbyUsers.isEmpty()) {
                    EmptyUsersState(discoveryState)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── Connected Section ──
                        if (connectedUsers.isNotEmpty()) {
                            item(key = "connected_header") {
                                SectionHeader(
                                    title = "Connected",
                                    count = connectedUsers.size,
                                    icon = Icons.Default.Link,
                                    color = MeshGreen
                                )
                            }

                            items(
                                items = connectedUsers,
                                key = { "connected_${it.id}" }
                            ) { user ->
                                UserCard(
                                    user = user,
                                    onClick = { onUserClick(user) }
                                )
                            }

                            item(key = "connected_spacer") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // ── Nearby Users Section ──
                        if (nearbyUsers.isNotEmpty()) {
                            item(key = "nearby_header") {
                                SectionHeader(
                                    title = "Nearby Users",
                                    count = nearbyUsers.size,
                                    icon = Icons.Default.People,
                                    color = MeshBlue
                                )
                            }

                            items(
                                items = nearbyUsers,
                                key = { "nearby_${it.id}" }
                            ) { user ->
                                UserCard(
                                    user = user,
                                    onClick = { onUserClick(user) }
                                )
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

        // Connecting Overlay
        if (connectingUserName != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceOverlay)
                    .pointerInput(Unit) {}, // Block all touch/click interactions
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MeshBlue,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Connecting to $connectingUserName...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Setting up connection over WiFi Direct.\nPlease keep devices close.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section header with icon, title, and count badge.
 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = "$count",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Animated discovery state banner with contextual messages.
 */
@Composable
private fun DiscoveryBanner(
    discoveryState: DiscoveryState,
    activeSource: ConnectionType?,
    totalFound: Int
) {
    AnimatedVisibility(
        visible = discoveryState != DiscoveryState.IDLE,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val (stateText, stateColor, stateIcon) = when (discoveryState) {
            DiscoveryState.SCANNING_WIFI,
            DiscoveryState.SCANNING_BLE,
            DiscoveryState.READING_IDENTITIES,
            DiscoveryState.FALLBACK_WIFI_SCAN -> Triple(
                "Scanning nearby users...",
                WiFiBadge,
                Icons.Default.Wifi
            )
            DiscoveryState.WIFI_FOUND,
            DiscoveryState.BLE_FOUND -> Triple(
                "Found $totalFound user${if (totalFound != 1) "s" else ""} nearby",
                MeshGreen,
                Icons.Default.People
            )
            DiscoveryState.IDLE -> Triple("", TextMuted, Icons.Default.Wifi)
        }

        if (stateText.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = stateColor.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated scanning indicator
                    if (discoveryState == DiscoveryState.SCANNING_WIFI ||
                        discoveryState == DiscoveryState.SCANNING_BLE ||
                        discoveryState == DiscoveryState.READING_IDENTITIES ||
                        discoveryState == DiscoveryState.FALLBACK_WIFI_SCAN
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scanAlpha"
                        )
                        Icon(
                            imageVector = stateIcon,
                            contentDescription = null,
                            tint = stateColor.copy(alpha = alpha),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = stateIcon,
                            contentDescription = null,
                            tint = stateColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = stateColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Empty state shown when no nearby users are found.
 */
@Composable
private fun EmptyUsersState(discoveryState: DiscoveryState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            // Animated radar icon
            val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
            val radarScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "radarScale"
            )
            val radarAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "radarAlpha"
            )

            Icon(
                imageVector = Icons.Outlined.Radar,
                contentDescription = null,
                tint = MeshBlue.copy(alpha = radarAlpha),
                modifier = Modifier
                    .size(80.dp)
                    .then(
                        if (discoveryState != DiscoveryState.IDLE) {
                            Modifier.size((80 * radarScale).dp)
                        } else {
                            Modifier
                        }
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (discoveryState) {
                    DiscoveryState.SCANNING_WIFI,
                    DiscoveryState.SCANNING_BLE,
                    DiscoveryState.READING_IDENTITIES,
                    DiscoveryState.FALLBACK_WIFI_SCAN -> "Scanning nearby users..."
                    else -> "No users found nearby"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Make sure other BridgeFy devices are nearby\nwith WiFi turned on",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
