package com.example.fieldsafesolar.data.repository

import android.content.Context
import com.example.fieldsafesolar.data.model.AppSettings

class AppSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("fss_settings", Context.MODE_PRIVATE)

    fun getSettings() = AppSettings(
        maxRecordingSeconds = prefs.getInt("max_recording_s", 15),
        silenceTimeoutSeconds = prefs.getInt("silence_timeout_s", 4),
        silenceMode = AppSettings.SilenceMode.valueOf(
            prefs.getString("silence_mode", AppSettings.SilenceMode.AUTO_CALIBRATE.name)!!
        ),
        manualSilenceThreshold = prefs.getInt("manual_threshold", 2000),
        ttsAutoReadQuestion = prefs.getBoolean("tts_auto_read", true),
        scanDurationSeconds = prefs.getInt("scan_duration_s", 5),
    )

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putInt("max_recording_s", s.maxRecordingSeconds)
            .putInt("silence_timeout_s", s.silenceTimeoutSeconds)
            .putString("silence_mode", s.silenceMode.name)
            .putInt("manual_threshold", s.manualSilenceThreshold)
            .putBoolean("tts_auto_read", s.ttsAutoReadQuestion)
            .putInt("scan_duration_s", s.scanDurationSeconds)
            .apply()
    }
}
