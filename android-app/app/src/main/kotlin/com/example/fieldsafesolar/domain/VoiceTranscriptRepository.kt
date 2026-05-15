package com.example.fieldsafesolar.domain

import com.example.fieldsafesolar.data.model.VoiceTranscript

interface VoiceTranscriptRepository {
    suspend fun saveTranscript(transcript: VoiceTranscript)
    suspend fun getTranscriptsForReport(reportId: String): List<VoiceTranscript>
}