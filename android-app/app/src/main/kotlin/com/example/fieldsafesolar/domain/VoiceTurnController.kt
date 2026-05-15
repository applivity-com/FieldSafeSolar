package com.example.fieldsafesolar.domain

import android.net.Uri

interface VoiceTurnController {
    suspend fun startVoiceInput(): Uri? // Returns URI of recorded audio file
    suspend fun processVoiceTurn(audioUri: Uri, inspectionState: String): String // Returns spoken response
}