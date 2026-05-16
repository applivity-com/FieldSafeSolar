package com.applivity.fieldsafesolar.ui.screens

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
import com.applivity.fieldsafesolar.data.model.SafetyReport
import com.applivity.fieldsafesolar.di.ServiceProvider
import com.applivity.fieldsafesolar.ui.navigation.Route
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun InspectorReportListScreen(
    navController: NavController,
    reports: List<SafetyReport>,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "INSPECTOR",
                    color = FieldSafeColors.Warning,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "  •  Select worker report to review",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FieldSafeColors.Secondary)
                        .clickable { navController.popBackStack() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "BACK",
                        color = FieldSafeColors.OnSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (reports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No worker reports available",
                            color = FieldSafeColors.OnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Workers must complete an inspection first",
                            color = FieldSafeColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    reports.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { report ->
                                WorkerReportCard(
                                    report = report,
                                    modifier = Modifier.weight(1f),
                                    onClick = { navController.navigate(Route.InspectorReview.createRoute(report.reportId)) },
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerReportCard(
    report: SafetyReport,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val decisionColor = when (report.overallDecision) {
        SafetyReport.Decision.PASS -> FieldSafeColors.SafeGreen
        SafetyReport.Decision.WARN -> FieldSafeColors.Warning
        SafetyReport.Decision.FAIL, SafetyReport.Decision.STOP_WORK -> FieldSafeColors.StopWorkRed
    }
    val decisionLabel = when (report.overallDecision) {
        SafetyReport.Decision.PASS -> "PASS"
        SafetyReport.Decision.WARN -> "CAUTION"
        SafetyReport.Decision.FAIL -> "FAIL"
        SafetyReport.Decision.STOP_WORK -> "STOP WORK"
    }
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FieldSafeColors.Surface)
            .border(2.dp, decisionColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.inspectionType.name.replace("_", " "),
                    color = FieldSafeColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatReportDate(report.createdAt),
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = decisionLabel,
                color = decisionColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
fun InspectorReviewScreen(
    navController: NavController,
    report: SafetyReport?,
) {
    if (report == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading report...", color = FieldSafeColors.OnSurfaceVariant)
            }
        }
        return
    }

    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val workerVerdictColor = when (report.overallDecision) {
        SafetyReport.Decision.PASS -> FieldSafeColors.SafeGreen
        SafetyReport.Decision.WARN -> FieldSafeColors.Warning
        SafetyReport.Decision.FAIL, SafetyReport.Decision.STOP_WORK -> FieldSafeColors.StopWorkRed
    }
    val workerVerdictLabel = when (report.overallDecision) {
        SafetyReport.Decision.PASS -> "✅ PASS"
        SafetyReport.Decision.WARN -> "⚠ CAUTION"
        SafetyReport.Decision.FAIL -> "❌ FAIL"
        SafetyReport.Decision.STOP_WORK -> "⛔ STOP WORK"
    }

    fun submitReview(decision: SafetyReport.Decision) {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val inspectorReport = SafetyReport(
                reportId = UUID.randomUUID().toString(),
                inspectionType = report.inspectionType,
                createdAt = Instant.now(),
                overallDecision = decision,
                severity = when (decision) {
                    SafetyReport.Decision.PASS -> SafetyReport.Severity.LOW
                    SafetyReport.Decision.WARN -> SafetyReport.Severity.MEDIUM
                    SafetyReport.Decision.FAIL -> SafetyReport.Severity.HIGH
                    SafetyReport.Decision.STOP_WORK -> SafetyReport.Severity.CRITICAL
                },
                summary = "Inspector review of worker submission (${formatReportDate(report.createdAt)}). Worker AI verdict: ${report.overallDecision.name}.",
                observedConditions = report.observedConditions,
                workerConfirmations = emptyList(),
                unverifiedItems = report.unverifiedItems,
                recommendedActions = report.recommendedActions,
                evidence = emptyList(),
                limitations = listOf(
                    "Inspector review of worker report ${report.reportId}.",
                    "Worker's original AI verdict: ${report.overallDecision.name}.",
                    "Inspector independently assessed conditions and issued this verdict.",
                ),
                syncStatus = SafetyReport.SyncStatus.REVIEWED,
                applicableStandards = report.applicableStandards,
            )
            try {
                ServiceProvider.getReportRepository().saveReport(inspectorReport)
            } catch (_: Exception) {}
            navController.navigate(Route.ReportDetail.createRoute(inspectorReport.reportId)) {
                popUpTo(Route.ModeSelect.route)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Left: worker report details
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "WORKER REPORT — ${report.inspectionType.name.replace("_", " ")}",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = formatReportDate(report.createdAt),
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(workerVerdictColor.copy(alpha = 0.12f))
                        .border(2.dp, workerVerdictColor, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "WORKER AI VERDICT: $workerVerdictLabel",
                        color = workerVerdictColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                }

                Text(
                    text = report.summary,
                    color = FieldSafeColors.OnSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )

                if (report.unverifiedItems.isNotEmpty()) {
                    Text(
                        text = "FLAGGED ITEMS",
                        color = FieldSafeColors.Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    report.unverifiedItems.forEach { item ->
                        Text(
                            text = "• $item",
                            color = FieldSafeColors.OnSurface,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (report.workerConfirmations.isNotEmpty()) {
                    Text(
                        text = "WORKER CONFIRMATIONS",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    report.workerConfirmations.forEach { conf ->
                        Text(
                            text = "\"$conf\"",
                            color = FieldSafeColors.OnSurface,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (report.recommendedActions.isNotEmpty()) {
                    Text(
                        text = "AI RECOMMENDATIONS",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    report.recommendedActions.forEach { action ->
                        Text(
                            text = "→ $action",
                            color = FieldSafeColors.OnSurface,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (report.applicableStandards.isNotEmpty()) {
                    Text(
                        text = "STANDARDS: ${report.applicableStandards.joinToString(" • ")}",
                        color = FieldSafeColors.OnSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            // Right: inspector verdict panel
            Column(
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "INSPECTOR\nASSESSMENT",
                    color = FieldSafeColors.Warning,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Review the worker report\nand issue your verdict:",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))

                InspectorVerdictButton(
                    label = if (isSaving) "SAVING..." else "PASS —\nSAFE TO PROCEED",
                    color = FieldSafeColors.SafeGreen,
                    textColor = Color(0xFF1A2600),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    onClick = { submitReview(SafetyReport.Decision.PASS) },
                )
                InspectorVerdictButton(
                    label = "CAUTION —\nREVIEW REQUIRED",
                    color = FieldSafeColors.Warning,
                    textColor = Color(0xFF1A0E00),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    onClick = { submitReview(SafetyReport.Decision.WARN) },
                )
                InspectorVerdictButton(
                    label = "STOP WORK",
                    color = FieldSafeColors.StopWorkRed,
                    textColor = Color.White,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    onClick = { submitReview(SafetyReport.Decision.STOP_WORK) },
                )
                InspectorVerdictButton(
                    label = "BACK",
                    color = FieldSafeColors.Secondary,
                    textColor = FieldSafeColors.OnSecondary,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    onClick = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun InspectorVerdictButton(
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
    }
}

private fun formatReportDate(instant: Instant): String =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
