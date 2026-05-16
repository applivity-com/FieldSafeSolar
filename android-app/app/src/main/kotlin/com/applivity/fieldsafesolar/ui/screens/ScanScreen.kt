package com.applivity.fieldsafesolar.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.data.model.VisionDetection
import com.applivity.fieldsafesolar.data.repository.MlKitVisionAnalyzer
import com.applivity.fieldsafesolar.di.ServiceProvider
import com.applivity.fieldsafesolar.presentation.VoiceInteractionViewModel
import com.applivity.fieldsafesolar.ui.components.RealWearButton
import com.applivity.fieldsafesolar.ui.navigation.Route
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors

private const val ANALYSIS_INTERVAL_MS = 600L

private enum class ScanPhase { PREPARING, SCANNING, COMPLETE }

private fun scanVoicePrompt(type: InspectionType) = when (type) {
    InspectionType.PPE_CHECK -> "Point camera at your hands and tools"
    InspectionType.INVERTER_PANEL_CHECK -> "Point at lockout tags and breakers"
    InspectionType.WORK_AREA_CHECK -> "Scan the work area for hazards"
    InspectionType.SOLAR_COMMISSIONING -> "Point at panels and connectors"
}

@Composable
fun ScanScreen(
    navController: NavController,
    inspectionTypeArg: String?
) {
    val inspectionType = try {
        inspectionTypeArg?.let { InspectionType.valueOf(it) } ?: InspectionType.PPE_CHECK
    } catch (e: Exception) { InspectionType.PPE_CHECK }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: VoiceInteractionViewModel = viewModel()

    val visionAnalyzer = remember { MlKitVisionAnalyzer() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val tts = remember { ServiceProvider.getSpeechOutputEngine() }
    val settings = remember { ServiceProvider.getAppSettingsRepository().getSettings() }
    val scanDurationMs = remember { settings.scanDurationSeconds * 1000L }

    var phase by remember { mutableStateOf(ScanPhase.PREPARING) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var autoAdvanceSecs by remember { mutableIntStateOf(4) }
    var autoAdvanceCancelled by remember { mutableStateOf(false) }
    val detections = remember { mutableStateListOf<VisionDetection>() }
    val allDetections = remember { mutableStateListOf<VisionDetection>() }
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lastAnalysisTime by remember { mutableStateOf(0L) }

    // Local functions defined before LaunchedEffects so they can be referenced

    fun finishScan() {
        val capture = imageCapture ?: run {
            val findings = visionAnalyzer.buildFindings(allDetections.toList(), null, scanDurationMs)
            viewModel.setVisionFindings(findings)
            navController.navigate(Route.VoiceInteraction.createRoute(inspectionType.name))
            return
        }
        val file = File(context.cacheDir, "scan_evidence_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    val uri = results.savedUri?.toString() ?: "file://${file.absolutePath}"
                    val findings = visionAnalyzer.buildFindings(allDetections.toList(), uri, scanDurationMs)
                    viewModel.setVisionFindings(findings)
                    navController.navigate(Route.VoiceInteraction.createRoute(inspectionType.name))
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("ScanScreen", "Evidence photo failed", e)
                    val findings = visionAnalyzer.buildFindings(allDetections.toList(), null, scanDurationMs)
                    viewModel.setVisionFindings(findings)
                    navController.navigate(Route.VoiceInteraction.createRoute(inspectionType.name))
                }
            }
        )
    }

    fun rescan() {
        detections.clear()
        allDetections.clear()
        autoAdvanceCancelled = true
        scanProgress = 0f
        phase = ScanPhase.SCANNING
    }

    // PREPARING: speak prompt, then start scanning
    LaunchedEffect(Unit) {
        tts.speak(scanVoicePrompt(inspectionType))
        delay(1500)
        phase = ScanPhase.SCANNING
    }

    // SCANNING: run countdown
    LaunchedEffect(phase) {
        if (phase != ScanPhase.SCANNING) return@LaunchedEffect
        scanProgress = 0f
        val start = System.currentTimeMillis()
        while (phase == ScanPhase.SCANNING) {
            val elapsed = System.currentTimeMillis() - start
            scanProgress = (elapsed.toFloat() / scanDurationMs).coerceIn(0f, 1f)
            if (elapsed >= scanDurationMs) {
                phase = ScanPhase.COMPLETE
                break
            }
            delay(100)
        }
    }

    // COMPLETE: auto-advance countdown (cancelled when user taps Cancel or Rescan)
    LaunchedEffect(phase, autoAdvanceCancelled) {
        if (phase != ScanPhase.COMPLETE || autoAdvanceCancelled) return@LaunchedEffect
        autoAdvanceSecs = 4
        while (autoAdvanceSecs > 0 && !autoAdvanceCancelled) {
            delay(1000)
            if (!autoAdvanceCancelled) autoAdvanceSecs--
        }
        if (!autoAdvanceCancelled && phase == ScanPhase.COMPLETE) finishScan()
    }

    // Cleanup TTS and analysis thread on navigation
    DisposableEffect(Unit) {
        onDispose {
            tts.stopSpeaking()
            analysisExecutor.shutdown()
        }
    }

    // CameraX setup
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (phase != ScanPhase.SCANNING || now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastAnalysisTime = now
                try {
                    val bitmap = imageProxy.toBitmapSafe()
                    if (bitmap != null) {
                        kotlinx.coroutines.runBlocking {
                            val found = visionAnalyzer.analyzeFrame(bitmap)
                            if (found.isNotEmpty()) {
                                allDetections.addAll(found)
                                val existingLabels = detections.map { it.label }.toSet()
                                val newOnes = found.filter { it.label !in existingLabels }
                                detections.addAll(newOnes)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ScanScreen", "Frame analysis error: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, capture, analysis
                )
            } catch (e: Exception) {
                Log.e("ScanScreen", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.96f))
                    )
                )
        )

        // PREPARING: center overlay with guidance
        if (phase == ScanPhase.PREPARING) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📷", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "POSITIONING",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (inspectionType) {
                        InspectionType.PPE_CHECK -> "Point camera at hands and tools"
                        InspectionType.INVERTER_PANEL_CHECK -> "Point at lockout tags and breakers"
                        InspectionType.WORK_AREA_CHECK -> "Pan around the work area"
                        InspectionType.SOLAR_COMMISSIONING -> "Point at panels and connectors"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        // SCANNING / COMPLETE: top status bar
        if (phase != ScanPhase.PREPARING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dot = if (phase == ScanPhase.SCANNING) "🔴" else "✅"
                val label = if (phase == ScanPhase.SCANNING) "SCANNING WORK AREA..." else "SCAN COMPLETE"
                val labelColor = if (phase == ScanPhase.SCANNING) Color(0xFFFF4444) else Color(0xFF4CAF50)
                Text(
                    text = "$dot  $label",
                    color = labelColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (phase == ScanPhase.SCANNING) {
                    val remainingS = ((scanDurationMs * (1f - scanProgress)) / 1000L).toInt().coerceAtLeast(0)
                    Text(
                        text = "${remainingS}s",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // SCANNING: reverse countdown progress bar (full → empty)
        if (phase == ScanPhase.SCANNING) {
            val remaining = 1f - scanProgress
            val barColor = when {
                remaining > 0.5f -> Color(0xFF2E7D32)
                remaining > 0.25f -> Color(0xFFE65100)
                else -> Color(0xFFC62828)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.TopStart)
                    .padding(top = 52.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(remaining)
                        .fillMaxHeight()
                        .background(barColor)
                )
            }
        }

        // Bottom panel — content varies by phase
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            when (phase) {
                ScanPhase.PREPARING -> { /* nothing in bottom during PREPARING */ }

                ScanPhase.SCANNING -> {
                    if (detections.isNotEmpty()) {
                        Text(
                            text = "DETECTING...",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        detections.take(5).forEach { detection ->
                            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                                DetectionRow(detection)
                            }
                        }
                    } else {
                        Text(
                            text = "Point camera at the work area",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    RealWearButton(
                        label = "Skip Scan",
                        onClick = { phase = ScanPhase.COMPLETE },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        isPrimary = false
                    )
                }

                ScanPhase.COMPLETE -> {
                    Text(
                        text = "SCAN RESULTS",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (detections.isEmpty()) {
                        Text(
                            text = "○  No items detected by camera",
                            color = Color(0xFFFFAB00),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = "Voice check will cover all safety items.",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        // Categorised detections
                        val confirmed = detections.filter {
                            it.safetyRelevance == VisionDetection.SafetyRelevance.PPE_PRESENT ||
                                it.safetyRelevance == VisionDetection.SafetyRelevance.EQUIPMENT
                        }
                        val hazards = detections.filter {
                            it.safetyRelevance == VisionDetection.SafetyRelevance.HAZARD_DETECTED
                        }
                        if (confirmed.isNotEmpty()) {
                            Text(
                                text = "Camera confirmed:",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            confirmed.take(4).forEach { DetectionRow(it) }
                        }
                        if (hazards.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Needs attention:",
                                color = Color(0xFFFF5722),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            hazards.take(3).forEach { DetectionRow(it) }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-advance indicator or manual proceed
                    if (!autoAdvanceCancelled && autoAdvanceSecs > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Proceeding to AI analysis in ${autoAdvanceSecs}s…",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            TextButton(onClick = { autoAdvanceCancelled = true }) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RealWearButton(
                            label = "🔄 Rescan",
                            onClick = { rescan() },
                            modifier = Modifier.weight(1f).height(80.dp),
                            isPrimary = false
                        )
                        RealWearButton(
                            label = "Proceed to AI →",
                            onClick = { finishScan() },
                            modifier = Modifier.weight(1.6f).height(80.dp),
                            isPrimary = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionRow(detection: VisionDetection) {
    val (icon, color) = when (detection.safetyRelevance) {
        VisionDetection.SafetyRelevance.PPE_PRESENT -> "✓" to Color(0xFF4CAF50)
        VisionDetection.SafetyRelevance.HAZARD_DETECTED -> "⚠" to Color(0xFFFF5722)
        VisionDetection.SafetyRelevance.EQUIPMENT -> "◉" to Color(0xFF2196F3)
        VisionDetection.SafetyRelevance.PERSON -> "👤" to Color(0xFFFFFFFF)
        VisionDetection.SafetyRelevance.IRRELEVANT -> "·" to Color(0xFF9E9E9E)
    }
    val pct = (detection.confidence * 100).toInt()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, color = color, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Text(
            text = detection.label,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$pct%",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Convert ImageProxy (RGBA_8888) to Bitmap safely
private fun androidx.camera.core.ImageProxy.toBitmapSafe(): Bitmap? {
    return try {
        val image = image ?: return null
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        bitmap
    } catch (e: Exception) {
        Log.w("ScanScreen", "Bitmap conversion failed: ${e.message}")
        null
    }
}
