package com.example.fieldsafesolar.domain

interface SpeechToTextEngine {
    suspend fun transcribeAudio(audioFilePath: String): String
}