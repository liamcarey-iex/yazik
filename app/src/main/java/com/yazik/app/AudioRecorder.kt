package com.yazik.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context, private val outputFile: File) {
    private var recorder: MediaRecorder? = null

    fun hasVoiceActivity(): Boolean = runCatching {
        (recorder?.maxAmplitude ?: 0) >= VOICE_ACTIVITY_THRESHOLD
    }.getOrDefault(false)

    fun start() {
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = instance
        } catch (error: Exception) {
            instance.release()
            outputFile.delete()
            throw error
        }
    }

    fun stop(): File {
        val current = recorder ?: error("Recording hasn't started.")
        recorder = null
        try {
            current.stop()
        } catch (error: RuntimeException) {
            outputFile.delete()
            throw error
        } finally {
            current.release()
        }
        return outputFile
    }

    fun cancel() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        outputFile.delete()
    }

    private companion object {
        // MediaRecorder amplitude is a peak value in the range 0–32767.
        const val VOICE_ACTIVITY_THRESHOLD = 1_200
    }
}
