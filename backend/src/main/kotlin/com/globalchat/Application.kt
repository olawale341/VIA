package com.globalchat

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
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
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.SecureRandom
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
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
    val groupName: String? = null,
    val callVideo: Boolean = false,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
)

@Serializable
data class OtpRequestBody(val phone: String, val email: String)

@Serializable
data class OtpVerifyBody(val phone: String, val code: String)

@Serializable
data class OtpApiResult(val ok: Boolean, val message: String)

@Serializable
data class ProfileSyncBody(val phone: String, val displayName: String, val about: String)

@Serializable
data class FcmTokenBody(val phone: String, val token: String)

@Serializable
data class ContactSyncRequest(val phones: List<String>)

@Serializable
data class RegisteredUserDto(val phone: String, val displayName: String, val about: String)

@Serializable
data class ContactSyncResponse(val users: List<RegisteredUserDto>)

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

data class PendingOtp(val code: String, val email: String, val expiresAtMs: Long)

private val json = Json { ignoreUnknownKeys = true }
private val httpClient = HttpClient(CIO)
private val random = SecureRandom()
private val otpStore = ConcurrentHashMap<String, PendingOtp>()
private val activeConnections = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

object FcmSender {
    private var ready = false

    fun init() {
        val saJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")?.trim().orEmpty()
        if (saJson.isBlank()) return
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(ByteArrayInputStream(saJson.toByteArray())))
                    .build()
                FirebaseApp.initializeApp(options)
            }
            ready = true
        } catch (e: Exception) {
            println("FCM INIT ERROR: ${e.localizedMessage}")
        }
    }

    fun sendPush(token: String, title: String, body: String, chatId: String) {
        if (!ready || token.isBlank()) return
        try {
            val msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("chatId", chatId)
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build()
            FirebaseMessaging.getInstance().send(msg)
        } catch (e: Exception) {
            println("FCM SEND ERROR: ${e.localizedMessage}")
        }
    }
}

object Db {
    private var dataSource: HikariDataSource? = null

    fun init() {
        val rawUrl = System.getenv("DATABASE_URL")?.trim().orEmpty()
        if (rawUrl.isBlank()) return
        if (rawUrl.startsWith("jdbc:")) {
            dataSource = HikariDataSource(HikariConfig().apply { jdbcUrl = rawUrl; maximumPoolSize = 5 })
            return
        }
        val uri = URI(rawUrl.replaceFirst("postgresql://", "http://").replaceFirst("postgres://", "http://"))
        val userInfo = uri.userInfo ?: ""
        val user = userInfo.substringBefore(":")
        val pass = userInfo.substringAfter(":", "")
        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        val queryPart = if (!uri.query.isNullOrBlank()) "?${uri.query}" else "?sslmode=require"
        val jdbcUrl = "jdbc:postgresql://${uri.host}$portPart${uri.path}$queryPart"
        dataSource = HikariDataSource(HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = user
            password = pass
            maximumPoolSize = 5
        })
    }

    fun ensureTables() {
        withConn { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        phone TEXT PRIMARY KEY,
                        email TEXT NOT NULL DEFAULT '',
                        display_name TEXT NOT NULL DEFAULT '',
                        about TEXT NOT NULL DEFAULT 'Hey there! I am using GlobalChat.',
                        fcm_token TEXT NOT NULL DEFAULT '',
                        public_key TEXT NOT NULL DEFAULT '',
                        updated_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS offline_messages (
                        id TEXT PRIMARY KEY,
                        target_phone TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun upsertUser(phone: String, email: String?, name: String?, about: String?) {
        withConn { conn ->
            conn.prepareStatement(
                """
                INSERT INTO users (phone, email, display_name, about, updated_at)
                VALUES (?, COALESCE(?, ''), COALESCE(?, ?), COALESCE(?, 'Hey there! I am using GlobalChat.'), ?)
                ON CONFLICT (phone) DO UPDATE SET
                    email = CASE WHEN EXCLUDED.email <> '' THEN EXCLUDED.email ELSE users.email END,
                    display_name = CASE WHEN EXCLUDED.display_name <> '' THEN EXCLUDED.display_name ELSE users.display_name END,
                    about = CASE WHEN EXCLUDED.about <> '' THEN EXCLUDED.about ELSE users.about END,
                    updated_at = EXCLUDED.updated_at
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, phone); ps.setString(2, email); ps.setString(3, name)
                ps.setString(4, phone); ps.setString(5, about); ps.setLong(6, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    fun saveFcmToken(phone: String, token: String) {
        withConn { conn ->
            conn.prepareStatement("UPDATE users SET fcm_token = ?, updated_at = ? WHERE phone = ?").use { ps ->
                ps.setString(1, token); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, phone)
                ps.executeUpdate()
            }
        }
    }

    fun getFcmToken(phone: String): String {
        var token = ""
        withConn { conn ->
            conn.prepareStatement("SELECT fcm_token FROM users WHERE phone = ? LIMIT 1").use { ps ->
                ps.setString(1, phone)
                ps.executeQuery().use { rs -> if (rs.next()) token = rs.getString("fcm_token") ?: "" }
            }
        }
        return token
    }

    fun getDisplayName(phone: String): String {
        var name = phone
        withConn { conn ->
            conn.prepareStatement("SELECT display_name FROM users WHERE phone = ? LIMIT 1").use { ps ->
                ps.setString(1, phone)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val dbName = rs.getString("display_name")
                        if (!dbName.isNullOrBlank()) name = dbName
                    }
                }
            }
        }
        return name
    }

    fun findRegisteredUsers(phones: List<String>): List<RegisteredUserDto> {
        if (phones.isEmpty()) return emptyList()
        val results = mutableListOf<RegisteredUserDto>()
        withConn { conn ->
            val placeholders = phones.joinToString(",") { "?" }
            conn.prepareStatement("SELECT phone, display_name, about FROM users WHERE phone IN ($placeholders)").use { ps ->
                phones.forEachIndexed { idx, p -> ps.setString(idx + 1, p) }
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(RegisteredUserDto(rs.getString("phone"), rs.getString("display_name"), rs.getString("about")))
                    }
                }
            }
        }
        return results
    }

    fun queueOfflineMessage(targetPhone: String, payload: ChatPayload) {
        withConn { conn ->
            conn.prepareStatement(
                "INSERT INTO offline_messages (id, target_phone, payload_json, created_at) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING"
            ).use { ps ->
                ps.setString(1, "${payload.id}_$targetPhone"); ps.setString(2, targetPhone)
                ps.setString(3, json.encodeToString(payload)); ps.setLong(4, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    fun drainOfflineMessages(targetPhone: String): List<ChatPayload> {
        val list = mutableListOf<ChatPayload>()
        withConn { conn ->
            conn.prepareStatement("SELECT payload_json FROM offline_messages WHERE target_phone = ? ORDER BY created_at ASC").use { ps ->
                ps.setString(1, targetPhone)
                ps.executeQuery().use { rs ->
                    while (rs.next()) list.add(json.decodeFromString<ChatPayload>(rs.getString("payload_json")))
                }
            }
            if (list.isNotEmpty()) {
                conn.prepareStatement("DELETE FROM offline_messages WHERE target_phone = ?").use { del ->
                    del.setString(1, targetPhone); del.executeUpdate()
                }
            }
        }
        return list
    }

    private inline fun withConn(block: (Connection) -> Unit) {
        val ds = dataSource ?: return
        try { ds.connection.use(block) } catch (e: Exception) { println("DB ERROR: ${e.localizedMessage}") }
    }
}

fun main() {
    Db.init()
    Db.ensureTables()
    FcmSender.init()
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 20.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") { call.respondText("GlobalChat Server Online (${activeConnections.size} users connected)") }

        post("/auth/request-otp") {
            val body = json.decodeFromString<OtpRequestBody>(call.receiveText())
            val apiKey = System.getenv("BREVO_API_KEY").orEmpty().trim()
            val senderEmail = System.getenv("BREVO_SENDER_EMAIL").orEmpty().trim()
            if (apiKey.isBlank() || senderEmail.isBlank()) {
                call.respondText(json.encodeToString(OtpApiResult(false, "Missing Brevo keys")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                return@post
            }
            val code = (100000 + random.nextInt(900000)).toString()
            otpStore[body.phone.trim()] = PendingOtp(code, body.email.trim(), System.currentTimeMillis() + 10 * 60_000L)
            val brevoBody = BrevoEmailPayload(
                BrevoSender("GlobalChat", senderEmail),
                listOf(BrevoRecipient(body.email.trim())),
                "Your GlobalChat Verification Code: $code",
                "<div style='font-family:sans-serif;padding:20px'><h2>GlobalChat Verification</h2><p>Your code for <b>${body.phone}</b> is:</p><h1 style='color:#008069;letter-spacing:4px'>$code</h1><p>This code expires in 10 minutes.</p></div>"
            )
            val resp = httpClient.post("https://api.brevo.com/v3/smtp/email") {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(brevoBody))
            }
            if (resp.status.isSuccess()) call.respondText(json.encodeToString(OtpApiResult(true, "Code sent")), ContentType.Application.Json)
            else call.respondText(json.encodeToString(OtpApiResult(false, "Email failed (${resp.status.value})")), ContentType.Application.Json, HttpStatusCode.BadGateway)
        }

        post("/auth/verify-otp") {
            val body = json.decodeFromString<OtpVerifyBody>(call.receiveText())
            val phone = body.phone.trim()
            val pending = otpStore[phone]
            val valid = pending != null && System.currentTimeMillis() <= pending.expiresAtMs && pending.code == body.code.trim()
            if (valid) {
                otpStore.remove(phone)
                Db.upsertUser(phone, pending!!.email, null, null)
                call.respondText(json.encodeToString(OtpApiResult(true, "Verified")), ContentType.Application.Json)
            } else {
                call.respondText(json.encodeToString(OtpApiResult(false, "Invalid or expired code")), ContentType.Application.Json, HttpStatusCode.Unauthorized)
            }
        }

        post("/profile/sync") {
            val body = json.decodeFromString<ProfileSyncBody>(call.receiveText())
            Db.upsertUser(body.phone.trim(), null, body.displayName.trim(), body.about.trim())
            call.respondText(json.encodeToString(OtpApiResult(true, "Profile synced")), ContentType.Application.Json)
        }

        post("/profile/fcm-token") {
            val body = json.decodeFromString<FcmTokenBody>(call.receiveText())
            Db.saveFcmToken(body.phone.trim(), body.token.trim())
            call.respondText(json.encodeToString(OtpApiResult(true, "Token saved")), ContentType.Application.Json)
        }

        post("/contacts/sync") {
            val body = json.decodeFromString<ContactSyncRequest>(call.receiveText())
            val found = Db.findRegisteredUsers(body.phones.distinct().take(500))
            call.respondText(json.encodeToString(ContactSyncResponse(found)), ContentType.Application.Json)
        }

        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing userId"))
            activeConnections[userId] = this
            broadcastPresence(userId, true)
            activeConnections.keys.filter { it != userId }.forEach { onlineUser ->
                sendPayload(this, ChatPayload("presence-$onlineUser", onlineUser, onlineUser, listOf(userId), "ONLINE", System.currentTimeMillis(), type = "PRESENCE"))
            }

            Db.drainOfflineMessages(userId).forEach { msg ->
                sendPayload(this, msg)
                activeConnections[msg.senderId]?.let { s ->
                    sendPayload(s, msg.copy(type = "ACK_DELIVERED", status = MessageStatus.DELIVERED, mediaBase64 = null))
                }
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) handlePayload(json.decodeFromString(frame.readText()))
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
            senderSession?.let { sendPayload(it, msg.copy(type = "ACK_SENT", status = MessageStatus.SENT, mediaBase64 = null)) }
            var delivered = false
            val senderName by lazy { Db.getDisplayName(msg.senderId) }
            msg.receiverIds.filter { it != msg.senderId }.forEach { targetId ->
                val target = activeConnections[targetId]
                if (target != null) {
                    sendPayload(target, msg)
                    delivered = true
                } else {
                    Db.queueOfflineMessage(targetId, msg)
                    val fcmToken = Db.getFcmToken(targetId)
                    val title = if (msg.isGroup && !msg.groupName.isNullOrBlank()) "${msg.groupName} • $senderName" else senderName
                    FcmSender.sendPush(fcmToken, title, msg.content, if (msg.isGroup) msg.chatId else msg.senderId)
                }
            }
            if (delivered) {
                senderSession?.let { sendPayload(it, msg.copy(type = "ACK_DELIVERED", status = MessageStatus.DELIVERED, mediaBase64 = null)) }
            }
        }
        "ACK_READ", "TYPING", "REACTION", "DELETE" -> {
            msg.receiverIds.filter { it != msg.senderId }.forEach { targetId ->
                val target = activeConnections[targetId]
                if (target != null) sendPayload(target, msg)
                else if (msg.type != "TYPING") Db.queueOfflineMessage(targetId, msg)
            }
        }
        "CALL_OFFER" -> {
            val callerName by lazy { Db.getDisplayName(msg.senderId) }
            msg.receiverIds.filter { it != msg.senderId }.forEach { targetId ->
                val target = activeConnections[targetId]
                if (target != null) {
                    sendPayload(target, msg)
                } else {
                    val callKind = if (msg.callVideo) "Video" else "Voice"
                    FcmSender.sendPush(Db.getFcmToken(targetId), "Incoming $callKind Call", "$callerName is calling you on GlobalChat", msg.senderId)
                }
            }
        }
        "CALL_ANSWER", "CALL_ICE", "CALL_END" -> {
            msg.receiverIds.filter { it != msg.senderId }.forEach { targetId ->
                activeConnections[targetId]?.let { sendPayload(it, msg) }
            }
        }
    }
}

private suspend fun broadcastPresence(userId: String, isOnline: Boolean) {
    val presence = ChatPayload(
        "presence-$userId-${System.currentTimeMillis()}", userId, userId, emptyList(),
        if (isOnline) "ONLINE" else "OFFLINE", System.currentTimeMillis(), type = "PRESENCE"
    )
    activeConnections.forEach { (id, session) -> if (id != userId) sendPayload(session, presence) }
}

private suspend fun sendPayload(session: DefaultWebSocketServerSession, payload: ChatPayload) {
    try { session.send(Frame.Text(json.encodeToString(payload))) } catch (_: Exception) { }
}