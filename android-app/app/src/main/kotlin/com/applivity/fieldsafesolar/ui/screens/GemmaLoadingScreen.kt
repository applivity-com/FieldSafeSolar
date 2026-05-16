package com.applivity.fieldsafesolar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.di.ServiceProvider
import com.applivity.fieldsafesolar.ui.components.ThinkingDotsAnimation
import com.applivity.fieldsafesolar.ui.navigation.Route
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun GemmaLoadingScreen(
    navController: NavController,
    inspectionTypeStr: String,
    modeStr: String,
) {
    val answerCount = ServiceProvider.getPendingBatch()?.answers?.size ?: 0

    val statusFlow = remember { ServiceProvider.getAnalysisStatusFlow() }
    val status by statusFlow.collectAsState()

    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsedSeconds++ }
    }

    val modelFileExists = remember { File(ServiceProvider.getModelFilePath()).exists() }

    val isOnDevice = remember(status) {
        val s = status.lowercase()
        "loaded" in s || "inference" in s || "parsing" in s || "preparing" in s
    }

    LaunchedEffect(Unit) {
        val request = ServiceProvider.getPendingBatch()
        if (request == null) {
            navController.navigate(Route.ModeSelect.route) {
                popUpTo(0) { inclusive = true }
            }
            return@LaunchedEffect
        }
        val result = try {
            ServiceProvider.getAiSafetyAnalyzer().analyzeFullChecklist(request)
        } catch (e: Exception) {
            com.applivity.fieldsafesolar.data.repository.DemoStubAnalyzer()
                .analyzeFullChecklist(request)
        }
        ServiceProvider.setPendingResult(result)
        navController.navigate(Route.FinalVerdict.createRoute(inspectionTypeStr, modeStr)) {
            popUpTo(Route.GemmaLoading.route) { inclusive = true }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ThinkingDotsAnimation(color = FieldSafeColors.Primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Analyzing Inspection",
                color = FieldSafeColors.Primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reviewing $answerCount responses...",
                color = FieldSafeColors.OnSurface,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AI-powered safety analysis",
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
            )

            // ── Debug panel ────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(1.dp)
                    .background(FieldSafeColors.SurfaceVariant)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "DEBUG",
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            val modelIcon = if (modelFileExists) "✓" else "✗"
            val modelLabel = if (modelFileExists) "E2B file found" else "E2B not found on device"
            DebugRow("$modelIcon  Model", modelLabel)

            val modeLabel = if (isOnDevice) "Gemma E2B (on-device)" else "Demo Stub (fallback)"
            DebugRow("▶  Mode", modeLabel)

            DebugRow("◉  Phase", status)

            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            DebugRow("⏱  Time", "$mins:${"%02d".format(secs)}")
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Text(
        text = "$label:   $value",
        color = FieldSafeColors.OnSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    Spacer(modifier = Modifier.height(4.dp))
}
