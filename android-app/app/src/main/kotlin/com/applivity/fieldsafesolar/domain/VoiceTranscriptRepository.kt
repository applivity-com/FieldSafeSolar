package com.applivity.fieldsafesolar.domain

import com.applivity.fieldsafesolar.data.model.VoiceTranscript

interface VoiceTranscriptRepository {
    suspend fun saveTranscript(transcript: VoiceTranscript)
    suspend fun getTranscriptsForReport(reportId: String): List<VoiceTranscript>
}