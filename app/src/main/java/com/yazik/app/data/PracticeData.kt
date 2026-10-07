package com.yazik.app.data

import androidx.room.Dao
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

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String,
    val mode: String,
    val title: String,
    val startedAt: Long,
    val updatedAt: Long,
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
    @PrimaryKey val id: Int = 1,
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

    @Query("SELECT * FROM learning_progress WHERE id = 1 LIMIT 1")
    fun observeProgress(): Flow<LearningProgress?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: LearningProgress)

    @Query("SELECT * FROM learning_progress WHERE id = 1 LIMIT 1")
    suspend fun getProgress(): LearningProgress?
}

@Database(
    entities = [Conversation::class, PracticeMessage::class, LearningProgress::class],
    version = 1,
    exportSchema = false
)
abstract class YazikDatabase : RoomDatabase() {
    abstract fun practiceDao(): PracticeDao
}
