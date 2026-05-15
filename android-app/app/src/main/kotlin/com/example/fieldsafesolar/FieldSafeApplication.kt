package com.example.fieldsafesolar

import android.app.Application
import android.util.Log
import com.example.fieldsafesolar.data.repository.WhisperCppSpeechToTextEngine
import com.example.fieldsafesolar.di.ServiceProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class FieldSafeApplication : Application() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("FieldSafeApplication", "Initializing FieldSafe Solar")

        try {
            ServiceProvider.initialize(this)
            Log.d("FieldSafeApplication", "ServiceProvider initialized successfully")

            // Pre-warm Whisper in background so first transcription has no cold-start delay
            val stt = ServiceProvider.getSpeechToTextEngine()
            if (stt is WhisperCppSpeechToTextEngine) {
                GlobalScope.launch { stt.warmUp() }
            }
        } catch (e: Exception) {
            Log.e("FieldSafeApplication", "Failed to initialize ServiceProvider", e)
        }
    }
}
