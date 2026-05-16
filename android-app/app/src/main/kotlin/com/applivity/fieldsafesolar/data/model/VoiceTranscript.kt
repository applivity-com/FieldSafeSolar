package com.applivity.fieldsafesolar.data.model

import java.time.Instant

data class VoiceTranscript(
    val id: String,
    val reportId: String,
    val timestamp: Instant,
    val userSpeech: String,
    val aiResponse: String?,
    val audioUri: String
)