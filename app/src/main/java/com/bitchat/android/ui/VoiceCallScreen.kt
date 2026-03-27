package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bitchat.android.model.CallState

@Composable
fun VoiceCallScreen(
    callState: CallState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = getStatusText(callState),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                when (callState.status) {
                    CallState.Status.INCOMING -> {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                Text("Accept")
                            }
                            Button(onClick = onDecline, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("Decline")
                            }
                        }
                    }
                    CallState.Status.OUTGOING, CallState.Status.ACTIVE -> {
                        Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("End Call")
                        }
                    }
                    CallState.Status.IDLE -> {
                        // Should not be visible in this state, but handle gracefully
                    }
                }
            }
        }
    }
}

@Composable
private fun getStatusText(callState: CallState): String {
    val nickname = callState.remoteNickname ?: "Unknown"
    return when (callState.status) {
        CallState.Status.IDLE -> "Idle"
        CallState.Status.OUTGOING -> "Calling $nickname..."
        CallState.Status.INCOMING -> "Incoming call from\n$nickname"
        CallState.Status.ACTIVE -> "On call with\n$nickname"
    }
}
