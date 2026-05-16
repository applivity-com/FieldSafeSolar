package com.applivity.fieldsafesolar.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.presentation.AppMode
import com.applivity.fieldsafesolar.presentation.InputState
import com.applivity.fieldsafesolar.presentation.QuestionSliderViewModel
import com.applivity.fieldsafesolar.di.ServiceProvider
import com.applivity.fieldsafesolar.ui.components.EewpPermitModal
import com.applivity.fieldsafesolar.ui.components.ThinkingDotsAnimation
import com.applivity.fieldsafesolar.ui.components.VoiceWaveAnimation
import com.applivity.fieldsafesolar.ui.navigation.Route
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors
import java.io.File

@Composable
fun QuestionSliderScreen(
    navController: NavController,
    inspectionTypeStr: String?,
    modeStr: String?,
    viewModel: QuestionSliderViewModel = viewModel(),
) {
    val inspectionType = remember(inspectionTypeStr) {
        inspectionTypeStr?.let { runCatching { InspectionType.valueOf(it) }.getOrNull() }
    } ?: return

    val mode = if (modeStr == "inspector") AppMode.INSPECTOR else AppMode.WORKER

    LaunchedEffect(inspectionType, mode) {
        viewModel.init(inspectionType, mode)
    }

    val state by viewModel.uiState.collectAsState()
    val pendingCount by viewModel.pendingTranscriptionCount.collectAsState()
    var showEewpModal by remember { mutableStateOf(false) }

    // Navigate to batch analysis when all questions answered
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            navController.navigate(
                Route.GemmaLoading.createRoute(
                    inspectionType.name,
                    modeStr ?: "worker"
                )
            )
        }
    }

    // Camera launcher for photo evidence
    val context = LocalContext.current
    val photoFile = remember { File(context.cacheDir, "evidence_${System.currentTimeMillis()}.jpg") }
    val photoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onPhotoCaptured(photoUri)
        else viewModel.skipPhoto()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top progress bar
            ProgressHeader(
                mode = mode,
                inspectionType = inspectionType,
                questionNumber = state.questionNumber,
                totalQuestions = state.totalQuestions,
                progress = state.progress,
                onBack = {
                    if (state.currentIndex == 0) navController.popBackStack()
                    else viewModel.goBack()
                }
            )

            // Main content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Left: question + context
                QuestionPanel(
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                    state = state,
                    onReplay = { viewModel.replayQuestion() },
                )

                // Right: answer buttons or recording controls
                Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = state.inputState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "input_state"
                    ) { inputState ->
                        when (inputState) {
                            InputState.IDLE -> {
                                if (state.showPhotoPrompt) {
                                    PhotoPromptButtons(
                                        onTakePhoto = { cameraLauncher.launch(photoUri) },
                                        onSkipPhoto = { viewModel.skipPhoto() },
                                    )
                                } else {
                                    AnswerButtons(
                                        onYes = {
                                            if (state.currentQuestion?.id == "inv_energized_permit") {
                                                showEewpModal = true
                                            } else {
                                                viewModel.answerYes()
                                            }
                                        },
                                        onNo = { viewModel.answerNo() },
                                        onSkip = { viewModel.skipQuestion() },
                                        onCustom = { viewModel.startCustomRecording() },
                                        isOpenEnded = state.currentQuestion?.type == com.applivity.fieldsafesolar.data.model.ChecklistItem.ChecklistItemType.OPEN_ENDED,
                                    )
                                }
                            }
                            InputState.RECORDING -> RecordingControls(
                                amplitude = state.recordingAmplitude,
                                onStop = { viewModel.stopCustomRecordingAsync() },
                            )
                            InputState.TRANSCRIBING -> TranscribingIndicator()
                            InputState.PROCESSING_PHOTO -> ProcessingPhotoIndicator()
                            InputState.CONFIRM_CUSTOM -> ConfirmCustomControls(
                                transcript = state.customTranscript,
                                onConfirm = { viewModel.confirmCustomAnswer() },
                                onReRecord = { viewModel.reRecord() },
                            )
                            InputState.FINALIZING -> FinalizingPanel(
                                pendingCount = pendingCount,
                                onConfirm = { viewModel.confirmTranscriptReview() },
                            )
                        }
                    }
                }
            }
        }
    }

    // EEWP permit modal — triggered when worker answers YES to energized work permit question
    if (showEewpModal) {
        EewpPermitModal(
            onPermitIssued = { record ->
                ServiceProvider.setPendingEewpRecord(record)
                showEewpModal = false
                viewModel.answerYes()
            },
            onDismiss = {
                showEewpModal = false
                viewModel.answerYes()
            },
        )
    }

    // First-use recording notice overlay (shown exactly once, ever)
    if (state.showRecordingNotice) {
        RecordingNoticeOverlay(
            onAutoConfirm = { viewModel.dismissRecordingNotice(dontShowAgain = true) },
        )
    }

    // Error snackbar
    state.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(FieldSafeColors.DangerRed)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(text = msg, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    mode: AppMode,
    inspectionType: InspectionType,
    questionNumber: Int,
    totalQuestions: Int,
    progress: Float,
    onBack: () -> Unit,
) {
    val modeColor = if (mode == AppMode.INSPECTOR) FieldSafeColors.Warning else FieldSafeColors.Primary
    val inspectionLabel = when (inspectionType) {
        InspectionType.PPE_CHECK -> "PPE CHECK"
        InspectionType.INVERTER_PANEL_CHECK -> "INVERTER / PANEL"
        InspectionType.WORK_AREA_CHECK -> "WORK AREA"
        InspectionType.SOLAR_COMMISSIONING -> "SOLAR COMMISSIONING"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FieldSafeColors.Surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(FieldSafeColors.Background)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("BACK", color = FieldSafeColors.OnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = mode.name,
                color = modeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = "  •  $inspectionLabel",
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Q$questionNumber of $totalQuestions",
                color = FieldSafeColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = modeColor,
            trackColor = FieldSafeColors.Surface,
        )
    }
}

@Composable
private fun QuestionPanel(
    modifier: Modifier = Modifier,
    state: com.applivity.fieldsafesolar.presentation.QuestionSliderUiState,
    onReplay: () -> Unit,
) {
    if (state.inputState == InputState.FINALIZING) {
        TranscriptReviewPanel(modifier = modifier, state = state)
        return
    }
    val q = state.currentQuestion
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.weight(1f))
        if (q != null) {
            Text(
                text = q.description,
                color = FieldSafeColors.OnBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            q.standardRef?.let { ref ->
                Text(
                    text = ref,
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                )
            }
            if (!q.required) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "OPTIONAL",
                    color = FieldSafeColors.Warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        // Show previous answer summary if in photo prompt
        if (state.showPhotoPrompt) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FieldSafeColors.PrimaryContainer)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "Answer recorded",
                        color = FieldSafeColors.OnPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    state.pendingAnswer?.customTranscript?.let { t ->
                        if (t.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"$t\"",
                                color = FieldSafeColors.OnPrimaryContainer,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        // Show custom transcript in confirm state
        if (state.inputState == InputState.CONFIRM_CUSTOM && state.customTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FieldSafeColors.SurfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = "\"${state.customTranscript}\"",
                    color = FieldSafeColors.OnSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }

        // Answers so far + last answer's photo findings
        if (state.answers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${state.answers.size} answered",
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 12.sp,
            )
            val lastFindings = state.answers.last().photoFindings
            if (lastFindings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Photo: ${lastFindings.joinToString(" · ")}",
                    color = FieldSafeColors.Primary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.inputState == InputState.IDLE && !state.showPhotoPrompt) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FieldSafeColors.Secondary)
                    .clickable(onClick = onReplay)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "REPLAY QUESTION",
                    color = FieldSafeColors.OnSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun AnswerButtons(
    onYes: () -> Unit,
    onNo: () -> Unit,
    onSkip: () -> Unit,
    onCustom: () -> Unit,
    isOpenEnded: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!isOpenEnded) {
            AnswerButton(label = "YES", color = FieldSafeColors.SafeGreen, textColor = Color(0xFF1A2600), modifier = Modifier.fillMaxWidth().weight(1f), onClick = onYes)
            AnswerButton(label = "NO", color = FieldSafeColors.DangerRed, textColor = Color.White, modifier = Modifier.fillMaxWidth().weight(1f), onClick = onNo)
        }
        AnswerButton(label = "SKIP", color = FieldSafeColors.Secondary, textColor = FieldSafeColors.OnSecondary, modifier = Modifier.fillMaxWidth().weight(1f), onClick = onSkip)
        AnswerButton(label = "DESCRIBE", color = FieldSafeColors.Warning, textColor = Color(0xFF1A0E00), modifier = Modifier.fillMaxWidth().weight(1f), onClick = onCustom)
    }
}

@Composable
private fun AnswerButton(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhotoPromptButtons(
    onTakePhoto: () -> Unit,
    onSkipPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Photo evidence?",
            color = FieldSafeColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Optional — helps AI verify your answer",
            color = FieldSafeColors.OnSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        AnswerButton(
            label = "CAPTURE",
            color = FieldSafeColors.Primary,
            textColor = FieldSafeColors.OnPrimary,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            onClick = onTakePhoto,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AnswerButton(
            label = "SKIP",
            color = FieldSafeColors.Secondary,
            textColor = FieldSafeColors.OnSecondary,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            onClick = onSkipPhoto,
        )
    }
}

@Composable
private fun RecordingControls(amplitude: Float, onStop: () -> Unit) {
    var secondsLeft by remember { mutableStateOf(15) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000)
            secondsLeft--
        }
        onStop()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VoiceWaveAnimation(
            amplitude = amplitude,
            color = FieldSafeColors.DangerRed,
            modifier = Modifier.fillMaxWidth().height(80.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "0:%02d".format(secondsLeft),
            color = FieldSafeColors.DangerRed,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Listening...",
            color = FieldSafeColors.DangerRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(20.dp))
        AnswerButton(
            label = "STOP",
            color = FieldSafeColors.DangerRed,
            textColor = Color.White,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            onClick = onStop,
        )
    }
}

@Composable
private fun TranscribingIndicator() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThinkingDotsAnimation(color = FieldSafeColors.Warning)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Transcribing...",
            color = FieldSafeColors.Warning,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ConfirmCustomControls(
    transcript: String,
    onConfirm: () -> Unit,
    onReRecord: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Heard correctly?",
            color = FieldSafeColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        AnswerButton(
            label = "CONFIRM",
            color = FieldSafeColors.SafeGreen,
            textColor = Color(0xFF1A2600),
            modifier = Modifier.fillMaxWidth().height(80.dp),
            onClick = onConfirm,
        )
        AnswerButton(
            label = "RE-RECORD",
            color = FieldSafeColors.Secondary,
            textColor = FieldSafeColors.OnSecondary,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            onClick = onReRecord,
        )
    }
}

@Composable
private fun ProcessingPhotoIndicator() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThinkingDotsAnimation(color = FieldSafeColors.Primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Analyzing photo...",
            color = FieldSafeColors.Primary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TranscriptReviewPanel(
    modifier: Modifier = Modifier,
    state: com.applivity.fieldsafesolar.presentation.QuestionSliderUiState,
) {
    val customAnswers = state.answers.filter {
        it.answerType == com.applivity.fieldsafesolar.data.model.QuestionAnswer.AnswerType.CUSTOM
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "REVIEW VOICE RESPONSES",
            color = FieldSafeColors.OnSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        customAnswers.forEachIndexed { _, answer ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FieldSafeColors.SurfaceVariant)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = answer.questionText.take(70) + if (answer.questionText.length > 70) "…" else "",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    if (answer.customTranscript != null) {
                        Text(
                            text = "\"${answer.customTranscript}\"",
                            color = FieldSafeColors.OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThinkingDotsAnimation(color = FieldSafeColors.Warning)
                            Text(
                                text = "Transcribing...",
                                color = FieldSafeColors.Warning,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalizingPanel(
    pendingCount: Int,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pendingCount > 0) {
            ThinkingDotsAnimation(color = FieldSafeColors.Warning)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (pendingCount == 1) "Transcribing 1 response…" else "Transcribing $pendingCount responses…",
                color = FieldSafeColors.Warning,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            Text(
                text = "✓",
                color = FieldSafeColors.SafeGreen,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "All responses ready",
                color = FieldSafeColors.SafeGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        AnswerButton(
            label = "CONTINUE",
            color = if (pendingCount == 0) FieldSafeColors.SafeGreen else FieldSafeColors.Secondary,
            textColor = if (pendingCount == 0) Color(0xFF1A2600) else FieldSafeColors.OnSurfaceVariant,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            onClick = { if (pendingCount == 0) onConfirm() },
        )
    }
}

@Composable
private fun RecordingNoticeOverlay(onAutoConfirm: () -> Unit) {
    var countdown by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
        onAutoConfirm()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(FieldSafeColors.Surface)
                .border(2.dp, FieldSafeColors.Warning, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "VOICE RECORDING",
                    color = FieldSafeColors.Warning,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You have 15 seconds to record your answer.\nAI will transcribe and process it after all questions.",
                    color = FieldSafeColors.OnSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recording begins in $countdown…",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

