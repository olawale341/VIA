package com.globalchat

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
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

@Serializable
data class OtpRequestBody(val phone: String, val email: String)

@Serializable
data class OtpVerifyBody(val phone: String, val code: String)

@Serializable
data class OtpApiResult(val ok: Boolean, val message: String)

@Serializable
data class BrevoSender(val name: String, val email: String)

@Serializable
data class BrevoRecipient(val email: String)

@Serializable
data class BrevoEmailPayload(
    val sender: BrevoSender,
    val to: List<BrevoRecipient>,
    val subject: String,
    val htmlContent: String
)

data class PendingOtp(val code: String, val expiresAtMs: Long)

private val json = Json { ignoreUnknownKeys = true }
private val httpClient = HttpClient(CIO)
private val random = SecureRandom()
private val otpStore = ConcurrentHashMap<String, PendingOtp>()
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

        post("/auth/request-otp") {
            val body = json.decodeFromString<OtpRequestBody>(call.receiveText())
            val apiKey = System.getenv("BREVO_API_KEY").orEmpty().trim()
            val senderEmail = System.getenv("BREVO_SENDER_EMAIL").orEmpty().trim()

            if (apiKey.isBlank() || senderEmail.isBlank()) {
                call.respondText(
                    json.encodeToString(OtpApiResult(false, "Server is missing BREVO_API_KEY or BREVO_SENDER_EMAIL")),
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
                return@post
            }

            val code = (100000 + random.nextInt(900000)).toString()
            otpStore[body.phone.trim()] = PendingOtp(code, System.currentTimeMillis() + 10 * 60_000L)

            val brevoBody = BrevoEmailPayload(
                sender = BrevoSender("GlobalChat", senderEmail),
                to = listOf(BrevoRecipient(body.email.trim())),
                subject = "Your GlobalChat Verification Code: $code",
                htmlContent = "<div style='font-family:sans-serif;padding:20px'><h2>GlobalChat Verification</h2><p>Your code for <b>${body.phone}</b> is:</p><h1 style='color:#008069;letter-spacing:4px'>$code</h1><p>This code expires in 10 minutes.</p></div>"
            )

            val resp = httpClient.post("https://api.brevo.com/v3/smtp/email") {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(brevoBody))
            }

            if (resp.status.isSuccess()) {
                call.respondText(
                    json.encodeToString(OtpApiResult(true, "Code sent to ${body.email}")),
                    ContentType.Application.Json
                )
            } else {
                val err = resp.bodyAsText()
                println("BREVO ERROR: ${resp.status} $err")
                call.respondText(
                    json.encodeToString(OtpApiResult(false, "Email failed (${resp.status.value})")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadGateway
                )
            }
        }

        post("/auth/verify-otp") {
            val body = json.decodeFromString<OtpVerifyBody>(call.receiveText())
            val pending = otpStore[body.phone.trim()]
            val valid = pending != null &&
                System.currentTimeMillis() <= pending.expiresAtMs &&
                pending.code == body.code.trim()

            if (valid) {
                otpStore.remove(body.phone.trim())
                call.respondText(json.encodeToString(OtpApiResult(true, "Verified")), ContentType.Application.Json)
            } else {
                call.respondText(
                    json.encodeToString(OtpApiResult(false, "Invalid or expired code")),
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized
                )
            }
        }

        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket close(
                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing userId")
            )
            activeConnections[userId] = this
            broadcastPresence(userId, true)
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

            offlineQueue.remove(userId)?.forEach { msg ->
                sendPayload(this, msg)
                activeConnections[msg.senderId]?.let { s ->
                    sendPayload(s, msg.copy(type = "ACK_DELIVERED", status = MessageStatus.DELIVERED, mediaBase64 = null))
                }
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        handlePayload(json.decodeFromString(frame.readText()))
                    }
                }
            } catch (_: Exception) {
            } finally {
                activeConnections.remove(userId)
                broadcastPresence(userId, false)
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
                val target = activeConnections[targetId]
                if (target != null) {
                    sendPayload(target, msg)
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
                val target = activeConnections[targetId]
                if (target != null) sendPayload(target, msg)
                else if (msg.type != "TYPING") offlineQueue.computeIfAbsent(targetId) { CopyOnWriteArrayList() }.add(msg)
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
    activeConnections.forEach { (id, session) -> if (id != userId) sendPayload(session, presence) }
}

private suspend fun sendPayload(session: DefaultWebSocketServerSession, payload: ChatPayload) {
    try {
        session.send(Frame.Text(json.encodeToString(payload)))
    } catch (_: Exception) { }
}