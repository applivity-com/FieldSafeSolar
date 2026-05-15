package com.example.fieldsafesolar.data.model

data class ReportGenerationRequest(
    val inspectionType: InspectionType,
    val completedChecklist: List<ChecklistItem>,
    val voiceTranscripts: List<VoiceTranscript>,
    val overallDecision: SafetyReport.Decision,
    val severity: SafetyReport.Severity
)