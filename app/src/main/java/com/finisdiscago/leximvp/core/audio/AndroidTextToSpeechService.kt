package com.finisdiscago.leximvp.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidTextToSpeechService(
    context: Context
) : AudioService, TextToSpeech.OnInitListener {
    private val applicationContext = context.applicationContext
    private val mutableAvailability = MutableStateFlow(AudioAvailability.Initializing)
    private val textToSpeech = TextToSpeech(applicationContext, this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCache = ConcurrentHashMap<String, File>()
    private val cacheDirectory = File(applicationContext.filesDir, "media/tts").apply { mkdirs() }
    private var mediaPlayer: MediaPlayer? = null
    private var speechRate = 0.9f

    override val availability: StateFlow<AudioAvailability> = mutableAvailability

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            mutableAvailability.value = AudioAvailability.InitializationFailed
            return
        }
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    val file = utteranceId?.let(pendingCache::remove) ?: return
                    if (file.length() > 0L) mainHandler.post { playCached(file) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let(pendingCache::remove)?.delete()
                    mutableAvailability.value = AudioAvailability.PlaybackFailed
                }
            }
        )
        val result = textToSpeech.setLanguage(Locale.US)
        textToSpeech.setSpeechRate(speechRate)
        mutableAvailability.value = when (result) {
            TextToSpeech.LANG_MISSING_DATA -> AudioAvailability.MissingLanguageData
            TextToSpeech.LANG_NOT_SUPPORTED -> AudioAvailability.LanguageNotSupported
            else -> AudioAvailability.Ready
        }
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 1.5f)
        if (mutableAvailability.value == AudioAvailability.Ready) {
            textToSpeech.setSpeechRate(speechRate)
        }
    }

    override fun speak(text: String) {
        if (mutableAvailability.value != AudioAvailability.Ready || text.isBlank()) return
        val normalized = text.trim()
        val cacheFile = File(cacheDirectory, "${cacheKey(normalized)}.wav")
        if (cacheFile.length() > 0L) {
            playCached(cacheFile)
            return
        }
        val utteranceId = "cache-${System.nanoTime()}"
        pendingCache[utteranceId] = cacheFile
        val result = textToSpeech.synthesizeToFile(normalized, null, cacheFile, utteranceId)
        if (result == TextToSpeech.ERROR) {
            pendingCache.remove(utteranceId)
            cacheFile.delete()
            speakWithoutCache(normalized)
        }
    }

    private fun speakWithoutCache(text: String) {
        val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-${System.nanoTime()}")
        if (result == TextToSpeech.ERROR) mutableAvailability.value = AudioAvailability.PlaybackFailed
    }

    private fun playCached(file: File) {
        runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { completed -> completed.release(); if (mediaPlayer === completed) mediaPlayer = null }
                setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (mediaPlayer === failed) mediaPlayer = null
                    mutableAvailability.value = AudioAvailability.PlaybackFailed
                    true
                }
                prepare()
                start()
            }
            mutableAvailability.value = AudioAvailability.Ready
        }.onFailure {
            file.delete()
            mutableAvailability.value = AudioAvailability.PlaybackFailed
        }
    }

    private fun cacheKey(text: String): String {
        val raw = "en-US|$speechRate|$text".toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
    }

    override fun stop() {
        textToSpeech.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun shutdown() {
        mutableAvailability.value = AudioAvailability.InitializationFailed
        textToSpeech.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        textToSpeech.shutdown()
    }
}
