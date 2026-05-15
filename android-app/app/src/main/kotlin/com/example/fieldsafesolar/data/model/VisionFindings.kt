package com.example.fieldsafesolar.data.model

data class VisionDetection(
    val label: String,
    val confidence: Float,
    val safetyRelevance: SafetyRelevance
) {
    enum class SafetyRelevance {
        PPE_PRESENT,       // hard hat, gloves, safety vest — presence is good
        HAZARD_DETECTED,   // exposed wire, fire, water near electrical
        EQUIPMENT,         // panel, inverter, tools — neutral, context-dependent
        PERSON,            // worker detected
        IRRELEVANT
    }
}

data class VisionFindings(
    val detections: List<VisionDetection>,
    val evidencePhotoUri: String?,
    val scanDurationMs: Long
) {
    val ppeDetected: List<VisionDetection>
        get() = detections.filter { it.safetyRelevance == VisionDetection.SafetyRelevance.PPE_PRESENT }

    val hazardsDetected: List<VisionDetection>
        get() = detections.filter { it.safetyRelevance == VisionDetection.SafetyRelevance.HAZARD_DETECTED }

    fun toPromptText(): String {
        if (detections.isEmpty()) return "Visual scan: no relevant items detected."
        val sb = StringBuilder("Visual scan results:\n")
        detections.forEach { d ->
            val pct = (d.confidence * 100).toInt()
            sb.append("- ${d.label} ($pct% confidence, ${d.safetyRelevance.name})\n")
        }
        return sb.toString().trimEnd()
    }

    companion object {
        val EMPTY = VisionFindings(emptyList(), null, 0)
    }
}
