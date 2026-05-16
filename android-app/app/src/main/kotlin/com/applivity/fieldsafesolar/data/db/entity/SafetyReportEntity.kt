package com.applivity.fieldsafesolar.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.applivity.fieldsafesolar.data.db.converters.InstantConverter
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.data.model.SafetyReport
import java.time.Instant
import org.json.JSONArray

/**
 * SafetyReportEntity: Room entity for SafetyReport persistence
 */
@Entity(tableName = "safety_reports")
@TypeConverters(InstantConverter::class)
data class SafetyReportEntity(
    @PrimaryKey
    val reportId: String,
    val inspectionType: String,
    val createdAt: Instant,
    val overallDecision: String,
    val severity: String,
    val summary: String,
    val observedConditions: String, // JSON array
    val workerConfirmations: String, // JSON array
    val unverifiedItems: String, // JSON array
    val recommendedActions: String, // JSON array
    val limitations: String, // JSON array
    val syncStatus: String,
    val applicableStandards: String = "[]", // JSON array
    val jhaConfirmed: Boolean = false,
) {
    fun toDomain(): SafetyReport {
        return SafetyReport(
            reportId = reportId,
            inspectionType = InspectionType.valueOf(inspectionType),
            createdAt = createdAt,
            overallDecision = SafetyReport.Decision.valueOf(overallDecision),
            severity = SafetyReport.Severity.valueOf(severity),
            summary = summary,
            observedConditions = parseJsonArray(observedConditions),
            workerConfirmations = parseJsonArray(workerConfirmations),
            unverifiedItems = parseJsonArray(unverifiedItems),
            recommendedActions = parseJsonArray(recommendedActions),
            evidence = emptyList(),
            limitations = parseJsonArray(limitations),
            syncStatus = SafetyReport.SyncStatus.valueOf(syncStatus),
            applicableStandards = parseJsonArray(applicableStandards),
            jhaConfirmed = jhaConfirmed,
        )
    }

    private fun parseJsonArray(json: String): List<String> {
        if (json.isEmpty() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun fromDomain(report: SafetyReport): SafetyReportEntity {
            return SafetyReportEntity(
                reportId = report.reportId,
                inspectionType = report.inspectionType.name,
                createdAt = report.createdAt,
                overallDecision = report.overallDecision.name,
                severity = report.severity.name,
                summary = report.summary,
                observedConditions = toJsonArray(report.observedConditions),
                workerConfirmations = toJsonArray(report.workerConfirmations),
                unverifiedItems = toJsonArray(report.unverifiedItems),
                recommendedActions = toJsonArray(report.recommendedActions),
                limitations = toJsonArray(report.limitations),
                syncStatus = report.syncStatus.name,
                applicableStandards = toJsonArray(report.applicableStandards),
                jhaConfirmed = report.jhaConfirmed,
            )
        }

        private fun toJsonArray(list: List<String>): String {
            return if (list.isEmpty()) "[]"
            else "[" + list.joinToString(",") { "\"$it\"" } + "]"
        }
    }
}
