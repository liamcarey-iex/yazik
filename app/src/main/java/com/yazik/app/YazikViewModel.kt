package com.yazik.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yazik.app.data.Conversation
import com.yazik.app.data.LearningProgress
import com.yazik.app.data.PracticeMessage
import com.yazik.app.data.PracticeMode
import com.yazik.app.data.PracticeLanguage
import com.yazik.app.data.YazikDatabase
import com.yazik.app.network.OpenAiClient
import com.yazik.app.security.ApiKeyVault
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { LANGUAGE, HOME, CHAT, SETTINGS }

data class YazikUiState(
    val screen: AppScreen = AppScreen.LANGUAGE,
    val language: PracticeLanguage = PracticeLanguage.RUSSIAN,
    val languageChosen: Boolean = false,
    val hasApiKey: Boolean = false,
    val mode: PracticeMode = PracticeMode.GUIDED,
    val conversationId: String? = null,
    val messages: List<PracticeMessage> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val progress: LearningProgress = LearningProgress(),
    val busy: Boolean = false,
    val error: String? = null
)

class YazikViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("yazik_preferences", 0)
    private val savedLanguage = preferences.getString("language", null)
    private val database = Room.databaseBuilder(application, YazikDatabase::class.java, "yazik.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    private val dao = database.practiceDao()
    private val keyVault = ApiKeyVault(application)
    private val api = OpenAiClient(application) { keyVault.get() }

    private val _state = MutableStateFlow(
        YazikUiState(
            screen = if (savedLanguage == null) AppScreen.LANGUAGE else AppScreen.HOME,
            language = PracticeLanguage.fromCode(savedLanguage),
            languageChosen = savedLanguage != null,
            hasApiKey = keyVault.hasKey()
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(dao.observeConversations(), dao.observeProgress()) { conversations, progress ->
                conversations to progress
            }.collect { (conversations, progress) ->
                _state.update { state ->
                    state.copy(conversations = conversations, progress = progress.firstOrNull { it.language == state.language.code } ?: LearningProgress(language = state.language.code))
                }
            }
        }
    }

    fun chooseLanguage(language: PracticeLanguage) {
        preferences.edit().putString("language", language.code).apply()
        _state.update {
            it.copy(language = language, languageChosen = true, screen = AppScreen.HOME, progress = LearningProgress(language = language.code), error = null)
        }
        viewModelScope.launch {
            val progress = dao.getProgress(language.code) ?: LearningProgress(language = language.code)
            _state.update { if (it.language == language) it.copy(progress = progress) else it }
        }
    }

    fun openSettings() = _state.update { it.copy(screen = AppScreen.SETTINGS, error = null) }
    fun openHome() = _state.update { it.copy(screen = AppScreen.HOME, error = null) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun saveApiKey(key: String) {
        runCatching { keyVault.put(key) }
            .onSuccess { _state.update { it.copy(hasApiKey = true, error = "API key saved on this phone.") } }
            .onFailure { showError(it.message ?: "Couldn't save that API key.") }
    }

    fun deleteApiKey() {
        runCatching { keyVault.clear() }
            .onSuccess { _state.update { it.copy(hasApiKey = false, error = "API key removed from this phone.") } }
            .onFailure { showError(it.message ?: "Couldn't remove the API key.") }
    }

    fun startConversation(mode: PracticeMode) {
        if (!keyVault.hasKey()) {
            _state.update { it.copy(screen = AppScreen.SETTINGS, error = "Add your OpenAI API key to begin.") }
            return
        }
        viewModelScope.launch {
            val language = _state.value.language
            val now = System.currentTimeMillis()
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                mode = mode.name,
                title = if (mode == PracticeMode.GUIDED) "${language.englishName} beginner lesson" else "Free ${language.englishName} practice",
                startedAt = now,
                updatedAt = now,
                language = language.code
            )
            dao.saveConversation(conversation)
            _state.update {
                it.copy(screen = AppScreen.CHAT, mode = mode, conversationId = conversation.id, messages = emptyList(), error = null)
            }
            if (mode == PracticeMode.GUIDED) {
                sendText("Let's begin a beginner ${language.englishName} lesson.", showUserMessage = false)
            }
        }
    }

    fun resumeConversation(conversation: Conversation) {
        viewModelScope.launch {
            val messages = dao.getMessages(conversation.id)
            _state.update {
                it.copy(
                    screen = AppScreen.CHAT,
                    mode = runCatching { PracticeMode.valueOf(conversation.mode) }.getOrDefault(PracticeMode.GUIDED),
                    language = PracticeLanguage.fromCode(conversation.language),
                    conversationId = conversation.id,
                    messages = messages,
                    error = null
                )
            }
            preferences.edit().putString("language", PracticeLanguage.fromCode(conversation.language).code).apply()
        }
    }

    fun sendText(text: String, showUserMessage: Boolean = true) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.busy) return
        val conversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                if (showUserMessage) saveMessage(conversationId, "user", clean)
                val history = dao.getMessages(conversationId).ifEmpty {
                    listOf(
                        PracticeMessage(
                            id = "opening-prompt",
                            conversationId = conversationId,
                            role = "user",
                            content = "Start the first short beginner ${_state.value.language.englishName} lesson now.",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                val answer = api.reply(_state.value.mode, _state.value.language, history)
                saveMessage(conversationId, "assistant", answer)
                bumpTurnCount()
            } catch (error: Exception) {
                showError(error.message ?: "Couldn't reach OpenAI. Check your connection and try again.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun sendVoice(recording: File) {
        val conversationId = _state.value.conversationId ?: run { recording.delete(); return }
        if (_state.value.busy) { recording.delete(); return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val language = _state.value.language
                val recognizedText = api.transcribe(recording, language)
                saveMessage(conversationId, "user", recognizedText)
                val answer = api.reply(_state.value.mode, language, dao.getMessages(conversationId))
                saveMessage(conversationId, "assistant", answer)
                bumpTurnCount()
            } catch (error: Exception) {
                showError(error.message ?: "Couldn't process that recording. Please try again.")
            } finally {
                recording.delete()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun createSpeech(text: String): File = api.speech(text)

    fun finishConversation() {
        if (_state.value.busy) {
            showError("Wait for the current reply to finish before ending this session.")
            return
        }
        val id = _state.value.conversationId
        viewModelScope.launch {
            if (id != null) {
                val changed = dao.completeConversation(id, System.currentTimeMillis())
                if (changed > 0 && _state.value.mode == PracticeMode.GUIDED) {
                    val language = _state.value.language
                    val old = dao.getProgress(language.code) ?: LearningProgress(language = language.code)
                    dao.saveProgress(old.copy(completedLessons = old.completedLessons + 1))
                }
            }
            _state.update { it.copy(screen = AppScreen.HOME, conversationId = null, messages = emptyList(), busy = false, error = null) }
        }
    }

    private suspend fun saveMessage(conversationId: String, role: String, content: String) {
        val message = PracticeMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        dao.saveMessage(message)
        dao.touchConversation(conversationId, message.createdAt)
        val messages = dao.getMessages(conversationId)
        _state.update { it.copy(messages = messages) }
    }

    private suspend fun bumpTurnCount() = withContext(Dispatchers.IO) {
        val language = _state.value.language
        val progress = dao.getProgress(language.code) ?: LearningProgress(language = language.code)
        dao.saveProgress(progress.copy(practiceTurns = progress.practiceTurns + 1, lastActivityAt = System.currentTimeMillis()))
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN language TEXT NOT NULL DEFAULT 'ru'")
                db.execSQL("CREATE TABLE learning_progress_new (language TEXT NOT NULL PRIMARY KEY, completedLessons INTEGER NOT NULL, practiceTurns INTEGER NOT NULL, lastActivityAt INTEGER)")
                db.execSQL("INSERT INTO learning_progress_new (language, completedLessons, practiceTurns, lastActivityAt) SELECT 'ru', completedLessons, practiceTurns, lastActivityAt FROM learning_progress WHERE id = 1")
                db.execSQL("DROP TABLE learning_progress")
                db.execSQL("ALTER TABLE learning_progress_new RENAME TO learning_progress")
            }
        }
    }
}
