package com.bridgefy.app.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bridgefy.app.BridgeFyApplication
import com.bridgefy.app.MainActivity
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.security.CryptoManager
import com.bridgefy.app.security.KeyManager
import com.bridgefy.app.ui.screen.ChatScreen
import com.bridgefy.app.ui.screen.LocationPrivacySettingsScreen
import com.bridgefy.app.ui.screen.MeshMapScreen
import com.bridgefy.app.ui.screen.MeshStatsScreen
import com.bridgefy.app.ui.screen.PeersScreen
import com.bridgefy.app.ui.screen.VoiceCallScreen
import com.bridgefy.app.ui.screen.CallHistoryScreen
import com.bridgefy.app.ui.theme.*
import com.bridgefy.app.ui.viewmodel.*
import com.bridgefy.app.ui.viewmodel.CallHistoryViewModel
import com.bridgefy.app.call.CallState
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import com.google.android.gms.location.LocationServices
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Analytics

private const val SOS_CHANNEL_ID = "bridgefy_sos"
private const val SOS_NOTIFICATION_ID = 9999

@Composable
private fun BridgeFyBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(24.dp),
        color = MeshNavyLight.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        ),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple("peers", Icons.Default.ChatBubble, "Chats"),
                Triple("mesh_map", Icons.Default.Map, "Map"),
                Triple("call_history", Icons.Default.History, "Calls"),
                Triple("stats", Icons.Default.Analytics, "Stats")
            )

            items.forEach { (route, icon, label) ->
                val selected = currentRoute?.startsWith(route) == true
                
                val duration = 250
                val contentColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MeshBlueBright else TextSecondary,
                    animationSpec = androidx.compose.animation.core.tween(duration),
                    label = "bottomBarItemColor"
                )
                
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1.0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "bottomBarItemScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(route) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Navigation graph for AlertNet.
 *
 * Routes:
 * - "peers" → Nearby Users screen (home) — WhatsApp-style
 * - "chat/{peerId}/{peerName}" → Chat with a specific peer
 * - "stats" → Mesh network statistics
 * - "mesh_map?focusLat={}&focusLon={}&pinType={}" → Offline map with peer locations
 * - "location_privacy_settings" → Location broadcast & local map toggles
 */
@Composable
fun NavGraph(app: BridgeFyApplication) {
    val navController = rememberNavController()
    val factory = remember { ViewModelFactory(app) }
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarComposable: @Composable () -> Unit = {
        BridgeFyBottomBar(
            currentRoute = currentRoute,
            onNavigate = { route ->
                if (currentRoute?.startsWith(route) != true) {
                    navController.navigate(route) {
                        popUpTo("peers") {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
    }

    // Create SOS notification channel once
    LaunchedEffect(Unit) {
        createSOSNotificationChannel(context)
    }

    val globalCallState by app.voiceCallManager.callState.collectAsState()
    val globalPeerId by app.voiceCallManager.peerId.collectAsState()
    val globalPeerName by app.voiceCallManager.peerName.collectAsState()
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            app.voiceCallManager.acceptCall()
            val pId = globalPeerId ?: ""
            val pName = globalPeerName
            navController.navigate("voice_call/$pId/$pName")
        }
    }

    // If an incoming call arrives (RINGING), show the full-screen Incoming Call dialog!
    if (globalCallState == CallState.RINGING) {
        AlertDialog(
            onDismissRequest = { app.voiceCallManager.rejectCall() },
            containerColor = SurfaceCard,
            icon = {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = MeshGreen,
                    modifier = Modifier.size(56.dp)
                )
            },
            title = {
                Text(
                    text = "Incoming Voice Call",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = globalPeerName,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "BridgeFy Calling...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeshGreen
                    )
                ) {
                    Text("ACCEPT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { app.voiceCallManager.rejectCall() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusFailed
                    )
                ) {
                    Text("REJECT", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LaunchedEffect(globalCallState) {
        if (globalCallState == CallState.CONNECTED) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != null && !currentRoute.startsWith("voice_call")) {
                val pId = globalPeerId ?: ""
                val pName = globalPeerName
                navController.navigate("voice_call/$pId/$pName")
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "peers"
    ) {
        composable("peers") {
            val viewModel: PeersViewModel = viewModel(factory = factory)
            val connectedUsers by viewModel.connectedUsers.collectAsState()
            val nearbyUsers by viewModel.nearbyOnlyUsers.collectAsState()
            val meshStats by viewModel.meshStats.collectAsState()
            val isDiscovering by viewModel.isDiscovering.collectAsState()
            val discoveryState by viewModel.discoveryState.collectAsState()
            val activeSource by viewModel.activeDiscoverySource.collectAsState()
            val sosSending by viewModel.sosSending.collectAsState()
            val connectingUserId by viewModel.connectingUserId.collectAsState()
            val connectionError by viewModel.connectionError.collectAsState()

            // Fused location client for SOS GPS
            val fusedClient = remember {
                LocationServices.getFusedLocationProviderClient(context)
            }

            // ─── Incoming SOS Alert ──────────────────────────────
            var sosAlert by remember { mutableStateOf<MeshMessage?>(null) }

            LaunchedEffect(Unit) {
                viewModel.incomingSOS.collect { message ->
                    // Decrypt the payload for display
                    val decrypted = try {
                        val key = KeyManager.getKey(context)
                        if (key != null) {
                            CryptoManager.decryptString(message.payload, key) ?: message.payload
                        } else {
                            message.payload
                        }
                    } catch (_: Exception) {
                        message.payload
                    }

                    // Fire system notification
                    fireSOSNotification(context, message.senderId, decrypted)

                    // Show in-app alert
                    sosAlert = message.copy(payload = decrypted)
                }
            }

            // SOS Received Alert Dialog
            sosAlert?.let { msg ->
                AlertDialog(
                    onDismissRequest = { sosAlert = null },
                    containerColor = SurfaceCard,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusFailed,
                            modifier = Modifier.size(56.dp)
                        )
                    },
                    title = {
                        Text(
                            "🆘 SOS ALERT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = StatusFailed
                        )
                    },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "From: ${msg.senderId.take(8)}… • Within 15m Radius",
                                color = StatusFailed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = msg.payload,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val regex = Regex("""Location:\s*([-\d.]+)\s*,\s*([-\d.]+)""")
                                val match = regex.find(msg.payload)
                                val lat = match?.groupValues?.getOrNull(1)
                                val lon = match?.groupValues?.getOrNull(2)
                                sosAlert = null
                                if (lat != null && lon != null) {
                                    navController.navigate(
                                        "mesh_map?focusLat=$lat&focusLon=$lon&pinType=${msg.senderId}"
                                    )
                                } else {
                                    navController.navigate("mesh_map")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusFailed
                            )
                        ) {
                            Text("ACKNOWLEDGED", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            PeersScreen(
                connectedUsers = connectedUsers,
                nearbyUsers = nearbyUsers,
                meshStats = meshStats,
                isDiscovering = isDiscovering,
                discoveryState = discoveryState,
                activeSource = activeSource,
                connectingUserId = connectingUserId,
                connectionError = connectionError,
                onUserClick = { user ->
                    // Initiate real WiFi Direct connection; navigate to chat only on success
                    val name = user.name.replace("/", "-")
                    viewModel.connectToUser(user.id) { actualId ->
                        navController.navigate("chat/$actualId/$name")
                    }
                },
                onConnectionErrorDismissed = { viewModel.clearConnectionError() },
                onRefresh = { viewModel.refreshPeers() },
                onStatsClick = { navController.navigate("stats") },
                onCallHistoryClick = { navController.navigate("call_history") },
                onMapClick = { navController.navigate("mesh_map") },
                onLocationSettingsClick = { navController.navigate("location_privacy_settings") },
                onSOSClick = { viewModel.sendSOS(fusedClient) },
                sosSending = sosSending,
                bottomBar = bottomBarComposable
            )
        }

        composable(
            route = "chat/{peerId}/{peerName}",
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            val peerName = backStackEntry.arguments?.getString("peerName") ?: "Peer"
            val chatVm: ChatViewModel = viewModel(factory = factory)
            val locationShareVm: LocationShareViewModel = viewModel(factory = factory)

            ChatScreen(
                peerId = peerId,
                peerName = peerName,
                viewModel = chatVm,
                locationShareViewModel = locationShareVm,
                onBack = { navController.popBackStack() },
                onViewOnMap = { lat, lon ->
                    navController.navigate(
                        "mesh_map?focusLat=$lat&focusLon=$lon&pinType=$peerId"
                    )
                },
                onInitiateCall = {
                    app.voiceCallManager.initiateCall(peerId, peerName)
                    navController.navigate("voice_call/$peerId/$peerName")
                }
            )
        }

        composable(
            route = "voice_call/{peerId}/{peerName}",
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            val peerName = backStackEntry.arguments?.getString("peerName") ?: "Peer"
            val voiceCallVm: VoiceCallViewModel = viewModel(factory = factory)
            val callState by voiceCallVm.callState.collectAsState()
            val callDuration by voiceCallVm.callDuration.collectAsState()
            val isMuted by voiceCallVm.isMuted.collectAsState()
            val isSpeaker by voiceCallVm.isSpeaker.collectAsState()

            LaunchedEffect(callState) {
                if (callState == CallState.IDLE) {
                    navController.popBackStack()
                }
            }

            VoiceCallScreen(
                peerName = peerName,
                callState = callState,
                callDuration = callDuration,
                isMuted = isMuted,
                isSpeaker = isSpeaker,
                onMuteToggle = { voiceCallVm.toggleMute() },
                onSpeakerToggle = { voiceCallVm.toggleSpeaker() },
                onEndCall = { voiceCallVm.endCall() }
            )
        }

        composable("call_history") {
            val callHistoryVm: CallHistoryViewModel = viewModel(factory = factory)
            val logs by callHistoryVm.callLogs.collectAsState()

            LaunchedEffect(Unit) {
                callHistoryVm.loadCallLogs()
            }

            CallHistoryScreen(
                callLogs = logs,
                onBack = { navController.popBackStack() },
                onCallUser = { peerId, peerName ->
                    app.voiceCallManager.initiateCall(peerId, peerName)
                    navController.navigate("voice_call/$peerId/$peerName")
                },
                bottomBar = bottomBarComposable
            )
        }

        composable("stats") {
            val viewModel: PeersViewModel = viewModel(factory = factory)
            val meshStats by viewModel.meshStats.collectAsState()

            MeshStatsScreen(
                stats = meshStats,
                deviceId = app.deviceId,
                onBack = { navController.popBackStack() },
                bottomBar = bottomBarComposable
            )
        }

        // ─── Mesh Map ────────────────────────────────────────────
        composable(
            route = "mesh_map?focusLat={focusLat}&focusLon={focusLon}&pinType={pinType}",
            arguments = listOf(
                navArgument("focusLat") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("focusLon") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("pinType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val focusLat = backStackEntry.arguments?.getString("focusLat")?.toDoubleOrNull()
            val focusLon = backStackEntry.arguments?.getString("focusLon")?.toDoubleOrNull()
            val pinType = backStackEntry.arguments?.getString("pinType")
            val mapVm: MeshMapViewModel = viewModel(factory = factory)

            MeshMapScreen(
                focusLat = focusLat,
                focusLon = focusLon,
                pinType = pinType,
                viewModel = mapVm,
                onBack = { navController.popBackStack() },
                bottomBar = bottomBarComposable
            )
        }

        // ─── Location Privacy Settings ───────────────────────────
        composable("location_privacy_settings") {
            val privacyVm: LocationPrivacyViewModel = viewModel(factory = factory)

            LocationPrivacySettingsScreen(
                viewModel = privacyVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ─── SOS Notification Helpers ─────────────────────────────────────

private fun createSOSNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            SOS_CHANNEL_ID,
            "SOS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Emergency SOS alerts from nearby BridgeFy devices"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            enableLights(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

private fun fireSOSNotification(context: Context, senderId: String, payload: String) {
    val pendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("🆘 SOS ALERT (<15m) — HELP ME!")
        .setContentText("Emergency within 15 meters from ${senderId.take(8)}…")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(payload)
                .setBigContentTitle("🆘 SOS ALERT (15m Radius)")
                .setSummaryText("Proximity Emergency Broadcast")
        )
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setSound(alarmSound)
        .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setFullScreenIntent(pendingIntent, true)
        .build()

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(SOS_NOTIFICATION_ID, notification)
}
