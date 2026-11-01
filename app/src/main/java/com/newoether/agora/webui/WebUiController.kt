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
    data class Failed(val message: String) : WebUiStatus
}

internal fun startWebUiEngine(
    port: Int,
    routes: WebUiServer,
    host: String = WebUiController.ANY_HOST,
    extraConnectors: Int = 0,
): EmbeddedServer<*, *> =
    embeddedServer(
        CIO,
        configure = {
            connectors.clear()
            connectors.add(connector { this.port = port; this.host = host })
            repeat(extraConnectors) { connectors.add(connector { this.port = 0; this.host = host }) }
            shareWorkGroup = false
        },
    ) { routes.install(this) }

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
        val settings = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!settings.getBoolean("webui_enabled", false)) return WebUiStatus.Stopped
        return startServer(settings.getInt("webui_port", 8765), settings.getBoolean("webui_https", true))
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
            try { withContext(Dispatchers.IO) { existing.stop(100, 500) } } catch (_: Exception) { }
            server = null
        }
        try {
            val candidate = serverFactory(port, WebUiServer(context.applicationContext, useHttps), ANY_HOST, EXTRA_CONNECTORS)
            withContext(Dispatchers.IO) { candidate.start(wait = true) }
            server = candidate
            WebUiStatus.Running(port, useHttps).also { _status.value = it }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failed = WebUiStatus.Failed(error.message ?: error.javaClass.simpleName)
            DebugLog.e(TAG, "Failed to start WebUI", error)
            _status.value = failed
            failed
        }
    }

    suspend fun stopServer(): WebUiStatus = transitionMutex.withLock {
        val existing = server
        server = null
        if (existing != null) {
            try { withContext(Dispatchers.IO) { existing.stop(100, 500) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { DebugLog.e(TAG, "Failed to stop WebUI", error) }
        }
        WebUiStatus.Stopped.also { _status.value = it }
    }

    suspend fun close() {
        try { stopServer() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val ANY_HOST = "0.0.0.0"
        const val EXTRA_CONNECTORS = 1
        private const val TAG = "WebUiController"
    }
}
