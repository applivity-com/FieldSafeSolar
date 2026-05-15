package com.example.fieldsafesolar.data.model

data class ChecklistItem(
    val id: String,
    val description: String,
    val type: ChecklistItemType,
    val required: Boolean,
    var status: Status = Status.PENDING,
    val evidenceUri: String? = null,
    val aiAnalysis: String? = null,
    val workerConfirmation: Boolean? = null,
    val standardRef: String? = null   // e.g. "NFPA 70E §130.7" or "OSHA 1910.147(d)(6)"
) {
    enum class Status {
        PENDING,
        COMPLETED_PASS,
        COMPLETED_WARN,
        COMPLETED_FAIL,
        COMPLETED_STOP_WORK
    }

    enum class ChecklistItemType {
        PHOTO_EVIDENCE,
        VERBAL_CONFIRMATION,
        AI_ANALYSIS,
        OPEN_ENDED
    }
}