package com.applivity.fieldsafesolar.domain

interface SpeechToTextEngine {
    suspend fun transcribeAudio(audioFilePath: String): String
}