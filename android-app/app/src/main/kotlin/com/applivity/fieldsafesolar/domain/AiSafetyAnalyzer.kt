package com.applivity.fieldsafesolar.domain

import com.applivity.fieldsafesolar.data.model.BatchAnalysisRequest
import com.applivity.fieldsafesolar.data.model.BatchAnalysisResult
import com.applivity.fieldsafesolar.data.model.EvidenceAnalysisRequest
import com.applivity.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.applivity.fieldsafesolar.data.model.ReportGenerationRequest
import com.applivity.fieldsafesolar.data.model.SafetyReport

interface AiSafetyAnalyzer {
    suspend fun analyzeEvidence(request: EvidenceAnalysisRequest): EvidenceAnalysisResult
    suspend fun generateReport(request: ReportGenerationRequest): SafetyReport
    suspend fun analyzeFullChecklist(request: BatchAnalysisRequest): BatchAnalysisResult
}