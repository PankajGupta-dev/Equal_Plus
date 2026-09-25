package com.bridgefy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.ui.theme.*

/**
 * Modern attachment bottom sheet grid UI.
 * Features:
 * - Rounded top corners
 * - Dark navy theme matching the design reference
 * - Colored modern icons with tinted glowing background circles
 * - Touch-friendly cards for Gallery, Camera, Location, Document
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    onDismiss: () -> Unit,
    onSelectGallery: () -> Unit,
    onSelectCamera: () -> Unit,
    onSelectLocation: () -> Unit,
    onSelectDocument: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MeshNavyLight,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Attach",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Grid of options (2x2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AttachmentItemCard(
                        icon = Icons.Default.InsertPhoto,
                        title = "Gallery",
                        description = "Photos & Videos",
                        color = Color(0xFF3B82F6), // Blue
                        onClick = {
                            onDismiss()
                            onSelectGallery()
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AttachmentItemCard(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera",
                        description = "Take Photo / Video",
                        color = Color(0xFFD946EF), // Fuchsia / Purple Magenta
                        onClick = {
                            onDismiss()
                            onSelectCamera()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AttachmentItemCard(
                        icon = Icons.Default.LocationOn,
                        title = "Location",
                        description = "Share Location",
                        color = Color(0xFFF97316), // Orange
                        onClick = {
                            onDismiss()
                            onSelectLocation()
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AttachmentItemCard(
                        icon = Icons.Default.Description,
                        title = "Document",
                        description = "Files & Docs",
                        color = Color(0xFF10B981), // Green
                        onClick = {
                            onDismiss()
                            onSelectDocument()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttachmentItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
