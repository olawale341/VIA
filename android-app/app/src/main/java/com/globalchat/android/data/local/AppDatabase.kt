package com.globalchat.android.data.local

import android.content.Context
import androidx.room.*
import com.globalchat.android.model.MessageStatus
import com.globalchat.android.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val title: String,
    val isGroup: Boolean,
    val memberIdsCsv: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val mediaType: String = "TEXT",
    val mediaBase64: String? = null,
    val durationSec: Int = 0,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val reaction: String? = null,
    val isDeleted: Boolean = false
)

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey val id: String,
    val ownerName: String,
    val ownerId: String,
    val caption: String,
    val imageBase64: String? = null,
    val bgColorHex: Long = 0xFF075E54,
    val timestamp: Long,
    val viewed: Boolean = false
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val partnerId: String,
    val title: String,
    val timestamp: Long,
    val isVideo: Boolean,
    val isMissed: Boolean = false,
    val isIncoming: Boolean = false
)

class Converters {
    @TypeConverter
    fun fromStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastTimestamp DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    suspend fun getChat(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    suspend fun clearUnread(chatId: String)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateReaction(messageId: String, reaction: String?)

    @Query("UPDATE messages SET content = 'This message was deleted', mediaType = 'TEXT', mediaBase64 = NULL, isDeleted = 1, reaction = NULL WHERE id = :messageId")
    suspend fun markDeleted(messageId: String)

    @Query("SELECT * FROM statuses ORDER BY timestamp DESC")
    fun observeStatuses(): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity)

    @Query("UPDATE statuses SET viewed = 1 WHERE id = :id")
    suspend fun markStatusViewed(id: String)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeCalls(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallLogEntity)

    @Query("DELETE FROM chats")
    suspend fun clearAllChats()

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

@Database(
    entities = [ChatEntity::class, MessageEntity::class, StatusEntity::class, CallLogEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "global_chat_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class ProfilePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("globalchat_prefs", Context.MODE_PRIVATE)

    fun load(): UserProfile = UserProfile(
        phoneNumber = prefs.getString("phone", "") ?: "",
        displayName = prefs.getString("name", "") ?: "",
        about = prefs.getString("about", "Hey there! I am using GlobalChat.") ?: "Hey there! I am using GlobalChat.",
        avatarBase64 = prefs.getString("avatar", null),
        serverHost = prefs.getString("server", "10.0.2.2:8080") ?: "10.0.2.2:8080",
        readReceipts = prefs.getBoolean("read_receipts", true),
        isRegistered = prefs.getBoolean("registered", false)
    )

    fun save(profile: UserProfile) {
        prefs.edit()
            .putString("phone", profile.phoneNumber)
            .putString("name", profile.displayName)
            .putString("about", profile.about)
            .putString("avatar", profile.avatarBase64)
            .putString("server", profile.serverHost)
            .putBoolean("read_receipts", profile.readReceipts)
            .putBoolean("registered", profile.isRegistered)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}