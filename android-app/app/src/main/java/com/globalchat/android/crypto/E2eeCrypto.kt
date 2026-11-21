package com.globalchat.android.crypto

import android.content.Context
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object E2eeCrypto {
    private const val PREFS = "globalchat_e2ee"
    private const val KEY_PRIV = "priv_ec_b64"
    private const val KEY_PUB = "pub_ec_b64"
    private const val PREFIX = "E2EE1:"
    private val random = SecureRandom()

    @Synchronized
    fun getOrCreateKeyPair(context: Context): KeyPair {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedPriv = prefs.getString(KEY_PRIV, null)
        val savedPub = prefs.getString(KEY_PUB, null)
        if (!savedPriv.isNullOrBlank() && !savedPub.isNullOrBlank()) {
            try {
                val kf = KeyFactory.getInstance("EC")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(savedPriv, Base64.NO_WRAP)))
                val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(savedPub, Base64.NO_WRAP)))
                return KeyPair(pub, priv)
            } catch (_: Exception) { }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), random)
        val kp = kpg.generateKeyPair()
        prefs.edit()
            .putString(KEY_PRIV, Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUB, Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
            .apply()
        return kp
    }

    fun getMyPublicKeyBase64(context: Context): String {
        val kp = getOrCreateKeyPair(context)
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    private fun deriveSharedAesKey(context: Context, partnerPubBase64: String): SecretKeySpec? {
        if (partnerPubBase64.isBlank()) return null
        return try {
            val myKp = getOrCreateKeyPair(context)
            val partnerPub = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.decode(partnerPubBase64, Base64.NO_WRAP)))
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(myKp.private)
            ka.doPhase(partnerPub, true)
            val sharedSecret = ka.generateSecret()
            val aesBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
            SecretKeySpec(aesBytes, "AES")
        } catch (_: Exception) {
            null
        }
    }

    fun encrypt(context: Context, plainText: String, partnerPubBase64: String): String? {
        val key = deriveSharedAesKey(context, partnerPubBase64) ?: return null
        return try {
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(context: Context, payloadText: String, senderPubBase64: String): String? {
        if (!payloadText.startsWith(PREFIX)) return payloadText
        val key = deriveSharedAesKey(context, senderPubBase64) ?: return null
        return try {
            val combined = Base64.decode(payloadText.removePrefix(PREFIX), Base64.NO_WRAP)
            if (combined.size <= 12) return null
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}