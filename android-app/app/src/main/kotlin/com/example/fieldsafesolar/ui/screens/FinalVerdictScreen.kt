package com.example.fieldsafesolar.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.fieldsafesolar.data.model.BatchAnalysisRequest
import com.example.fieldsafesolar.data.model.BatchAnalysisResult
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.example.fieldsafesolar.data.model.InspectionType
import com.example.fieldsafesolar.data.model.SafetyReport
import com.example.fieldsafesolar.di.ServiceProvider
import com.example.fieldsafesolar.ui.navigation.Route
import com.example.fieldsafesolar.ui.theme.FieldSafeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

@Composable
fun FinalVerdictScreen(
    navController: NavController,
    inspectionTypeStr: String,
    modeStr: String,
) {
    val result = remember { ServiceProvider.getPendingResult() }
    val request = remember { ServiceProvider.getPendingBatch() }

    if (result == null || request == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No analysis result available.", color = FieldSafeColors.OnSurfaceVariant)
            }
        }
        return
    }

    var showReasoningExpanded by remember { mutableStateOf(false) }
    var showOverrideOverlay by remember { mutableStateOf(false) }
    var pendingOverrideDecision by remember { mutableStateOf<SafetyReport.Decision?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val verdictColor = when (result.verdict) {
        BatchAnalysisResult.Verdict.GO -> FieldSafeColors.SafeGreen
        BatchAnalysisResult.Verdict.CAUTION -> FieldSafeColors.Warning
        BatchAnalysisResult.Verdict.STOP_WORK -> FieldSafeColors.StopWorkRed
    }
    val verdictLabel = when (result.verdict) {
        BatchAnalysisResult.Verdict.GO -> "SAFE TO PROCEED"
        BatchAnalysisResult.Verdict.CAUTION -> "CAUTION — REVIEW REQUIRED"
        BatchAnalysisResult.Verdict.STOP_WORK -> "STOP WORK"
    }
    val verdictIcon = when (result.verdict) {
        BatchAnalysisResult.Verdict.GO -> "✓"
        BatchAnalysisResult.Verdict.CAUTION -> "▲"
        BatchAnalysisResult.Verdict.STOP_WORK -> "✕"
    }
    val verdictBgAlpha = when (result.verdict) {
        BatchAnalysisResult.Verdict.STOP_WORK -> 0.28f
        BatchAnalysisResult.Verdict.CAUTION -> 0.18f
        BatchAnalysisResult.Verdict.GO -> 0.15f
    }
    val borderWidth = if (result.verdict == BatchAnalysisResult.Verdict.STOP_WORK) 3.dp else 2.dp

    fun saveAndNavigate(override: SafetyReport.Decision? = null, overrideReason: String? = null) {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val report = buildReport(request, result, override, overrideReason)
            try {
                ServiceProvider.getReportRepository().saveReport(report)
            } catch (_: Exception) {}
            ServiceProvider.clearPendingBatch()
            ServiceProvider.clearPendingResult()
            ServiceProvider.clearJhaConfirmed()
            ServiceProvider.clearPendingEewpRecord()
            navController.navigate(Route.ReportDetail.createRoute(report.reportId)) {
                popUpTo(Route.ModeSelect.route)
            }
        }
    }

    val screenModifier = if (result.verdict == BatchAnalysisResult.Verdict.STOP_WORK)
        Modifier.fillMaxSize().border(4.dp, FieldSafeColors.StopWorkRed)
    else
        Modifier.fillMaxSize()

    Surface(modifier = screenModifier, color = FieldSafeColors.Background) {
        Row(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

            // Left: verdict + reasoning
            Column(
                modifier = Modifier.weight(0.6f).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "FIELDSAFE AI VERDICT",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )

                // Verdict badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(verdictColor.copy(alpha = verdictBgAlpha))
                        .border(borderWidth, verdictColor, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = verdictIcon,
                            color = verdictColor,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = verdictLabel,
                            color = verdictColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gemma 4 · On-device AI",
                            color = verdictColor.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Summary
                Text(
                    text = result.summary,
                    color = FieldSafeColors.OnSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )

                // Key finding — first observation surfaced prominently for STOP_WORK / CAUTION
                if (result.verdict != BatchAnalysisResult.Verdict.GO && result.observations.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(verdictColor.copy(alpha = 0.12f))
                            .border(1.dp, verdictColor, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "KEY FINDING",
                                color = verdictColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.observations.first(),
                                color = FieldSafeColors.OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }

                // Remaining observations
                val remainingObs = if (result.verdict != BatchAnalysisResult.Verdict.GO)
                    result.observations.drop(1) else result.observations
                if (remainingObs.isNotEmpty()) {
                    Text(
                        text = "OBSERVATIONS",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    remainingObs.forEach { obs ->
                        Text(text = obs, color = FieldSafeColors.OnSurface, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }

                // Collapsible AI reasoning
                if (result.safetyReasoningSummary.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FieldSafeColors.SurfaceVariant)
                            .clickable { showReasoningExpanded = !showReasoningExpanded }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (showReasoningExpanded) "▲ HIDE AI REASONING" else "▼ VIEW AI REASONING",
                            color = FieldSafeColors.OnSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    AnimatedVisibility(visible = showReasoningExpanded) {
                        Text(
                            text = result.safetyReasoningSummary,
                            color = FieldSafeColors.OnSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Recommended actions
                if (result.recommendedActions.isNotEmpty()) {
                    Text(
                        text = "RECOMMENDED ACTIONS",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    result.recommendedActions.forEach { action ->
                        Text(text = "• $action", color = FieldSafeColors.OnSurface, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }

            // Right: action buttons
            Column(
                modifier = Modifier.weight(0.4f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VerdictActionButton(
                    label = if (isSaving) "SAVING..." else "ACCEPT &\nSAVE REPORT",
                    color = FieldSafeColors.Primary,
                    textColor = FieldSafeColors.OnPrimary,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    onClick = { saveAndNavigate() },
                )
                VerdictActionButton(
                    label = "OVERRIDE\nVERDICT",
                    color = FieldSafeColors.Warning,
                    textColor = Color(0xFF1A0E00),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = { showOverrideOverlay = true },
                )
                VerdictActionButton(
                    label = "BACK TO HOME",
                    color = FieldSafeColors.Secondary,
                    textColor = FieldSafeColors.OnSecondary,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = {
                        ServiceProvider.clearPendingBatch()
                        ServiceProvider.clearPendingResult()
                        navController.navigate(Route.ModeSelect.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }

    // Override verdict overlay — step 1: pick decision
    if (showOverrideOverlay) {
        OverrideVerdictOverlay(
            aiVerdict = result.verdict,
            onSelect = { workerDecision ->
                showOverrideOverlay = false
                pendingOverrideDecision = workerDecision   // proceed to reason recording
            },
            onDismiss = { showOverrideOverlay = false },
        )
    }

    // Override verdict overlay — step 2: record reason
    pendingOverrideDecision?.let { overrideDecision ->
        RecordReasonOverlay(
            pendingDecision = overrideDecision,
            aiVerdict = result.verdict,
            onConfirm = { reason ->
                pendingOverrideDecision = null
                saveAndNavigate(override = overrideDecision, overrideReason = reason)
            },
            onCancel = { pendingOverrideDecision = null },
        )
    }
}

@Composable
private fun VerdictActionButton(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else FieldSafeColors.Secondary)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) textColor else FieldSafeColors.OnSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun OverrideVerdictOverlay(
    aiVerdict: BatchAnalysisResult.Verdict,
    onSelect: (SafetyReport.Decision) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .clip(RoundedCornerShape(16.dp))
                .background(FieldSafeColors.Surface)
                .border(2.dp, FieldSafeColors.Warning, RoundedCornerShape(16.dp))
                .clickable {} // consume clicks so overlay doesn't close
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "OVERRIDE VERDICT",
                    color = FieldSafeColors.Warning,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "AI verdict: ${aiVerdict.name.replace("_", " ")}\nSelect your decision:",
                    color = FieldSafeColors.OnSurface,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                VerdictActionButton(
                    label = "PROCEED",
                    color = FieldSafeColors.SafeGreen,
                    textColor = Color(0xFF1A2600),
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = { onSelect(SafetyReport.Decision.PASS) },
                )
                VerdictActionButton(
                    label = "CAUTION",
                    color = FieldSafeColors.Warning,
                    textColor = Color(0xFF1A0E00),
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = { onSelect(SafetyReport.Decision.WARN) },
                )
                VerdictActionButton(
                    label = "STOP WORK",
                    color = FieldSafeColors.StopWorkRed,
                    textColor = Color.White,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = { onSelect(SafetyReport.Decision.STOP_WORK) },
                )
                VerdictActionButton(
                    label = "CANCEL",
                    color = FieldSafeColors.Secondary,
                    textColor = FieldSafeColors.OnSecondary,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    onClick = onDismiss,
                )
            }
        }
    }
}

private enum class RecordState { IDLE, RECORDING, TRANSCRIBING }

@Composable
private fun RecordReasonOverlay(
    pendingDecision: SafetyReport.Decision,
    aiVerdict: BatchAnalysisResult.Verdict,
    onConfirm: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val audioRecorder = remember { ServiceProvider.getAudioRecorder() }
    val sttEngine = remember { ServiceProvider.getSpeechToTextEngine() }

    var recordState by remember { mutableStateOf(RecordState.IDLE) }
    var reason by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .clip(RoundedCornerShape(16.dp))
                .background(FieldSafeColors.Surface)
                .border(2.dp, FieldSafeColors.Warning, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "STATE YOUR REASON",
                color = FieldSafeColors.Warning,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = "AI: ${aiVerdict.name.replace("_", " ")}  →  You: ${pendingDecision.name.replace("_", " ")}",
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            when (recordState) {
                RecordState.IDLE -> {
                    reason?.let { r ->
                        Text(
                            text = "\"$r\"",
                            color = FieldSafeColors.OnSurface,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    VerdictActionButton(
                        label = if (reason == null) "RECORD REASON" else "RE-RECORD",
                        color = FieldSafeColors.Primary,
                        textColor = FieldSafeColors.OnPrimary,
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        onClick = {
                            scope.launch {
                                audioRecorder.startRecording()
                                recordState = RecordState.RECORDING
                            }
                        },
                    )
                    VerdictActionButton(
                        label = if (reason != null) "CONFIRM" else "SKIP REASON",
                        color = if (reason != null) FieldSafeColors.SafeGreen else FieldSafeColors.Secondary,
                        textColor = if (reason != null) Color(0xFF1A2600) else FieldSafeColors.OnSecondary,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        onClick = { onConfirm(reason) },
                    )
                    VerdictActionButton(
                        label = "CANCEL",
                        color = FieldSafeColors.Secondary,
                        textColor = FieldSafeColors.OnSecondary,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        onClick = onCancel,
                    )
                }
                RecordState.RECORDING -> {
                    Text(
                        text = "Recording… speak your reason",
                        color = FieldSafeColors.DangerRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    VerdictActionButton(
                        label = "STOP",
                        color = FieldSafeColors.StopWorkRed,
                        textColor = Color.White,
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        onClick = {
                            recordState = RecordState.TRANSCRIBING
                            scope.launch {
                                val uri = audioRecorder.stopRecording()
                                val transcript = uri?.path?.let { path ->
                                    withContext(Dispatchers.IO) {
                                        runCatching { sttEngine.transcribeAudio(path) }.getOrNull()
                                    }
                                }
                                reason = transcript?.trim()?.ifBlank { null }
                                recordState = RecordState.IDLE
                            }
                        },
                    )
                }
                RecordState.TRANSCRIBING -> {
                    Text(
                        text = "Transcribing…",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

private fun buildEvidenceCaption(answer: com.example.fieldsafesolar.data.model.QuestionAnswer): String {
    val findings = answer.photoFindings.joinToString(", ").ifEmpty { null }
    val ocr = answer.ocrText?.take(80)?.ifEmpty { null }
    return listOfNotNull(findings, ocr?.let { "OCR: $it" }).joinToString(" | ")
        .ifEmpty { answer.questionText.take(60) }
}

private fun buildReport(
    request: BatchAnalysisRequest,
    result: BatchAnalysisResult,
    workerOverride: SafetyReport.Decision?,
    overrideReason: String?,
): SafetyReport {
    val decision = workerOverride ?: when (result.verdict) {
        BatchAnalysisResult.Verdict.GO -> SafetyReport.Decision.PASS
        BatchAnalysisResult.Verdict.CAUTION -> SafetyReport.Decision.WARN
        BatchAnalysisResult.Verdict.STOP_WORK -> SafetyReport.Decision.STOP_WORK
    }
    val severity = when (decision) {
        SafetyReport.Decision.PASS -> SafetyReport.Severity.LOW
        SafetyReport.Decision.WARN -> SafetyReport.Severity.MEDIUM
        SafetyReport.Decision.FAIL -> SafetyReport.Severity.HIGH
        SafetyReport.Decision.STOP_WORK -> SafetyReport.Severity.CRITICAL
    }
    val limitations = buildList {
        add("Analysis based on worker responses and Gemma 4 AI reasoning.")
        add("Physical verification by a qualified safety officer is recommended for critical tasks.")
        add("AI analysis does not constitute legal compliance certification.")
        if (workerOverride != null) {
            add("Worker override applied: AI verdict was ${result.verdict.name}. ${overrideReason ?: ""}")
        }
    }
    val jhaConfirmed = ServiceProvider.getJhaConfirmed()
    val eewpLines = ServiceProvider.getPendingEewpRecord()?.toDisplayLines() ?: emptyList()
    return SafetyReport(
        reportId = UUID.randomUUID().toString(),
        inspectionType = request.inspectionType,
        createdAt = Instant.now(),
        overallDecision = decision,
        severity = severity,
        summary = result.summary,
        observedConditions = buildList {
            if (workerOverride != null) {
                val aiText = result.verdict.name.replace("_", " ")
                val decisionText = workerOverride.name.replace("_", " ")
                val reasonSuffix = if (!overrideReason.isNullOrBlank()) " — Reason: $overrideReason" else ""
                add("⚠ WORKER OVERRIDE: AI said $aiText → Decision: $decisionText.$reasonSuffix")
            }
            addAll(result.observations)
        },
        workerConfirmations = request.answers
            .filter { it.answerType == com.example.fieldsafesolar.data.model.QuestionAnswer.AnswerType.CUSTOM }
            .mapNotNull { it.customTranscript?.let { t -> "\"$t\" (${it.questionText})" } },
        unverifiedItems = request.answers
            .filter {
                it.answerType == com.example.fieldsafesolar.data.model.QuestionAnswer.AnswerType.NO ||
                    it.answerType == com.example.fieldsafesolar.data.model.QuestionAnswer.AnswerType.SKIP
            }
            .map { "${it.answerType.name}: ${it.questionText}" },
        recommendedActions = result.recommendedActions,
        evidence = request.answers
            .filter { it.photoPath != null }
            .map { answer ->
                SafetyReport.Evidence(
                    evidenceId = UUID.randomUUID().toString(),
                    stepId = answer.questionId,
                    fileUri = answer.photoPath!!,
                    caption = buildEvidenceCaption(answer),
                    analysisConfidence = EvidenceAnalysisResult.Confidence.MEDIUM,
                )
            },
        limitations = buildList {
            addAll(limitations)
            if (!jhaConfirmed) add("Pre-task Job Hazard Analysis was not fully confirmed before this inspection (OSHA 29 CFR 1926.20(b)(2)).")
        },
        syncStatus = SafetyReport.SyncStatus.LOCAL_ONLY,
        applicableStandards = result.applicableStandards,
        jhaConfirmed = jhaConfirmed,
        eewpPermitLines = eewpLines,
    )
}
