package com.applivity.fieldsafesolar.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.presentation.JhaAnswerState
import com.applivity.fieldsafesolar.presentation.PreTaskInputState
import com.applivity.fieldsafesolar.presentation.PreTaskPlanViewModel
import com.applivity.fieldsafesolar.ui.navigation.Route
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors

@Composable
fun PreTaskPlanScreen(
    navController: NavController,
    mode: String?,
    viewModel: PreTaskPlanViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.init() }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            navController.navigate(Route.InspectionTypeSelect.createRoute(mode ?: "worker")) {
                popUpTo(Route.ModeSelect.route)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {

            // Left: progress checklist
            Column(
                modifier = Modifier.width(200.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "PRE-TASK\nSAFETY CHECK",
                    color = FieldSafeColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "OSHA 29 CFR 1926.20(b)(2)\nANSI/SEIA 301-2025",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                viewModel.questions.forEachIndexed { idx, q ->
                    JhaProgressRow(
                        index = idx,
                        isCurrent = idx == state.currentIndex && state.inputState != PreTaskInputState.ANSWERED,
                        answer = state.answers.getOrElse(idx) { JhaAnswerState.PENDING },
                        questionText = q.text,
                    )
                }
            }

            // Right: active question + controls
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val currentQ = viewModel.questions.getOrNull(state.currentIndex)
                val progress = (state.currentIndex.toFloat() + 1f) / viewModel.questions.size

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = FieldSafeColors.Primary,
                    trackColor = FieldSafeColors.SurfaceVariant,
                )
                Text(
                    text = "Question ${state.currentIndex + 1} of ${viewModel.questions.size}",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                )

                AnimatedContent(
                    targetState = state.inputState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "jha_state",
                ) { inputState ->
                    when (inputState) {
                        PreTaskInputState.ANSWERED -> {
                            // All questions answered — show summary + proceed
                            val allConfirmed = state.answers.all { it == JhaAnswerState.CONFIRMED }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = if (allConfirmed) "JHA COMPLETE ✓" else "JHA RECORDED",
                                    color = if (allConfirmed) FieldSafeColors.SafeGreen else FieldSafeColors.Warning,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = if (allConfirmed)
                                        "All pre-task safety checks confirmed.\nProceed to inspection."
                                    else
                                        "Some items not confirmed.\nProceed with caution — findings recorded.",
                                    color = FieldSafeColors.OnSurface,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                )
                                Button(
                                    onClick = { viewModel.confirmComplete() },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (allConfirmed) FieldSafeColors.Primary else FieldSafeColors.Warning,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = if (allConfirmed) "PROCEED TO INSPECTION" else "PROCEED (CAUTION)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FieldSafeColors.OnPrimary,
                                    )
                                }
                            }
                        }
                        else -> {
                            // Active question
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (currentQ != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(FieldSafeColors.Surface)
                                            .border(1.dp, FieldSafeColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .padding(20.dp),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                text = currentQ.text,
                                                color = FieldSafeColors.OnSurface,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 26.sp,
                                            )
                                            Text(
                                                text = currentQ.standardRef,
                                                color = FieldSafeColors.OnSurfaceVariant,
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }

                                    when (inputState) {
                                        PreTaskInputState.READING -> {
                                            Text(
                                                text = "Reading question...",
                                                color = FieldSafeColors.OnSurfaceVariant,
                                                fontSize = 13.sp,
                                            )
                                        }
                                        PreTaskInputState.RECORDING -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Text(
                                                    text = "● RECORDING — Say YES or NO",
                                                    color = FieldSafeColors.DangerRed,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Button(
                                                    onClick = { viewModel.stopAndTranscribe() },
                                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.DangerRed),
                                                    shape = RoundedCornerShape(12.dp),
                                                ) {
                                                    Text("STOP RECORDING", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                        PreTaskInputState.TRANSCRIBING -> {
                                            Text(
                                                text = "Transcribing...",
                                                color = FieldSafeColors.OnSurfaceVariant,
                                                fontSize = 13.sp,
                                            )
                                        }
                                        else -> {
                                            // IDLE — show voice + manual buttons
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = { viewModel.startRecording() },
                                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.Primary),
                                                    shape = RoundedCornerShape(12.dp),
                                                ) {
                                                    Text("ANSWER BY VOICE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FieldSafeColors.OnPrimary)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    Button(
                                                        onClick = { viewModel.answerManually(true) },
                                                        modifier = Modifier.weight(1f).height(80.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.SafeGreen),
                                                        shape = RoundedCornerShape(12.dp),
                                                    ) {
                                                        Text("YES", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.answerManually(false) },
                                                        modifier = Modifier.weight(1f).height(80.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.DangerRed),
                                                        shape = RoundedCornerShape(12.dp),
                                                    ) {
                                                        Text("NO", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                                                    }
                                                }
                                                Button(
                                                    onClick = { viewModel.replayQuestion() },
                                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.SurfaceVariant),
                                                    shape = RoundedCornerShape(12.dp),
                                                ) {
                                                    Text("REPLAY QUESTION", fontSize = 13.sp, color = FieldSafeColors.OnSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JhaProgressRow(
    index: Int,
    isCurrent: Boolean,
    answer: JhaAnswerState,
    questionText: String,
) {
    val (dotColor, dotLabel) = when {
        answer == JhaAnswerState.CONFIRMED -> Pair(FieldSafeColors.SafeGreen, "✓")
        answer == JhaAnswerState.NOT_CONFIRMED -> Pair(FieldSafeColors.DangerRed, "✗")
        isCurrent -> Pair(FieldSafeColors.Primary, "${index + 1}")
        else -> Pair(FieldSafeColors.SurfaceVariant, "${index + 1}")
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(dotColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = dotLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            text = questionText,
            color = if (isCurrent) FieldSafeColors.OnSurface else FieldSafeColors.OnSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
