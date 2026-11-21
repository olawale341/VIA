package com.globalchat.android.data.remote

import com.globalchat.android.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ChatSocketClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val publicKeyCache = ConcurrentHashMap<String, String>()
    private val profileCache = ConcurrentHashMap<String, RegisteredUserDto>()

    private val client = HttpClient(OkHttp) {
        install(WebSockets)
        engine {
            config {
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val _incoming = MutableSharedFlow<ChatPayload>(extraBufferCapacity = 128)
    val incoming: SharedFlow<ChatPayload> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private fun parseHost(serverHost: String): Pair<String, Boolean> {
        val cleanHost = serverHost.trim()
            .removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val isLocal = cleanHost.startsWith("10.") ||
            cleanHost.startsWith("192.168.") ||
            cleanHost.startsWith("localhost") ||
            cleanHost.contains(":")
        return cleanHost to isLocal
    }

    private fun httpBase(serverHost: String): String {
        val (host, isLocal) = parseHost(serverHost)
        val scheme = if (isLocal) "http" else "https"
        return "$scheme://$host"
    }

    suspend fun requestOtp(serverHost: String, phone: String, email: String): OtpApiResult {
        return try {
            val resp = client.post("${httpBase(serverHost)}/auth/request-otp") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(OtpRequestBody(phone, email)))
            }
            json.decodeFromString<OtpApiResult>(resp.bodyAsText())
        } catch (e: Exception) {
            OtpApiResult(false, "Server waking up or unreachable: ${e.localizedMessage}")
        }
    }

    suspend fun verifyOtp(serverHost: String, phone: String, code: String): OtpApiResult {
        return try {
            val resp = client.post("${httpBase(serverHost)}/auth/verify-otp") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(OtpVerifyBody(phone, code)))
            }
            json.decodeFromString<OtpApiResult>(resp.bodyAsText())
        } catch (e: Exception) {
            OtpApiResult(false, "Network error: ${e.localizedMessage}")
        }
    }

    suspend fun syncProfile(serverHost: String, phone: String, displayName: String, about: String, avatarBase64: String?) {
        try {
            client.post("${httpBase(serverHost)}/profile/sync") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ProfileSyncBody(phone, displayName, about, avatarBase64)))
            }
        } catch (_: Exception) { }
    }

    suspend fun fetchProfile(serverHost: String, phone: String): RegisteredUserDto? {
        profileCache[phone]?.let { return it }
        return try {
            val resp = client.get("${httpBase(serverHost)}/profile/${phone.encodeURLParameter()}")
            val dto = json.decodeFromString<RegisteredUserDto>(resp.bodyAsText())
            profileCache[phone] = dto
            dto
        } catch (_: Exception) {
            null
        }
    }

    suspend fun registerFcmToken(serverHost: String, phone: String, token: String) {
        try {
            client.post("${httpBase(serverHost)}/profile/fcm-token") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(FcmTokenBody(phone, token)))
            }
        } catch (_: Exception) { }
    }

    suspend fun uploadPublicKey(serverHost: String, phone: String, publicKey: String) {
        try {
            client.post("${httpBase(serverHost)}/keys/upload") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(PublicKeyBody(phone, publicKey)))
            }
        } catch (_: Exception) { }
    }

    suspend fun fetchPublicKey(serverHost: String, phone: String): String? {
        publicKeyCache[phone]?.let { return it }
        return try {
            val resp = client.get("${httpBase(serverHost)}/keys/${phone.encodeURLParameter()}")
            val key = json.decodeFromString<PublicKeyResponse>(resp.bodyAsText()).publicKey
            if (key.isNotBlank()) {
                publicKeyCache[phone] = key
                key
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun syncContacts(serverHost: String, phones: List<String>): List<RegisteredUserDto> {
        if (phones.isEmpty()) return emptyList()
        return try {
            val resp = client.post("${httpBase(serverHost)}/contacts/sync") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ContactSyncRequest(phones)))
            }
            val users = json.decodeFromString<ContactSyncResponse>(resp.bodyAsText()).users
            users.forEach { profileCache[it.phone] = it }
            users
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun postStatus(serverHost: String, status: StatusDto) {
        try {
            client.post("${httpBase(serverHost)}/statuses") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(status))
            }
        } catch (_: Exception) { }
    }

    suspend fun fetchStatuses(serverHost: String): List<StatusDto> {
        return try {
            val resp = client.get("${httpBase(serverHost)}/statuses")
            json.decodeFromString<StatusListResponse>(resp.bodyAsText()).statuses
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun start(scope: CoroutineScope, myUserId: String, serverHost: String) {
        val (cleanHost, isLocal) = parseHost(serverHost)
        val wsScheme = if (isLocal) "ws" else "wss"

        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    client.webSocket("$wsScheme://$cleanHost/chat/$myUserId") {
                        session = this
                        _isConnected.value = true
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                _incoming.emit(json.decodeFromString<ChatPayload>(frame.readText()))
                            }
                        }
                    }
                } catch (_: Exception) {
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