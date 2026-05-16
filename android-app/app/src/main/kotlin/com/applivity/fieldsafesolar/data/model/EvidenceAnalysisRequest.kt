package com.applivity.fieldsafesolar.data.model

data class EvidenceAnalysisRequest(
    val inspectionType: InspectionType,
    val checklistStep: String,
    val workerTranscript: String,
    val imageUri: String? = null,
    val currentSafetyState: Map<String, String> = emptyMap(),
    val visionFindings: VisionFindings = VisionFindings.EMPTY,
    val isFirstTurn: Boolean = false,   // true on first message of a session
    val standardRef: String? = null     // e.g. "NFPA 70E §130.7(C)(7)(a)"
)
