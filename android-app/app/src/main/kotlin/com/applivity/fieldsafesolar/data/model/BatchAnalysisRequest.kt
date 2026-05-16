package com.applivity.fieldsafesolar.data.model

data class BatchAnalysisRequest(
    val inspectionType: InspectionType,
    val answers: List<QuestionAnswer>,
)
