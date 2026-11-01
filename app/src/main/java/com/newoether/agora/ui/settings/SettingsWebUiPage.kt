package com.newoether.agora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newoether.agora.webui.WebUiServer
import kotlinx.coroutines.launch

@Composable
fun SettingsWebUiPage(
    server: WebUiServer,
    isRunning: Boolean,
    port: Int,
    onPortChange: (Int) -> Unit,
) {
    var portText by remember(port) { mutableStateOf(port.toString()) }
    var isStarting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isRunning) { isStarting = false }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Web UI", style = MaterialTheme.typography.titleLarge)
        Text("Run a browser-based interface on your local network.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = portText,
            onValueChange = { value ->
                portText = value.filter(Char::isDigit).take(5)
                portText.toIntOrNull()?.let(onPortChange)
            },
            label = { Text("Port") },
            singleLine = true,
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (isRunning) {
            Text("Web UI is running on port $port", color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !isRunning && !isStarting,
                onClick = {
                    val requestedPort = portText.toIntOrNull()
                    if (requestedPort == null || requestedPort !in 1..65535) {
                        errorMessage = "Enter a port from 1 to 65535"
                    } else {
                        errorMessage = null
                        isStarting = true
                        scope.launch {
                            try {
                                server.start(requestedPort)
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "Unable to start Web UI"
                                isStarting = false
                            }
                        }
                    }
                },
            ) {
                if (isStarting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Start Web UI")
            }
            if (isRunning) {
                Button(onClick = { server.stop() }) { Text("Stop") }
            }
        }
    }
}
