package com.applivity.fieldsafesolar.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SafetyReport(
    val reportId: String,
    val inspectionType: InspectionType,
    val createdAt: Instant,
    val overallDecision: Decision,
    val severity: Severity,
    val summary: String,
    val observedConditions: List<String>,
    val workerConfirmations: List<String>,
    val unverifiedItems: List<String>,
    val recommendedActions: List<String>,
    val evidence: List<Evidence>,
    val limitations: List<String>,
    var syncStatus: SyncStatus,
    val applicableStandards: List<String> = emptyList(),
    val jhaConfirmed: Boolean = false,
    val eewpPermitLines: List<String> = emptyList(),
) {
    // Industry O&M defect classification (SEIA O&M, IEC 62446-2, Cypress Creek)
    val defectClass: DefectClass
        get() = when (overallDecision) {
            Decision.STOP_WORK -> DefectClass.CRITICAL
            Decision.FAIL -> DefectClass.MAJOR
            Decision.WARN -> DefectClass.MINOR
            Decision.PASS -> DefectClass.NONE
        }

    val correctiveActionLabel: String?
        get() = when (overallDecision) {
            Decision.STOP_WORK -> "CRITICAL — Immediate corrective action required. Do not resume work until hazard is resolved."
            Decision.FAIL -> "MAJOR — Corrective action required within 7 days (IEC 62446-2 / SEIA O&M)."
            Decision.WARN -> "MINOR — Address at next scheduled maintenance interval."
            Decision.PASS -> null
        }

    val correctiveActionDueDate: LocalDate?
        get() = when (overallDecision) {
            Decision.STOP_WORK -> createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
            Decision.FAIL -> createdAt.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(7)
            else -> null
        }

    enum class Decision {
        PASS, WARN, FAIL, STOP_WORK
    }

    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    enum class SyncStatus {
        LOCAL_ONLY, PENDING_SYNC, SYNCED, REVIEWED
    }

    enum class DefectClass {
        CRITICAL, MAJOR, MINOR, NONE
    }

    data class Evidence(
        val evidenceId: String,
        val stepId: String,
        val fileUri: String,
        val caption: String,
        val analysisConfidence: EvidenceAnalysisResult.Confidence
    )
}
