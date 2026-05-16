package com.applivity.fieldsafesolar.data.model

data class AppSettings(
    val maxRecordingSeconds: Int = 15,
    val silenceTimeoutSeconds: Int = 4,
    val silenceMode: SilenceMode = SilenceMode.AUTO_CALIBRATE,
    val manualSilenceThreshold: Int = 2000,
    val ttsAutoReadQuestion: Boolean = true,
    val scanDurationSeconds: Int = 5,
) {
    enum class SilenceMode { AUTO_CALIBRATE, MANUAL }
}
