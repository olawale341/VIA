package com.globalchat.android.data.remote

import com.globalchat.android.model.ChatPayload
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatSocketClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) { install(WebSockets) }

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val _incoming = MutableSharedFlow<ChatPayload>(extraBufferCapacity = 128)
    val incoming: SharedFlow<ChatPayload> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun start(scope: CoroutineScope, myUserId: String, serverHost: String) {
        val cleanHost = serverHost.trim()
            .removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val scheme = if (
            cleanHost.startsWith("10.") ||
            cleanHost.startsWith("192.168.") ||
            cleanHost.startsWith("localhost") ||
            cleanHost.contains(":")
        ) "ws" else "wss"

        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    client.webSocket("$scheme://$cleanHost/chat/$myUserId") {
                        session = this
                        _isConnected.value = true
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val payload = json.decodeFromString<ChatPayload>(frame.readText())
                                _incoming.emit(payload)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Auto-retry every 3 seconds
                } finally {
                    session = null
                    _isConnected.value = false
                }
                delay(3000)
            }
        }
    }

    suspend fun send(payload: ChatPayload): Boolean {
        val active = session ?: return false
        return try {
            active.send(Frame.Text(json.encodeToString(payload)))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        connectionJob?.cancel()
        session = null
        _isConnected.value = false
    }
}