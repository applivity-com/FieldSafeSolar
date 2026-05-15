package com.example.fieldsafesolar.domain

import com.example.fieldsafesolar.data.model.SafetyReport

interface ReportRepository {
    suspend fun saveReport(report: SafetyReport)
    suspend fun getReport(reportId: String): SafetyReport?
    suspend fun getAllReports(): List<SafetyReport>
    suspend fun updateReportSyncStatus(reportId: String, status: SafetyReport.SyncStatus)
}