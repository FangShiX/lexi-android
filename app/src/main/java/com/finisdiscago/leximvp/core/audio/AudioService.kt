package com.finisdiscago.leximvp.core.audio

import kotlinx.coroutines.flow.StateFlow

enum class AudioAvailability {
    Initializing,
    Ready,
    MissingLanguageData,
    LanguageNotSupported,
    InitializationFailed,
    PlaybackFailed
}

fun AudioAvailability.userMessage(): String? = when (this) {
    AudioAvailability.Initializing -> "语音服务正在初始化"
    AudioAvailability.Ready -> null
    AudioAvailability.MissingLanguageData -> "缺少英语语音数据，仍可继续学习"
    AudioAvailability.LanguageNotSupported -> "当前语音引擎不支持英语，仍可继续学习"
    AudioAvailability.InitializationFailed -> "语音服务初始化失败，仍可继续学习"
    AudioAvailability.PlaybackFailed -> "语音播放失败，仍可继续学习"
}

interface AudioService {
    val availability: StateFlow<AudioAvailability>
    fun setSpeechRate(rate: Float)
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
