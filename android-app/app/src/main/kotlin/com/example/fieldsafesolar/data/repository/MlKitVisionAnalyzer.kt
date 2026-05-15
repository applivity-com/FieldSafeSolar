package com.example.fieldsafesolar.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.fieldsafesolar.data.model.VisionDetection
import com.example.fieldsafesolar.data.model.VisionFindings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MlKitVisionAnalyzer {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.35f)
            .build()
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyzeFrame(bitmap: Bitmap): List<VisionDetection> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labels = suspendCancellableCoroutine<List<com.google.mlkit.vision.label.ImageLabel>> { cont ->
            labeler.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit labeling failed: ${e.message}")
                    cont.resume(emptyList())
                }
        }
        return labels.mapNotNull { label -> mapToDetection(label.text, label.confidence) }
    }

    suspend fun extractText(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text.trim()
                    cont.resume(if (text.isBlank() || isNoisyOcr(text)) null else text)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    private fun isNoisyOcr(text: String): Boolean {
        if (Regex("""\d{3,}x\d{3,}""").containsMatchIn(text)) return true   // e.g. "1024x683"
        if (Regex("""\.\w{2,4}\b""").containsMatchIn(text)) return true      // e.g. ".png", ".txt"
        val meaningfulWords = text.split(Regex("\\s+")).count { it.length > 2 && it.any { c -> c.isLetter() } }
        return meaningfulWords < 3
    }

    fun buildFindings(
        allDetections: List<VisionDetection>,
        evidencePhotoUri: String?,
        scanDurationMs: Long
    ): VisionFindings {
        // Deduplicate: keep highest-confidence detection per label
        val deduped = allDetections
            .groupBy { it.label }
            .map { (_, hits) -> hits.maxByOrNull { it.confidence }!! }
            .filter { it.safetyRelevance != VisionDetection.SafetyRelevance.IRRELEVANT }
            .sortedByDescending { it.confidence }
        return VisionFindings(deduped, evidencePhotoUri, scanDurationMs)
    }

    private fun mapToDetection(label: String, confidence: Float): VisionDetection? {
        val lower = label.lowercase()
        val relevance = when {
            PPE_LABELS.any { lower.contains(it) } -> VisionDetection.SafetyRelevance.PPE_PRESENT
            HAZARD_LABELS.any { lower.contains(it) } -> VisionDetection.SafetyRelevance.HAZARD_DETECTED
            EQUIPMENT_LABELS.any { lower.contains(it) } -> VisionDetection.SafetyRelevance.EQUIPMENT
            lower.contains("person") || lower.contains("human") || lower.contains("man") || lower.contains("woman") ->
                VisionDetection.SafetyRelevance.PERSON
            else -> return null  // drop irrelevant labels entirely
        }
        val displayLabel = DISPLAY_NAMES[label] ?: label
        return VisionDetection(displayLabel, confidence, relevance)
    }

    companion object {
        private const val TAG = "MlKitVisionAnalyzer"

        private val PPE_LABELS = setOf(
            "hard hat", "helmet", "headgear", "hard hats",
            "glove", "gloves", "rubber glove",
            "safety vest", "vest", "high-visibility", "high visibility", "reflective",
            "safety glasses", "goggles", "eye protection", "glasses",
            "safety boot", "boot", "boots",
            "personal protective equipment", "ppe",
            "harness", "safety harness", "lanyard",
            "face shield", "respirator", "mask"
        )

        private val HAZARD_LABELS = setOf(
            "fire", "flame", "smoke",
            "wire", "cable", "electrical", "electricity",
            "water", "liquid", "puddle", "moisture",
            "ladder", "scaffold", "scaffolding",
            "warning", "danger", "caution", "hazard",
            "explosion", "arc", "spark"
        )

        private val EQUIPMENT_LABELS = setOf(
            "panel", "solar panel", "inverter", "breaker", "switchboard",
            "electrical panel", "circuit breaker", "fuse box",
            "tool", "wrench", "screwdriver", "multimeter", "voltmeter",
            "lock", "padlock", "lockout", "tag",
            "battery", "charger", "solar", "photovoltaic"
        )

        // Human-readable labels for display
        private val DISPLAY_NAMES = mapOf(
            "Hard hat" to "Hard hat",
            "Helmet" to "Hard hat / Helmet",
            "Personal protective equipment" to "PPE detected",
            "Glove" to "Gloves",
            "Safety vest" to "Safety vest",
            "High-visibility clothing" to "Hi-vis vest",
            "Safety glasses" to "Eye protection",
            "Electrical wiring" to "Exposed wiring",
            "Electric arc" to "⚠ Arc hazard",
            "Fire" to "⚠ Fire hazard"
        )
    }
}
