package com.globalchat.android.model

import kotlinx.serialization.Serializable

const val DEFAULT_SERVER_HOST = "globalchat-server.onrender.com"

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
data class ProfileSyncBody(val phone: String, val displayName: String, val about: String)

@Serializable
data class FcmTokenBody(val phone: String, val token: String)

@Serializable
data class ContactSyncRequest(val phones: List<String>)

@Serializable
data class RegisteredUserDto(val phone: String, val displayName: String, val about: String)

@Serializable
data class ContactSyncResponse(val users: List<RegisteredUserDto> = emptyList())

enum class OnboardingStep { WELCOME, PHONE, OTP, PROFILE, DONE }

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
    val about: String = ""
)

data class UserProfile(
    val phoneNumber: String = "",
    val displayName: String = "",
    val about: String = "Hey there! I am using GlobalChat.",
    val avatarBase64: String? = null,
    val serverHost: String = DEFAULT_SERVER_HOST,
    val readReceipts: Boolean = true,
    val isRegistered: Boolean = false
)

data class ActiveCallUiState(
    val partnerId: String,
    val title: String,
    val isVideo: Boolean
)