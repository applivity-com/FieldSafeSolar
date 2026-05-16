package com.applivity.fieldsafesolar.data.model

data class QuestionAnswer(
    val questionId: String,
    val questionText: String,
    val standardRef: String?,
    val answerType: AnswerType,
    val customTranscript: String? = null,
    val photoFindings: List<String> = emptyList(),
    val ocrText: String? = null,
    val photoPath: String? = null,
) {
    enum class AnswerType { YES, NO, SKIP, CUSTOM }

    val isSkipped: Boolean get() = answerType == AnswerType.SKIP

    fun toPromptText(): String = buildString {
        append("${questionText.trimEnd('.')}: ")
        when (answerType) {
            AnswerType.YES -> append("YES (confirmed)")
            AnswerType.NO -> append("NO (not confirmed)")
            AnswerType.SKIP -> append("SKIPPED (not answered)")
            AnswerType.CUSTOM -> append("CUSTOM — \"${customTranscript ?: ""}\"")
        }
        if (photoFindings.isNotEmpty()) {
            append(" | Photo evidence: ${photoFindings.joinToString(", ")}")
        }
        if (!ocrText.isNullOrBlank()) {
            append(" | OCR text: $ocrText")
        }
        if (standardRef != null) {
            append(" [$standardRef]")
        }
    }
}
