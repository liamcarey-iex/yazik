package com.yazik.app.network

import android.content.Context
import com.yazik.app.data.PracticeMessage
import com.yazik.app.data.PracticeMode
import com.yazik.app.data.PracticeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiClient(context: Context, private val getApiKey: () -> String?) {
    private val cacheDir = context.cacheDir
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun reply(mode: PracticeMode, language: PracticeLanguage, history: List<PracticeMessage>): String = withContext(Dispatchers.IO) {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", tutorInstructions(mode, language)))
            history.takeLast(24).forEach { message ->
                put(JSONObject().put("role", message.role).put("content", message.content))
            }
        }
        val body = JSONObject()
            .put("model", TEXT_MODEL)
            .put("messages", messages)
            .put("max_completion_tokens", 450)
            .toString()

        val request = request("/v1/chat/completions")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val json = executeJson(request)
        json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("OpenAI returned an empty reply.")
    }

    suspend fun transcribe(recording: File, language: PracticeLanguage): String = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", TRANSCRIPTION_MODEL)
            .addFormDataPart("languages[]", language.code)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", "speech.m4a", recording.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val request = request("/v1/audio/transcriptions").post(requestBody).build()
        executeJson(request).optString("text").takeIf(String::isNotBlank)
            ?: throw IOException("I couldn't make out the recording. Try speaking a little more slowly.")
    }

    suspend fun speech(text: String): File = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", SPEECH_MODEL)
            .put("input", text.take(MAX_SPEECH_CHARS))
            .put("voice", "marin")
            .put("response_format", "mp3")
            .toString()
        val request = request("/v1/audio/speech")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw apiError(response.code, response.body?.string().orEmpty())
            val bytes = response.body?.bytes() ?: throw IOException("OpenAI returned no audio.")
            File.createTempFile("yazik-reply-", ".mp3", cacheDir).also { it.writeBytes(bytes) }
        }
    }

    private fun request(path: String): Request.Builder {
        val apiKey = getApiKey()?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Add your OpenAI API key in Settings to start practicing.")
        return Request.Builder()
            .url("https://api.openai.com$path")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
    }

    private fun executeJson(request: Request): JSONObject = http.newCall(request).execute().use { response ->
        val responseText = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw apiError(response.code, responseText)
        JSONObject(responseText)
    }

    private fun apiError(status: Int, body: String): IOException {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf(String::isNotBlank)
        val message = when (status) {
            401 -> "That API key was rejected. Check it in Settings."
            429 -> "OpenAI is rate-limiting this request or your API balance may be low. Try again later."
            in 500..599 -> "OpenAI is temporarily unavailable. Please try again shortly."
            else -> detail ?: "OpenAI request failed (HTTP $status)."
        }
        return IOException(message)
    }

    private fun tutorInstructions(mode: PracticeMode, language: PracticeLanguage): String {
        val style = if (mode == PracticeMode.GUIDED) {
            "Run a short structured beginner ${language.englishName} lesson. Start with one useful phrase, explain it simply, then ask one small question. " +
                "Adapt to the learner's answers and end with a quick recap when appropriate."
        } else {
            "Have a relaxed ${language.englishName} conversation about the learner's chosen topic. Keep turns short and invite them to respond."
        }
        return """
            You are Yazik, a patient ${language.englishName} tutor for an absolute beginner whose explanation language is English.
            $style
            Use simple ${language.englishName}, with English help where useful. After each learner message, first show a natural corrected version in ${language.englishName} if there is an error, then give one brief English explanation and continue the conversation with one question or prompt. If the learner's ${language.englishName} is already correct, say so briefly. Do not overwhelm them with grammar terminology. ${if (language == PracticeLanguage.RUSSIAN) "Use Cyrillic and optionally show transliteration only when it helps pronunciation." else "Use Japanese writing (kana and kanji); optionally include romaji only when it helps pronunciation."}
        """.trimIndent()
    }

    companion object {
        const val TEXT_MODEL = "gpt-6-luna"
        private const val TRANSCRIPTION_MODEL = "gpt-transcribe"
        private const val SPEECH_MODEL = "gpt-4o-mini-tts"
        private const val MAX_SPEECH_CHARS = 3500
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
