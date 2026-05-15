package com.example.fieldsafesolar.data.repository

import android.content.Context
import android.util.Log
import com.example.fieldsafesolar.data.model.BatchAnalysisRequest
import com.example.fieldsafesolar.data.model.BatchAnalysisResult
import com.example.fieldsafesolar.data.model.EvidenceAnalysisRequest
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.example.fieldsafesolar.data.model.InspectionType
import com.example.fieldsafesolar.data.model.ReportGenerationRequest
import com.example.fieldsafesolar.data.model.SafetyReport
import com.example.fieldsafesolar.data.model.VisionFindings
import com.example.fieldsafesolar.domain.AiSafetyAnalyzer
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class GemmaEdgeAnalyzer(private val context: Context) : AiSafetyAnalyzer {

    private var engine: Engine? = null
    private val loadMutex = Mutex()
    private var modelLoaded = false
    private val fallback = DemoStubAnalyzer()

    private val _statusFlow = MutableStateFlow("Idle")
    val statusFlow: StateFlow<String> = _statusFlow

    // Multi-turn session state — one persistent conversation per inspection
    private var activeConversation: Conversation? = null
    private var sessionVisionFindings: VisionFindings = VisionFindings.EMPTY
    private var sessionInspectionType: InspectionType? = null
    private var sessionTurnCount = 0

    companion object {
        private const val TAG = "GemmaEdgeAnalyzer"
        private const val MODEL_E2B = "gemma-4-E2B-it.litertlm"
    }

    private suspend fun ensureModelLoaded() {
        if (modelLoaded) return
        loadMutex.withLock {
            if (modelLoaded) return
            _statusFlow.value = "Loading model…"
            val modelsDir = (context.getExternalFilesDir(null) ?: run {
                Log.w(TAG, "External storage unavailable — falling back to internal filesDir")
                context.filesDir
            }).resolve("models")
            for (modelName in listOf(MODEL_E2B)) {
                val modelFile = modelsDir.resolve(modelName)
                if (!modelFile.exists()) {
                    Log.w(TAG, "$modelName not found at ${modelFile.absolutePath}")
                    _statusFlow.value = "Model not found — Demo mode"
                    continue
                }
                _statusFlow.value = "Initializing engine…"
                try {
                    val config = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU()
                    )
                    val e = Engine(config)
                    e.initialize()
                    engine = e
                    modelLoaded = true
                    _statusFlow.value = "Model loaded ✓"
                    Log.i(TAG, "Loaded $modelName")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load $modelName", e)
                    _statusFlow.value = "Load error: ${e.message?.take(60)}"
                    engine = null
                }
            }
            if (!modelLoaded) _statusFlow.value = "No model found — Demo mode"
            Log.w(TAG, "No Gemma model loaded — falling back to DemoStubAnalyzer")
        }
    }

    // ── Session management ────────────────────────────────────────────────────

    fun startSession(inspectionType: InspectionType, visionFindings: VisionFindings) {
        closeSession()
        sessionInspectionType = inspectionType
        sessionVisionFindings = visionFindings
        sessionTurnCount = 0
        Log.d(TAG, "Session started for $inspectionType, vision detections: ${visionFindings.detections.size}")
    }

    fun closeSession() {
        activeConversation?.close()
        activeConversation = null
        sessionTurnCount = 0
    }

    fun isModelLoaded(): Boolean = modelLoaded

    fun getModelFilePath(): String {
        val modelsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("models")
        return modelsDir.resolve(MODEL_E2B).absolutePath
    }

    private fun getOrCreateConversation(): Conversation? {
        val e = engine ?: return null
        if (activeConversation == null) {
            activeConversation = e.createConversation()
        }
        return activeConversation
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    private suspend fun sendToConversation(message: String): String =
        withContext(Dispatchers.IO) {
            val conv = getOrCreateConversation()
                ?: throw IllegalStateException("No model loaded")
            conv.sendMessage(message).contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }

    override suspend fun analyzeEvidence(request: EvidenceAnalysisRequest): EvidenceAnalysisResult {
        withContext(Dispatchers.IO) { ensureModelLoaded() }
        engine ?: return fallback.analyzeEvidence(request)

        return try {
            val prompt = buildTurnPrompt(request)
            val response = sendToConversation(prompt)
            sessionTurnCount++
            parseAnalysisResult(response, request.checklistStep)
                ?: retryAnalysis(response, request)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeEvidence failed", e)
            fallback.analyzeEvidence(request)
        }
    }

    private suspend fun retryAnalysis(
        badResponse: String,
        request: EvidenceAnalysisRequest
    ): EvidenceAnalysisResult {
        return try {
            val repairPrompt = buildRepairPrompt(badResponse)
            val retryResponse = sendToConversation(repairPrompt)
            parseAnalysisResult(retryResponse, request.checklistStep)
                ?: fallback.analyzeEvidence(request)
        } catch (e: Exception) {
            Log.w(TAG, "Retry failed, using fallback", e)
            fallback.analyzeEvidence(request)
        }
    }

    override suspend fun analyzeFullChecklist(request: BatchAnalysisRequest): BatchAnalysisResult {
        _statusFlow.value = "Preparing…"
        withContext(Dispatchers.IO) { ensureModelLoaded() }
        if (engine == null) {
            _statusFlow.value = "Fallback: Demo mode"
            return fallback.analyzeFullChecklist(request)
        }

        return try {
            val prompt = buildBatchPrompt(request)
            _statusFlow.value = "Running inference (${request.answers.size} answers)…"
            val response = withContext(Dispatchers.IO) {
                engine!!.createConversation().use { conv ->
                    conv.sendMessage(prompt).contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                }
            }
            _statusFlow.value = "Parsing response…"
            parseBatchResult(response) ?: run {
                _statusFlow.value = "Parse failed — Demo fallback"
                fallback.analyzeFullChecklist(request)
            }
        } catch (e: Exception) {
            Log.e(TAG, "analyzeFullChecklist failed", e)
            _statusFlow.value = "Error: ${e.message?.take(60)} — Demo fallback"
            fallback.analyzeFullChecklist(request)
        }
    }

    override suspend fun generateReport(request: ReportGenerationRequest): SafetyReport {
        withContext(Dispatchers.IO) { ensureModelLoaded() }
        engine ?: return fallback.generateReport(request)

        return try {
            // Report generation uses a fresh one-shot conversation to avoid contaminating session
            val response = withContext(Dispatchers.IO) {
                engine!!.createConversation().use { conv ->
                    conv.sendMessage(buildReportPrompt(request)).contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                }
            }
            parseReport(response, request) ?: fallback.generateReport(request)
        } catch (e: Exception) {
            Log.e(TAG, "generateReport failed", e)
            fallback.generateReport(request)
        }
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private fun buildTurnPrompt(request: EvidenceAnalysisRequest): String {
        return if (request.isFirstTurn || sessionTurnCount == 0) {
            buildFirstTurnPrompt(request)
        } else {
            buildContinuationTurnPrompt(request)
        }
    }

    private fun buildFirstTurnPrompt(request: EvidenceAnalysisRequest): String {
        val visionSection = if (sessionVisionFindings.detections.isNotEmpty()) {
            "\n\n${sessionVisionFindings.toPromptText()}"
        } else {
            "\n\nVisual scan: no PPE or hazards detected by camera."
        }

        val discrepancyNote = buildDiscrepancyNote(request.workerTranscript, sessionVisionFindings)

        val standardLine = request.standardRef?.let {
            "\n\nApplicable standard: $it\nIf the worker's testimony or visual evidence does not satisfy this standard, recommend WARN, FAIL, or STOP_WORK."
        } ?: ""

        return """
You are FieldSafe Solar, an AI safety inspector for solar and electrical field work.
You have access to both camera scan results and the worker's voice testimony.
Your role is to assist, not certify. Use conservative safety reasoning.
If critical PPE, isolation, lockout/tagout, or authorization is missing or visually unconfirmed, recommend WARN, FAIL, or STOP_WORK.
Flag any discrepancy between what the camera detected and what the worker claims.
Return valid JSON only — no markdown, no code blocks.

Camera device context: This inspection uses a RealWear HMT head-mounted device. The forward-facing camera captures what the worker LOOKS AT — it cannot see items worn on the worker's body above the neck (hard hat, face shield) or at the feet (boots). It CAN capture: gloves (worker looks at hands), lockout tags, equipment labels, work area conditions, solar panels, and connectors. Checklist items confirmed as verbal confirmations were obtained from speech alone — no camera confirmation is possible for those items. Do not penalise the worker for lack of visual confirmation on items that a head-worn camera physically cannot capture.

Inspection type: ${request.inspectionType.name}
Checklist step: ${request.checklistStep}$visionSection$discrepancyNote$standardLine

Worker says: "${request.workerTranscript}"

Return JSON matching this schema exactly:
{"step_id":"string","visible_items":["string"],"possible_hazards":["string"],"missing_or_unclear_items":["string"],"confidence":"LOW|MEDIUM|HIGH","recommended_decision":"PASS|WARN|FAIL|STOP_WORK","worker_followup_question":"string or null","spoken_summary":"string","safety_reasoning_summary":"string"}
        """.trimIndent()
    }

    private fun buildContinuationTurnPrompt(request: EvidenceAnalysisRequest): String {
        // In a live conversation, just send the worker's next response
        // Gemma already has full context from prior turns
        return """
Worker responds to follow-up: "${request.workerTranscript}"

Checklist step being assessed: ${request.checklistStep}

Based on all evidence so far (camera scan + previous exchanges + this response), provide your updated assessment.
Return valid JSON only:
{"step_id":"string","visible_items":["string"],"possible_hazards":["string"],"missing_or_unclear_items":["string"],"confidence":"LOW|MEDIUM|HIGH","recommended_decision":"PASS|WARN|FAIL|STOP_WORK","worker_followup_question":"string or null","spoken_summary":"string","safety_reasoning_summary":"string"}
        """.trimIndent()
    }

    private fun buildDiscrepancyNote(transcript: String, findings: VisionFindings): String {
        if (findings.detections.isEmpty()) return ""
        val lower = transcript.lowercase()

        val claims = mutableListOf<String>()
        val detected = findings.ppeDetected.map { it.label.lowercase() }

        // Check if worker claims PPE that wasn't detected
        if ((lower.contains("glove") || lower.contains("gloves")) &&
            detected.none { it.contains("glove") }) {
            claims.add("Worker mentions gloves but gloves were NOT detected by camera scan")
        }
        if ((lower.contains("hard hat") || lower.contains("helmet")) &&
            detected.none { it.contains("hard hat") || it.contains("helmet") }) {
            claims.add("Worker mentions hard hat but hard hat was NOT detected by camera scan")
        }
        if ((lower.contains("vest") || lower.contains("hi-vis") || lower.contains("high vis")) &&
            detected.none { it.contains("vest") || it.contains("visibility") }) {
            claims.add("Worker mentions safety vest but vest was NOT detected by camera scan")
        }

        // Hazards detected that worker hasn't mentioned
        findings.hazardsDetected.forEach { hazard ->
            if (!lower.contains(hazard.label.lowercase().split(" ").first())) {
                claims.add("Camera detected: ${hazard.label} (${(hazard.confidence * 100).toInt()}%) — worker has not addressed this")
            }
        }

        return if (claims.isEmpty()) "" else "\n\n⚠ Discrepancy alerts:\n" + claims.joinToString("\n") { "- $it" }
    }

    private fun buildReportPrompt(request: ReportGenerationRequest): String {
        val transcripts = request.voiceTranscripts
            .joinToString("\n") { "- Worker: \"${it.userSpeech}\"" }
            .ifEmpty { "(none recorded)" }
        val checklist = request.completedChecklist
            .joinToString("\n") { item ->
                val ref = item.standardRef?.let { " [$it]" } ?: ""
                "- [${item.status}] ${item.description}$ref"
            }
            .ifEmpty { "(none recorded)" }
        return """
You are generating a structured safety observation report.
Include only facts supported by evidence, worker confirmations, or checklist rules.
Do not claim legal compliance certification.
Return valid JSON only — no markdown, no code blocks.

Inspection type: ${request.inspectionType}
Overall decision: ${request.overallDecision}
Severity: ${request.severity}

Checklist (each item shows [STATUS] description [standard reference]):
$checklist

Worker transcripts:
$transcripts

Return JSON matching this schema:
{"report_id":"string","inspection_type":"string","created_at":"ISO-8601","overall_decision":"PASS|WARN|FAIL|STOP_WORK","severity":"LOW|MEDIUM|HIGH|CRITICAL","summary":"string","observed_conditions":["string"],"worker_confirmations":["string"],"unverified_items":["string"],"recommended_actions":["string"],"evidence":[],"limitations":["string"],"sync_status":"LOCAL_ONLY","applicable_standards":["string"]}
        """.trimIndent()
    }

    private fun buildBatchPrompt(request: BatchAnalysisRequest): String {
        val answersSection = request.answers.mapIndexed { i, a ->
            "Q${i + 1}. ${a.toPromptText()}"
        }.joinToString("\n")
        return """
You are FieldSafe Solar, an AI safety inspector for solar and electrical field work.
Review all checklist answers below and give your final safety verdict.
Use conservative safety reasoning: if any critical safety item is NO or SKIPPED, use CAUTION or STOP_WORK.
Return valid JSON only — no markdown, no explanation.

Inspection: ${request.inspectionType.name.replace("_", " ")}
Total questions: ${request.answers.size}

CHECKLIST ANSWERS:
$answersSection

Return JSON:
{"verdict":"GO|CAUTION|STOP_WORK","summary":"string","observations":["string"],"recommended_actions":["string"],"follow_up_questions":["string"],"safety_reasoning_summary":"string","applicable_standards":["string"]}
        """.trimIndent()
    }

    private fun parseBatchResult(response: String): BatchAnalysisResult? {
        return try {
            val obj = JSONObject(extractJson(response) ?: return null)
            val verdictStr = obj.optString("verdict", "CAUTION").trim().uppercase()
            val verdict = when {
                verdictStr == "GO" -> BatchAnalysisResult.Verdict.GO
                verdictStr.contains("STOP") -> BatchAnalysisResult.Verdict.STOP_WORK
                else -> BatchAnalysisResult.Verdict.CAUTION
            }
            BatchAnalysisResult(
                verdict = verdict,
                summary = obj.optString("summary", "Safety analysis complete."),
                observations = obj.optJSONArray("observations").toStringList(),
                recommendedActions = obj.optJSONArray("recommended_actions").toStringList(),
                followUpQuestions = obj.optJSONArray("follow_up_questions").toStringList(),
                safetyReasoningSummary = obj.optString("safety_reasoning_summary", ""),
                applicableStandards = obj.optJSONArray("applicable_standards").toStringList(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseBatchResult failed: ${e.message}")
            null
        }
    }

    private fun buildRepairPrompt(badResponse: String): String = """
The following text should be valid JSON but may have errors. Return ONLY the corrected JSON, no explanation:

$badResponse
    """.trimIndent()

    // ── JSON parsers ──────────────────────────────────────────────────────────

    private fun parseAnalysisResult(response: String, fallbackStepId: String): EvidenceAnalysisResult? {
        return try {
            val obj = JSONObject(extractJson(response) ?: return null)
            EvidenceAnalysisResult(
                stepId = obj.optString("step_id", fallbackStepId).ifEmpty { fallbackStepId },
                visibleItems = obj.optJSONArray("visible_items").toStringList(),
                possibleHazards = obj.optJSONArray("possible_hazards").toStringList(),
                missingOrUnclearItems = obj.optJSONArray("missing_or_unclear_items").toStringList(),
                confidence = parseConfidence(obj.optString("confidence")),
                recommendedDecision = parseDecision(obj.optString("recommended_decision")),
                workerFollowupQuestion = obj.optString("worker_followup_question")
                    .takeIf { it.isNotBlank() && it != "null" },
                spokenSummary = obj.optString("spoken_summary", "Safety analysis complete."),
                safetyReasoningSummary = obj.optString("safety_reasoning_summary", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseAnalysisResult failed: ${e.message}")
            null
        }
    }

    private fun parseReport(response: String, request: ReportGenerationRequest): SafetyReport? {
        return try {
            val obj = JSONObject(extractJson(response) ?: return null)
            SafetyReport(
                reportId = obj.optString("report_id").ifEmpty { UUID.randomUUID().toString() },
                inspectionType = request.inspectionType,
                createdAt = Instant.now(),
                overallDecision = parseSafetyDecision(obj.optString("overall_decision"))
                    ?: request.overallDecision,
                severity = parseSeverity(obj.optString("severity")) ?: request.severity,
                summary = obj.optString("summary", "Safety inspection completed."),
                observedConditions = obj.optJSONArray("observed_conditions").toStringList(),
                workerConfirmations = obj.optJSONArray("worker_confirmations").toStringList(),
                unverifiedItems = obj.optJSONArray("unverified_items").toStringList(),
                recommendedActions = obj.optJSONArray("recommended_actions").toStringList(),
                evidence = emptyList(),
                limitations = obj.optJSONArray("limitations").toStringList(),
                syncStatus = SafetyReport.SyncStatus.LOCAL_ONLY,
                applicableStandards = obj.optJSONArray("applicable_standards").toStringList()
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseReport failed: ${e.message}")
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractJson(text: String): String? {
        val stripped = text.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun parseConfidence(value: String): EvidenceAnalysisResult.Confidence =
        EvidenceAnalysisResult.Confidence.entries.firstOrNull {
            it.name.equals(value.trim(), ignoreCase = true)
        } ?: EvidenceAnalysisResult.Confidence.LOW

    private fun parseDecision(value: String): EvidenceAnalysisResult.Decision =
        EvidenceAnalysisResult.Decision.entries.firstOrNull {
            it.name.equals(value.trim().replace(" ", "_"), ignoreCase = true)
        } ?: EvidenceAnalysisResult.Decision.WARN

    private fun parseSafetyDecision(value: String): SafetyReport.Decision? =
        SafetyReport.Decision.entries.firstOrNull {
            it.name.equals(value.trim().replace(" ", "_"), ignoreCase = true)
        }

    private fun parseSeverity(value: String): SafetyReport.Severity? =
        SafetyReport.Severity.entries.firstOrNull {
            it.name.equals(value.trim(), ignoreCase = true)
        }
}
