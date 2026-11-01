package com.newoether.agora.webui

import android.content.Context
import android.util.Log
import com.newoether.agora.data.PreferencesManager
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns and serves the Web UI server, exposing safe handlers for UI interactions.
 */
class WebUiController(
    context: Context,
    private val preferencesManager: PreferencesManager,
    private val viewModel: ChatViewModel,
) {
    private val appContext = context.applicationContext
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: WebUiServer? = null
    private var port = DEFAULT_PORT

    suspend fun startServer(requestedPort: Int = port) {
        server?.stop()
        port = requestedPort
        server = WebUiServer(appContext, viewModel, preferencesManager, controllerScope, port)
        server?.start(port)
        Log.i(TAG, "Web UI server started on port $port")
    }

    fun stopServer() {
        server?.stop()
        server = null
        Log.i(TAG, "Web UI server stopped")
    }

    fun isRunning(): Boolean = server?.isRunning() ?: false

    fun getPort(): Int = port

    private companion object {
        const val TAG = "WebUiController"
        const val DEFAULT_PORT = 8080
    }
}
