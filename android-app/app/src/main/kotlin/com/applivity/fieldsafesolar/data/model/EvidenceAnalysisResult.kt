package com.applivity.fieldsafesolar.data.model

data class EvidenceAnalysisResult(
    val stepId: String,
    val visibleItems: List<String>,
    val possibleHazards: List<String>,
    val missingOrUnclearItems: List<String>,
    val confidence: Confidence,
    val recommendedDecision: Decision,
    val workerFollowupQuestion: String?,
    val spokenSummary: String,
    val safetyReasoningSummary: String
) {
    enum class Confidence {
        LOW, MEDIUM, HIGH
    }

    enum class Decision {
        PASS, WARN, FAIL, STOP_WORK
    }
}