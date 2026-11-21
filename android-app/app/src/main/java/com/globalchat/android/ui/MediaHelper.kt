package com.globalchat.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.globalchat.android.model.PhoneContact
import java.io.ByteArrayOutputStream
import java.io.File

object MediaHelper {
    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStartTime: Long = 0L
    private var player: MediaPlayer? = null

    fun uriToCompressedBase64(context: Context, uri: Uri, maxDim: Int = 720): String? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            if (original == null) return null

            val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val width = (original.width * ratio).toInt().coerceAtLeast(1)
            val height = (original.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, width, height, true)

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImage(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun startVoiceRecording(context: Context): Boolean {
        return try {
            stopVoiceRecordingInternal()
            val file = File(context.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(16000)
            mr.setAudioEncodingBitRate(24000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            recordFile = file
            recordStartTime = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopAndEncodeVoiceNote(): Pair<String, Int>? {
        val file = recordFile
        val durationSec = ((System.currentTimeMillis() - recordStartTime) / 1000L).toInt().coerceAtLeast(1)
        stopVoiceRecordingInternal()
        if (file == null || !file.exists()) return null
        return try {
            val bytes = file.readBytes()
            file.delete()
            if (bytes.isEmpty()) null else Base64.encodeToString(bytes, Base64.NO_WRAP) to durationSec
        } catch (_: Exception) {
            null
        }
    }

    private fun stopVoiceRecordingInternal() {
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        try {
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
    }

    fun playVoiceBase64(context: Context, base64: String, onFinished: () -> Unit) {
        stopPlayback()
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val temp = File(context.cacheDir, "play_${System.currentTimeMillis()}.m4a")
            temp.writeBytes(bytes)
            player = MediaPlayer().apply {
                setDataSource(temp.absolutePath)
                prepare()
                setOnCompletionListener {
                    temp.delete()
                    onFinished()
                }
                start()
            }
        } catch (_: Exception) {
            onFinished()
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) { }
        try {
            player?.release()
        } catch (_: Exception) { }
        player = null
    }

    fun readPhoneContacts(context: Context): List<PhoneContact> {
        val list = mutableListOf<PhoneContact>()
        val seen = mutableSetOf<String>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx)?.trim() ?: continue
                    val rawNum = it.getString(numIdx)?.replace(Regex("[\\s()-]"), "") ?: continue
                    if (rawNum.length >= 6 && seen.add(rawNum)) {
                        list.add(PhoneContact(name = name, phoneNumber = rawNum))
                    }
                }
            }
        } catch (_: SecurityException) { }
        return list
    }
}