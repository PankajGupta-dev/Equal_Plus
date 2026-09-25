package com.bridgefy.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/**
 * One-time consent dialog shown on first visit to MeshMapScreen.
 *
 * Gated by `has_shown_location_consent` DataStore flag (default false).
 * Both accept and decline set the flag to true — it never appears again.
 *
 * Accept → enables mesh location broadcasting.
 * Decline → leaves broadcasting disabled; user can enable later in Settings.
 */
@Composable
fun LocationConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Share Your Location?") },
        text = {
            Text(
                "Peers on this mesh can see your location on their map. " +
                "You can change this anytime in Settings → Privacy."
            )
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("Share My Location") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Not Now") }
        }
    )
}
