package com.example.fieldsafesolar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldsafesolar.data.model.ChecklistItem
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.example.fieldsafesolar.data.model.InspectionType
import com.example.fieldsafesolar.data.model.SafetyReport
import com.example.fieldsafesolar.data.model.VisionDetection
import com.example.fieldsafesolar.di.ServiceProvider
import com.example.fieldsafesolar.presentation.ConversationTurn
import com.example.fieldsafesolar.presentation.VoiceInteractionViewModel
import com.example.fieldsafesolar.ui.components.RealWearButton
import com.example.fieldsafesolar.ui.components.SafetyDecisionBadge
import com.example.fieldsafesolar.ui.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInteractionScreen(
    navController: NavController,
    inspectionTypeArg: String?
) {
    val inspectionType = try {
        inspectionTypeArg?.let { InspectionType.valueOf(it) } ?: InspectionType.PPE_CHECK
    } catch (e: Exception) { InspectionType.PPE_CHECK }

    val viewModel: VoiceInteractionViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var stopWorkReason by remember { mutableStateOf<String?>(null) }
    var historyExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(inspectionType) { viewModel.initInspection(inspectionType) }

    DisposableEffect(Unit) {
        onDispose { ServiceProvider.getSpeechOutputEngine().stopSpeaking() }
    }

    LaunchedEffect(uiState.conversationTurns.size) {
        val lastTurn = uiState.conversationTurns.lastOrNull()
        if (lastTurn?.decision == EvidenceAnalysisResult.Decision.STOP_WORK) {
            stopWorkReason = lastTurn.aiResponse
        }
    }

    LaunchedEffect(uiState.savedReportId) {
        uiState.savedReportId?.let { id ->
            viewModel.clearSavedReportId()
            navController.navigate(Route.ReportDetail.createRoute(id))
        }
    }

    LaunchedEffect(uiState.conversationTurns.size) {
        if (uiState.conversationTurns.isNotEmpty()) listState.animateScrollToItem(Int.MAX_VALUE)
    }

    val totalItems = uiState.checklistItems.size
    val currentIndex = uiState.currentItemIndex
    val currentItem = uiState.checklistItems.getOrNull(currentIndex)
    val isChecklistComplete = totalItems > 0 && currentIndex >= totalItems
    val isBusy = uiState.isRecording || uiState.isTranscribing || uiState.isAnalyzing

    // Split turns: only current checklist step vs all previous steps
    val currentStepTurns = uiState.conversationTurns.filter {
        it.checklistStep == currentItem?.description
    }
    val historyTurns = uiState.conversationTurns.filter {
        it.checklistStep != currentItem?.description
    }

    val overallDecision: SafetyReport.Decision? = if (uiState.conversationTurns.isNotEmpty()) {
        uiState.checklistItems.let { items ->
            when {
                items.any { it.status == ChecklistItem.Status.COMPLETED_STOP_WORK } -> SafetyReport.Decision.STOP_WORK
                items.any { it.status == ChecklistItem.Status.COMPLETED_FAIL } -> SafetyReport.Decision.FAIL
                items.any { it.status == ChecklistItem.Status.COMPLETED_WARN } -> SafetyReport.Decision.WARN
                items.all { it.status != ChecklistItem.Status.PENDING } -> SafetyReport.Decision.PASS
                else -> null
            }
        }
    } else null

    val progressLabel = when {
        totalItems == 0 -> ""
        isChecklistComplete -> "Complete"
        else -> "Question ${(currentIndex + 1).coerceAtMost(totalItems)} of $totalItems"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = inspectionType.name.replace("_", " "),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (progressLabel.isNotEmpty()) {
                                Text(
                                    text = progressLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                                )
                            }
                            Text(
                                text = if (uiState.isGemmaLoaded) "Gemma 4 on-device" else "Demo mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isGemmaLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (overallDecision != null) {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            SafetyDecisionBadge(overallDecision)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {

                    if (uiState.visionFindings.detections.isNotEmpty()) {
                        VisionFindingsBanner(uiState.visionFindings.detections)
                    }

                    // Fixed progress dots — always visible above the scrollable content
                    if (totalItems > 0) {
                        ProgressDotsRow(
                            items = uiState.checklistItems,
                            currentIndex = currentIndex
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // Collapsible history of completed steps
                        if (historyTurns.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = { historyExpanded = !historyExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (historyExpanded)
                                            "▲ Hide ${historyTurns.size} previous step${if (historyTurns.size != 1) "s" else ""}"
                                        else
                                            "▼ View ${historyTurns.size} previous step${if (historyTurns.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (historyExpanded) {
                                items(historyTurns) { turn -> ConversationTurnCard(turn) }
                            }
                        }

                        // Current step's conversation (AI feedback + follow-up)
                        items(currentStepTurns) { turn -> ConversationTurnCard(turn) }

                        if (uiState.isTranscribing || uiState.isAnalyzing) {
                            item { ThinkingIndicator(uiState.isTranscribing) }
                        }

                        // Current question — the dominant element when idle
                        if (!isBusy && !isChecklistComplete) {
                            item {
                                CurrentQuestionCard(
                                    step = currentItem?.description ?: "Describe what you observe",
                                    standardRef = currentItem?.standardRef,
                                    followup = currentStepTurns.lastOrNull()?.followupQuestion
                                )
                            }
                        }

                        if (isChecklistComplete && uiState.conversationTurns.isNotEmpty()) {
                            item {
                                Text(
                                    text = "✅ All steps assessed. Generate your report.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Bottom controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isChecklistComplete) {
                            when {
                                uiState.isRecording -> RecordingActiveControls(
                                    elapsedMs = uiState.recordingElapsedMs,
                                    maxMs = uiState.recordingMaxMs,
                                    silenceCountdownMs = uiState.silenceCountdownMs,
                                    silenceTimeoutMs = uiState.silenceTimeoutMs,
                                    onStop = { viewModel.stopAndAnalyze() }
                                )
                                uiState.isTranscribing -> TranscribingControls(
                                    progress = uiState.transcribingProgress,
                                    animatedText = uiState.animatedTranscript,
                                    durationMs = uiState.transcribingDurationMs,
                                    onCancel = { viewModel.cancelTranscriptionAndReRecord() }
                                )
                                uiState.isAnalyzing -> AnalyzingIndicator()
                                else -> IdleControls(
                                    onMic = { viewModel.startRecording() },
                                    onRepeat = { viewModel.repeatCurrentQuestion() }
                                )
                            }
                        }

                        if (uiState.voiceTranscripts.isNotEmpty()) {
                            RealWearButton(
                                label = if (uiState.isSavingReport) "Generating Report..." else "Generate Safety Report",
                                onClick = { viewModel.saveReport() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                isPrimary = isChecklistComplete,
                                enabled = !uiState.isSavingReport && !isBusy
                            )
                        }
                    }
                }

                // STOP_WORK full-screen overlay
                stopWorkReason?.let { reason ->
                    StopWorkOverlay(
                        reason = reason,
                        onAcknowledge = { stopWorkReason = null }
                    )
                }
            }
        }
    }
}

// ── Progress dots row ────────────────────────────────────────────────────────

@Composable
private fun ProgressDotsRow(items: List<ChecklistItem>, currentIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isActive = index == currentIndex
            val dotColor = when (item.status) {
                ChecklistItem.Status.PENDING ->
                    if (isActive) MaterialTheme.colorScheme.primary else Color(0xFFBDBDBD)
                ChecklistItem.Status.COMPLETED_PASS -> Color(0xFF2E7D32)
                ChecklistItem.Status.COMPLETED_WARN -> Color(0xFFE65100)
                ChecklistItem.Status.COMPLETED_FAIL -> Color(0xFFC62828)
                ChecklistItem.Status.COMPLETED_STOP_WORK -> Color(0xFFB71C1C)
            }
            val dotSize = if (isActive) 28.dp else 18.dp

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
                contentAlignment = Alignment.Center
            ) {
                val label = when (item.status) {
                    ChecklistItem.Status.PENDING -> if (isActive) "${index + 1}" else ""
                    ChecklistItem.Status.COMPLETED_PASS -> "✓"
                    ChecklistItem.Status.COMPLETED_WARN -> "!"
                    ChecklistItem.Status.COMPLETED_FAIL -> "✗"
                    ChecklistItem.Status.COMPLETED_STOP_WORK -> "⛔"
                }
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = if (isActive) 11.sp else 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Bottom control composables ───────────────────────────────────────────────

@Composable
private fun IdleControls(onMic: () -> Unit, onRepeat: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RealWearButton(
            label = "🎤  ANSWER",
            onClick = onMic,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            isPrimary = true
        )
        RealWearButton(
            label = "REPEAT QUESTION",
            onClick = onRepeat,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            isPrimary = false
        )
    }
}

@Composable
private fun RecordingActiveControls(
    elapsedMs: Long,
    maxMs: Long,
    silenceCountdownMs: Long,
    silenceTimeoutMs: Long,
    onStop: () -> Unit
) {
    val fraction = (elapsedMs.toFloat() / maxMs).coerceIn(0f, 1f)
    val remaining = 1f - fraction
    val barColor = when {
        remaining > 0.5f -> Color(0xFF2E7D32)
        remaining > 0.25f -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }
    val remainingSeconds = ((maxMs - elapsedMs) / 1000L).coerceAtLeast(0L)
    val silenceActive = silenceCountdownMs < silenceTimeoutMs && silenceTimeoutMs > 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🔴  Recording…",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFEF5350),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${remainingSeconds}s left",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(remaining)
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(4.dp))
            )
        }

        if (silenceActive) {
            val filledDots = ((silenceCountdownMs.toFloat() / silenceTimeoutMs) * 5)
                .toInt().coerceIn(0, 5)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Silence: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (i < filledDots) Color(0xFF2196F3) else Color(0xFF9E9E9E),
                                CircleShape
                            )
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "auto in ${silenceCountdownMs / 1000L}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        RealWearButton(
            label = "■  STOP AND SUBMIT",
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            isPrimary = true
        )
    }
}

@Composable
private fun TranscribingControls(
    progress: Float,
    animatedText: String,
    durationMs: Long,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val estimatedS = (durationMs / 1000L).coerceAtLeast(1L)
        Text(
            text = "Transcribing ~${estimatedS}s of audio…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (animatedText.isNotEmpty()) {
            Text(
                text = "\"$animatedText\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        RealWearButton(
            label = "↩  STILL TALKING — RE-RECORD",
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            isPrimary = false
        )
    }
}

@Composable
private fun AnalyzingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "FieldSafe AI is thinking…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Current question card ────────────────────────────────────────────────────

@Composable
private fun CurrentQuestionCard(step: String, standardRef: String?, followup: String?) {
    val question = followup ?: step
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (standardRef != null && followup == null) {
                Text(
                    text = standardRef,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f)
                )
            }
        }
    }
}

// ── STOP_WORK overlay ────────────────────────────────────────────────────────

@Composable
private fun StopWorkOverlay(reason: String, onAcknowledge: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⛔", fontSize = 72.sp)
            Text(
                text = "STOP WORK",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = reason,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            RealWearButton(
                label = "I ACKNOWLEDGE — CONTINUE",
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                isPrimary = false
            )
        }
    }
}

// ── Vision findings banner ───────────────────────────────────────────────────

@Composable
private fun VisionFindingsBanner(detections: List<VisionDetection>) {
    val hasHazards = detections.any {
        it.safetyRelevance == VisionDetection.SafetyRelevance.HAZARD_DETECTED
    }
    val bgColor = if (hasHazards) Color(0xFFB71C1C) else Color(0xFF1B5E20)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = if (hasHazards) "⚠ " else "📷 ", fontSize = 16.sp)
        Text(
            text = "Camera scan: ${detections.size} item${if (detections.size != 1) "s" else ""} detected  •  " +
                detections.take(3).joinToString(", ") { it.label },
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Conversation turn card ───────────────────────────────────────────────────

@Composable
private fun ConversationTurnCard(turn: ConversationTurn) {
    var reasoningExpanded by remember { mutableStateOf(false) }
    val decisionColor = when (turn.decision) {
        EvidenceAnalysisResult.Decision.PASS -> Color(0xFF2E7D32)
        EvidenceAnalysisResult.Decision.WARN -> Color(0xFFE65100)
        EvidenceAnalysisResult.Decision.FAIL -> Color(0xFFC62828)
        EvidenceAnalysisResult.Decision.STOP_WORK -> Color(0xFFB71C1C)
    }
    val decisionIcon = when (turn.decision) {
        EvidenceAnalysisResult.Decision.PASS -> "✓"
        EvidenceAnalysisResult.Decision.WARN -> "⚠"
        EvidenceAnalysisResult.Decision.FAIL -> "✗"
        EvidenceAnalysisResult.Decision.STOP_WORK -> "⛔"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "You",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        turn.workerSpeech,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(decisionColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    decisionIcon,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = decisionColor.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "FieldSafe AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = decisionColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        turn.aiResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (turn.safetyReasoningSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { reasoningExpanded = !reasoningExpanded },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = if (reasoningExpanded) "▲ Hide reasoning" else "▼ View AI reasoning",
                                style = MaterialTheme.typography.labelSmall,
                                color = decisionColor.copy(alpha = 0.7f)
                            )
                        }
                        if (reasoningExpanded) {
                            Text(
                                text = turn.safetyReasoningSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Thinking indicator ───────────────────────────────────────────────────────

@Composable
private fun ThinkingIndicator(isTranscribing: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isTranscribing) "Transcribing your speech..." else "AI is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
