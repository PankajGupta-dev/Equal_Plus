package com.bridgefy.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.model.CallLog
import com.bridgefy.app.model.CallLogStatus
import com.bridgefy.app.model.CallType
import com.bridgefy.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    callLogs: List<CallLog>,
    onBack: () -> Unit = {},
    onCallUser: (peerId: String, peerName: String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    }

    Scaffold(
        containerColor = MeshNavy,
        bottomBar = bottomBar,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Call History",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MeshNavy
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (callLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MeshNavy),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneMissed,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No Call History",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All your direct voice calls over Wi-Fi Direct will be logged here.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MeshNavy),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(callLogs, key = { it.id }) { log ->
                    val isOutgoing = log.callerId != log.receiverId && log.callerId.startsWith("device") || !log.callerId.contains("-") // caller is us, let's simplify: callerId == our ID
                    // Wait, let's simplify display: we can show caller/receiver display. Since we only have raw device IDs, let's truncate.
                    val remoteId = if (log.callerId.startsWith("device") || log.callerId == "us") log.receiverId else log.callerId
                    
                    val statusColor = when (log.status) {
                        CallLogStatus.MISSED -> StatusFailed
                        CallLogStatus.REJECTED -> StatusFailed
                        CallLogStatus.ANSWERED, CallLogStatus.ENDED -> MeshGreen
                    }

                    val statusIcon = when (log.status) {
                        CallLogStatus.MISSED -> Icons.Default.CallMissed
                        CallLogStatus.REJECTED -> Icons.Default.CallMissed
                        CallLogStatus.ANSWERED, CallLogStatus.ENDED -> {
                            if (log.callerId == remoteId) Icons.Default.CallReceived else Icons.Default.CallMade
                        }
                    }

                    val durationText = if (log.duration > 0) {
                        val min = log.duration / 60
                        val sec = log.duration % 60
                        String.format("%02d:%02d", min, sec)
                    } else {
                        "No answer"
                    }

                    val dateText = remember(log.startTime) {
                        dateFormatter.format(Date(log.startTime))
                    }

                    val peerDisplayName = remoteId.take(8).uppercase()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCallUser(remoteId, "Peer-$peerDisplayName")
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Avatar Icon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MeshNavyLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = MeshBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Middle Info
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Peer ($peerDisplayName)",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${log.status.name.lowercase().replaceFirstChar { it.titlecase() }} • $durationText",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Timestamp and Call Back Button
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = dateText,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )

                                IconButton(
                                    onClick = {
                                        onCallUser(remoteId, "Peer-$peerDisplayName")
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MeshNavyLight, CircleShape)
                                 ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call Back",
                                        tint = MeshGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
