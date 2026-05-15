package com.example.fieldsafesolar.domain

import android.net.Uri

/**
 * AudioRecorder interface: Abstracts voice recording operations
 */
interface AudioRecorder {
    /**
     * Start recording audio to file
     * Returns true if recording started successfully
     */
    suspend fun startRecording(): Boolean

    /**
     * Stop recording and get the URI to recorded file
     */
    suspend fun stopRecording(): Uri?

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean

    /**
     * Get duration of current recording in seconds
     */
    fun getRecordingDuration(): Int

    /**
     * Cancel recording without saving
     */
    fun cancelRecording()

    /**
     * RMS amplitude of the most recent audio buffer (0–32767 scale).
     * Returns 0 when not recording.
     */
    fun getCurrentAmplitude(): Float
}
