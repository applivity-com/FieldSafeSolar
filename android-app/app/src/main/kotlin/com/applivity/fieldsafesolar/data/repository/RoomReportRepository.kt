package com.applivity.fieldsafesolar.data.repository

import com.applivity.fieldsafesolar.data.db.dao.EvidenceDao
import com.applivity.fieldsafesolar.data.db.dao.SafetyReportDao
import com.applivity.fieldsafesolar.data.db.entity.EvidenceEntity
import com.applivity.fieldsafesolar.data.db.entity.SafetyReportEntity
import com.applivity.fieldsafesolar.data.model.SafetyReport
import com.applivity.fieldsafesolar.domain.ReportRepository

/**
 * RoomReportRepository: Room-backed implementation of ReportRepository
 */
class RoomReportRepository(
    private val safetyReportDao: SafetyReportDao,
    private val evidenceDao: EvidenceDao
) : ReportRepository {

    override suspend fun saveReport(report: SafetyReport) {
        val entity = SafetyReportEntity.fromDomain(report)
        safetyReportDao.insert(entity)

        // Save evidence separately
        report.evidence.forEach { evidence ->
            val evidenceEntity = EvidenceEntity.fromDomain(report.reportId, evidence)
            evidenceDao.insert(evidenceEntity)
        }
    }

    override suspend fun getReport(reportId: String): SafetyReport? {
        val entity = safetyReportDao.getById(reportId) ?: return null
        
        // Load evidence
        val evidenceEntities = evidenceDao.getByReportId(reportId)
        val evidence = evidenceEntities.map { it.toDomain() }

        // Reconstruct report with evidence
        return entity.toDomain().copy(evidence = evidence)
    }

    override suspend fun getAllReports(): List<SafetyReport> {
        val entities = safetyReportDao.getAllReports()
        return entities.map { entity ->
            val evidenceEntities = evidenceDao.getByReportId(entity.reportId)
            val evidence = evidenceEntities.map { it.toDomain() }
            entity.toDomain().copy(evidence = evidence)
        }
    }

    override suspend fun updateReportSyncStatus(
        reportId: String,
        status: SafetyReport.SyncStatus
    ) {
        safetyReportDao.updateSyncStatus(reportId, status.name)
    }

    suspend fun deleteReport(reportId: String) {
        safetyReportDao.deleteById(reportId)
        evidenceDao.deleteByReportId(reportId)
    }

    suspend fun getReportsByType(inspectionType: String): List<SafetyReport> {
        val entities = safetyReportDao.getReportsByType(inspectionType)
        return entities.map { entity ->
            val evidenceEntities = evidenceDao.getByReportId(entity.reportId)
            val evidence = evidenceEntities.map { it.toDomain() }
            entity.toDomain().copy(evidence = evidence)
        }
    }

    suspend fun getReportCount(): Int = safetyReportDao.getReportCount()
}

