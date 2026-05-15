package com.example.fieldsafesolar.data.repository

import com.example.fieldsafesolar.domain.SpeechToTextEngine
import kotlinx.coroutines.delay

class FakeSpeechToTextEngine : SpeechToTextEngine {
    override suspend fun transcribeAudio(audioFilePath: String): String {
        delay(500) // Simulate network/processing delay
        return when {
            audioFilePath.contains("gloves_missing", ignoreCase = true) -> "I don't have gloves."
            audioFilePath.contains("gloves_present", ignoreCase = true) -> "Here are my gloves."
            audioFilePath.contains("lockout_tagout_present", ignoreCase = true) -> "Here is the lockout tag."
            audioFilePath.contains("lockout_tagout_not_verified", ignoreCase = true) -> "I'm not sure if it's de-energized."
            else -> "Default transcription for testing."
        }
    }
}