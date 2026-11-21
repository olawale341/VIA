package com.globalchat

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

@Serializable
enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

@Serializable
data class ChatPayload(
    val id: String,
    val chatId: String,
    val senderId: String,
    val receiverIds: List<String>,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val type: String = "TEXT",
    val mediaType: String = "TEXT",
    val mediaBase64: String? = null,
    val durationSec: Int = 0,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val reaction: String? = null,
    val isGroup: Boolean = false,
    val groupName: String? = null
)

private val json = Json { ignoreUnknownKeys = true }
private val activeConnections = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
private val offlineQueue = ConcurrentHashMap<String, CopyOnWriteArrayList<ChatPayload>>()

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 20.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respondText("GlobalChat Server Online (${activeConnections.size} users connected)")
        }

        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket close(
                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing userId")
            )

            activeConnections[userId] = this
            println("CONNECTED: $userId (Online: ${activeConnections.size})")

            broadcastPresence(userId, isOnline = true)
            activeConnections.keys.filter { it != userId }.forEach { onlineUser ->
                sendPayload(
                    this,
                    ChatPayload(
                        id = "presence-$onlineUser",
                        chatId = onlineUser,
                        senderId = onlineUser,
                        receiverIds = listOf(userId),
                        content = "ONLINE",
                        timestamp = System.currentTimeMillis(),
                        type = "PRESENCE"
                    )
                )
            }

            // Deliver missed messages that arrived while user was offline
            val missed = offlineQueue.remove(userId)
            missed?.forEach { msg ->
                sendPayload(this, msg)
                activeConnections[msg.senderId]?.let { senderSession ->
                    sendPayload(senderSession, msg.copy(type = "ACK_DELIVERED", status = MessageStatus.DELIVERED))
                }
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val payload = json.decodeFromString<ChatPayload>(frame.readText())
                        handlePayload(payload)
                    }
                }
            } catch (e: Exception) {
                println("ERROR [$userId]: ${e.localizedMessage}")
            } finally {
                activeConnections.remove(userId)
                broadcastPresence(userId, isOnline = false)
                println("DISCONNECTED: $userId")
            }
        }
    }
}

private suspend fun handlePayload(msg: ChatPayload) {
    val senderSession = activeConnections[msg.senderId]

    when (msg.type) {
        "TEXT" -> {
            senderSession?.let {
                sendPayload(it, msg.copy(type = "ACK_SENT", status = MessageStatus.SENT, mediaBase64 = null))
            }
            var delivered = false
            msg.receiverIds.filter { it != msg.senderId }.forEach { targetId ->
                val targetSession = activeConnections[targetId]
                if (targetSession != null) {
                    sendPayload(targetSession, msg)
                    delivered = true
                } else {
                    offlineQueue.computeIfAbsent(targetId) { CopyOnWriteArrayList() }.add(msg)
                }
            }
            if (delivered) {
                senderSession?.let {
                    sendPayload(it, msg.copy(type = "ACK_DELIVERED", status = MessageStatus.DELIVERED, mediaBase64 = null))
                }
            }
        }

        "ACK_READ", "TYPING", "REACTION", "DELETE" -> {
            msg.receiverIds.filter { it != msg.senderId }.forEach { targetId ->
                val targetSession = activeConnections[targetId]
                if (targetSession != null) {
                    sendPayload(targetSession, msg)
                } else if (msg.type != "TYPING") {
                    offlineQueue.computeIfAbsent(targetId) { CopyOnWriteArrayList() }.add(msg)
                }
            }
        }
    }
}

private suspend fun broadcastPresence(userId: String, isOnline: Boolean) {
    val presence = ChatPayload(
        id = "presence-$userId-${System.currentTimeMillis()}",
        chatId = userId,
        senderId = userId,
        receiverIds = emptyList(),
        content = if (isOnline) "ONLINE" else "OFFLINE",
        timestamp = System.currentTimeMillis(),
        type = "PRESENCE"
    )
    activeConnections.forEach { (id, session) ->
        if (id != userId) sendPayload(session, presence)
    }
}

private suspend fun sendPayload(session: DefaultWebSocketServerSession, payload: ChatPayload) {
    try {
        session.send(Frame.Text(json.encodeToString(payload)))
    } catch (_: Exception) { }
}