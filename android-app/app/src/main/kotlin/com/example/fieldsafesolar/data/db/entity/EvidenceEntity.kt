package com.example.fieldsafesolar.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fieldsafesolar.data.db.converters.InstantConverter
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.example.fieldsafesolar.data.model.SafetyReport
import java.time.Instant

/**
 * EvidenceEntity: Room entity for inspection evidence (photos, captions)
 */
@Entity(
    tableName = "evidence",
    foreignKeys = [
        ForeignKey(
            entity = SafetyReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(InstantConverter::class)
data class EvidenceEntity(
    @PrimaryKey
    val evidenceId: String,
    val reportId: String,
    val stepId: String, // Checklist item identifier
    val fileUri: String,
    val caption: String,
    val confidenceLevel: String,
    val createdAt: Instant
) {
    fun toDomain(): SafetyReport.Evidence {
        return SafetyReport.Evidence(
            evidenceId = evidenceId,
            stepId = stepId,
            fileUri = fileUri,
            caption = caption,
            analysisConfidence = EvidenceAnalysisResult.Confidence.valueOf(confidenceLevel)
        )
    }

    companion object {
        fun fromDomain(
            reportId: String,
            evidence: SafetyReport.Evidence
        ): EvidenceEntity {
            return EvidenceEntity(
                evidenceId = evidence.evidenceId,
                reportId = reportId,
                stepId = evidence.stepId,
                fileUri = evidence.fileUri,
                caption = evidence.caption,
                confidenceLevel = evidence.analysisConfidence.name,
                createdAt = Instant.now()
            )
        }
    }
}
