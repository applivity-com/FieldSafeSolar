package com.example.fieldsafesolar.domain

import com.example.fieldsafesolar.data.model.BatchAnalysisRequest
import com.example.fieldsafesolar.data.model.BatchAnalysisResult
import com.example.fieldsafesolar.data.model.EvidenceAnalysisRequest
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.example.fieldsafesolar.data.model.ReportGenerationRequest
import com.example.fieldsafesolar.data.model.SafetyReport

interface AiSafetyAnalyzer {
    suspend fun analyzeEvidence(request: EvidenceAnalysisRequest): EvidenceAnalysisResult
    suspend fun generateReport(request: ReportGenerationRequest): SafetyReport
    suspend fun analyzeFullChecklist(request: BatchAnalysisRequest): BatchAnalysisResult
}