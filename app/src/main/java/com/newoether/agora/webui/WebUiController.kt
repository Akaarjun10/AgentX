package com.newoether.agora.webui

import android.content.Context
import com.newoether.agora.R
import com.newoether.agora.util.DebugLog
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whether the WebUI server is listening. */
internal sealed interface WebUiStatus {
    data object Stopped : WebUiStatus
    data object Starting : WebUiStatus
    data class Running(val port: Int, val https: Boolean) : WebUiStatus
    /** The server could not start, typically because the port is in use. */
    data class Failed(val message: String) : WebUiStatus
}

/**
 * Binds and starts the server, returning once it listens.
 *
 * Deliberately a plain function: inside a coroutine body, `embeddedServer` resolves to Ktor's
 * `CoroutineScope.embeddedServer`, which makes the server a child of that coroutine, so the
 * enclosing `withContext` would never return while the server runs.
 */
internal fun startWebUiEngine(
    port: Int,
    routes: WebUiServer,
    host: String = WebUiController.ANY_HOST,
    extraConnectors: Int = 0,
): EmbeddedServer<*, *> =
    embeddedServer(
        CIO,
        configure = {
            // The first connector takes [port]; each extra one takes a subsequent ephemeral port.
            connectors.clear()
            connectors.add(connector { this.port = port; this.host = host })
            repeat(extraConnectors) { connectors.add(connector { this.port = 0; this.host = host }) }
            shareWorkGroup = false
        },
    ) { routes.install(this) }

/** Controls the local HTTP/HTTPS WebUI server and exposes connection status. */
internal class WebUiController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val serverFactory: (Int, WebUiServer, String, Int) -> EmbeddedServer<*, *> =
        ::startWebUiEngine,
) {
    private val _status = MutableStateFlow<WebUiStatus>(WebUiStatus.Stopped)
    val status: StateFlow<WebUiStatus> = _status.asStateFlow()

    private val transitionMutex = Mutex()
    private var server: EmbeddedServer<*, *>? = null

    fun start(): Flow<WebUiStatus> = _status.map { it }

    suspend fun startIfEnabled(): WebUiStatus {
        val settings = context.applicationContext
            .let { app -> app.getSharedPreferences("settings", Context.MODE_PRIVATE) }
        val enabled = settings.getBoolean("webui_enabled", false)
        if (!enabled) return WebUiStatus.Stopped
        val port = settings.getInt("webui_port", 8765)
        val useHttps = settings.getBoolean("webui_https", true)
        return startServer(port, useHttps)
    }

    suspend fun startServer(port: Int, useHttps: Boolean): WebUiStatus = transitionMutex.withLock {
        if (port !in 1..65535) {
            val failed = WebUiStatus.Failed("Port must be between 1 and 65535")
            _status.value = failed
            return@withLock failed
        }
        _status.value = WebUiStatus.Starting
        val existing = server
        if (existing != null) {
            try {
                withContext(Dispatchers.IO) { existing.stop(100, 500) }
            } catch (_: Exception) { /* ignore stop failure */ }
            server = null
        }
        try {
            val routes = WebUiServer(context.applicationContext, useHttps)
            val candidate = serverFactory(port, routes, ANY_HOST, EXTRA_CONNECTORS)
            withContext(Dispatchers.IO) { candidate.start(wait = true) }
            server = candidate
            val running = WebUiStatus.Running(port, useHttps)
            _status.value = running
            running
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            DebugLog.e(TAG, "Failed to start WebUI", error)
            val failed = WebUiStatus.Failed(message)
            _status.value = failed
            failed
        }
    }

    suspend fun stopServer(): WebUiStatus = transitionMutex.withLock {
        val existing = server
        server = null
        if (existing != null) {
            try {
                withContext(Dispatchers.IO) { existing.stop(100, 500) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DebugLog.e(TAG, "Failed to stop WebUI", error)
            }
        }
        _status.value = WebUiStatus.Stopped
        WebUiStatus.Stopped
    }

    suspend fun close() {
        try { stopServer() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { /* shutdown best-effort */ }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val ANY_HOST = "0.0.0.0"
        const val EXTRA_CONNECTORS = 1
        private const val TAG = "WebUiController"
    }
}
