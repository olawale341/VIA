package com.globalchat.android.model

import kotlinx.serialization.Serializable

const val DEFAULT_SERVER_HOST = "globalchat-server.onrender.com"
const val DELETE_FOR_EVERYONE_LIMIT_MS = 48L * 60L * 60L * 1000L

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
    val sdpMLineIndex: Int = 0,
    val isEncrypted: Boolean = false,
    val avatarVersion: Long = 0L,
    val adminIdsCsv: String = ""
)

@Serializable
data class OtpRequestBody(val phone: String, val email: String)

@Serializable
data class OtpVerifyBody(val phone: String, val code: String)

@Serializable
data class OtpApiResult(val ok: Boolean, val message: String, val avatarVersion: Long = 0L)

@Serializable
data class ProfileSyncBody(val phone: String, val displayName: String, val about: String, val avatarBase64: String? = null)

@Serializable
data class FcmTokenBody(val phone: String, val token: String)

@Serializable
data class PublicKeyBody(val phone: String, val publicKey: String)

@Serializable
data class PublicKeyResponse(val phone: String, val publicKey: String = "")

@Serializable
data class ContactSyncRequest(val phones: List<String>)

@Serializable
data class RegisteredUserDto(
    val phone: String,
    val displayName: String,
    val about: String,
    val avatarBase64: String? = null,
    val avatarVersion: Long = 0L
)

@Serializable
data class ContactSyncResponse(val users: List<RegisteredUserDto> = emptyList())

@Serializable
data class GroupSyncBody(val chatId: String, val title: String, val adminPhonesCsv: String, val memberPhonesCsv: String)

@Serializable
data class GroupInfoResponse(
    val chatId: String,
    val title: String,
    val adminPhonesCsv: String,
    val members: List<RegisteredUserDto> = emptyList()
)

@Serializable
data class StatusDto(
    val id: String,
    val ownerPhone: String,
    val ownerName: String,
    val ownerAvatar: String? = null,
    val caption: String,
    val imageBase64: String? = null,
    val bgColorHex: Long = 0xFF005C4B,
    val timestamp: Long
)

@Serializable
data class StatusListResponse(val statuses: List<StatusDto> = emptyList())

enum class OnboardingStep { WELCOME, PHONE, OTP, PROFILE, DONE }

enum class CallStatus { OUTGOING_RINGING, INCOMING_RINGING, CONNECTING, CONNECTED }

data class CountryOption(val name: String, val dialCode: String, val flag: String)

val supportedCountries = listOf(
    CountryOption("Nigeria", "+234", "🇳🇬"),
    CountryOption("Ghana", "+233", "🇬🇭"),
    CountryOption("Kenya", "+254", "🇰🇪"),
    CountryOption("South Africa", "+27", "🇿🇦"),
    CountryOption("United Kingdom", "+44", "🇬🇧"),
    CountryOption("United States", "+1", "🇺🇸"),
    CountryOption("India", "+91", "🇮🇳")
)

data class PhoneContact(
    val name: String,
    val phoneNumber: String,
    val isOnGlobalChat: Boolean = false,
    val about: String = "",
    val avatarBase64: String? = null,
    val avatarVersion: Long = 0L
)

data class UserProfile(
    val phoneNumber: String = "",
    val displayName: String = "",
    val about: String = "Hey there! I am using GlobalChat.",
    val avatarBase64: String? = null,
    val avatarVersion: Long = 0L,
    val serverHost: String = DEFAULT_SERVER_HOST,
    val readReceipts: Boolean = true,
    val isRegistered: Boolean = false,
    val themeMode: String = "SYSTEM"
)

data class ActiveCallUiState(
    val partnerId: String,
    val title: String,
    val isVideo: Boolean,
    val status: CallStatus = CallStatus.OUTGOING_RINGING,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val partnerAvatarBase64: String? = null,
    val partnerAvatarVersion: Long = 0L
)

data class UploadStatusUi(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val isError: Boolean = false
)