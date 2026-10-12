package com.yazik.app.data

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class PracticeMode(val label: String) {
    GUIDED("Guided lesson"),
    FREE("Free conversation")
}

enum class PracticeLanguage(val code: String, val englishName: String, val nativeName: String) {
    RUSSIAN("ru", "Russian", "Русский"),
    JAPANESE("ja", "Japanese", "日本語");

    companion object {
        fun fromCode(code: String?): PracticeLanguage = entries.firstOrNull { it.code == code } ?: RUSSIAN
    }
}

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String,
    val mode: String,
    val title: String,
    val startedAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'ru'") val language: String = "ru",
    val completedAt: Long? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Conversation::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId")]
)
data class PracticeMessage(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Entity(tableName = "learning_progress")
data class LearningProgress(
    @PrimaryKey val language: String = "ru",
    val completedLessons: Int = 0,
    val practiceTurns: Int = 0,
    val lastActivityAt: Long? = null
)

@Dao
interface PracticeDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConversation(conversation: Conversation)

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touchConversation(id: String, time: Long)

    @Query("UPDATE conversations SET completedAt = :time WHERE id = :id AND completedAt IS NULL")
    suspend fun completeConversation(id: String, time: Long): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<PracticeMessage>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessages(conversationId: String): List<PracticeMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMessage(message: PracticeMessage)

    @Query("SELECT * FROM learning_progress")
    fun observeProgress(): Flow<List<LearningProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: LearningProgress)

    @Query("SELECT * FROM learning_progress WHERE language = :language LIMIT 1")
    suspend fun getProgress(language: String): LearningProgress?
}

@Database(
    entities = [Conversation::class, PracticeMessage::class, LearningProgress::class],
    version = 2,
    exportSchema = false
)
abstract class YazikDatabase : RoomDatabase() {
    abstract fun practiceDao(): PracticeDao
}
