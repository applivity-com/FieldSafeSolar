package com.example.fieldsafesolar.data.repository

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.fieldsafesolar.domain.SpeechOutputEngine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidTextToSpeechEngine(context: Context) : SpeechOutputEngine {

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pendingCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (ready) {
                    tts?.setSpeechRate(1.1f)
                    tts?.setPitch(0.9f)
                    tts?.voices
                        ?.filter { v -> !v.isNetworkConnectionRequired && v.locale.language == "en" }
                        ?.maxByOrNull { v -> v.quality }
                        ?.let { best -> tts?.voice = best }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            utteranceId?.let { id -> pendingCallbacks.remove(id)?.invoke() }
                        }
                        @Deprecated("Deprecated in API 21")
                        override fun onError(utteranceId: String?) {
                            utteranceId?.let { id -> pendingCallbacks.remove(id) }
                        }
                    })
                    Log.d("TTS", "TextToSpeech ready")
                } else {
                    Log.e("TTS", "Language not supported (result=$result)")
                }
            } else {
                Log.e("TTS", "TextToSpeech initialization failed: $status")
            }
        }
    }

    override fun speak(text: String) {
        if (!ready) {
            Log.w("TTS", "Not ready, dropping: $text")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fss_${System.currentTimeMillis()}")
    }

    override fun speakAndThen(text: String, onDone: () -> Unit) {
        if (!ready) {
            onDone()
            return
        }
        val id = "fss_cb_${System.currentTimeMillis()}"
        pendingCallbacks[id] = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stopSpeaking() {
        pendingCallbacks.clear()
        tts?.stop()
    }

    fun shutdown() {
        pendingCallbacks.clear()
        tts?.shutdown()
        tts = null
    }
}
