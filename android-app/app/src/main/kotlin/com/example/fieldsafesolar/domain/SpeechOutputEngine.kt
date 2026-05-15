package com.example.fieldsafesolar.domain

interface SpeechOutputEngine {
    fun speak(text: String)
    fun speakAndThen(text: String, onDone: () -> Unit)
    fun stopSpeaking()
}