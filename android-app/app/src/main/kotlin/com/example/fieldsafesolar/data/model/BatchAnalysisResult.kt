package com.example.fieldsafesolar.data.model

data class BatchAnalysisResult(
    val verdict: Verdict,
    val summary: String,
    val observations: List<String>,
    val recommendedActions: List<String>,
    val followUpQuestions: List<String>,
    val safetyReasoningSummary: String,
    val applicableStandards: List<String>,
) {
    enum class Verdict { GO, CAUTION, STOP_WORK }
}
