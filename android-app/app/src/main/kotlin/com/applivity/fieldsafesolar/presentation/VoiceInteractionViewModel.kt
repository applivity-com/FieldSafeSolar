package com.applivity.fieldsafesolar.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applivity.fieldsafesolar.data.db.entity.VoiceTranscriptEntity
import com.applivity.fieldsafesolar.data.model.AppSettings
import com.applivity.fieldsafesolar.data.model.ChecklistItem
import com.applivity.fieldsafesolar.data.model.EvidenceAnalysisRequest
import com.applivity.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.data.model.ReportGenerationRequest
import com.applivity.fieldsafesolar.data.model.SafetyReport
import com.applivity.fieldsafesolar.data.model.VisionFindings
import com.applivity.fieldsafesolar.data.model.VoiceTranscript
import com.applivity.fieldsafesolar.data.repository.GemmaEdgeAnalyzer
import com.applivity.fieldsafesolar.di.ServiceProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class VoiceInteractionViewModel(application: Application) : AndroidViewModel(application) {

    private val reportRepository = ServiceProvider.getReportRepository()
    private val aiSafetyAnalyzer = ServiceProvider.getAiSafetyAnalyzer()
    private val speechToTextEngine = ServiceProvider.getSpeechToTextEngine()
    private val audioRecorder = ServiceProvider.getAudioRecorder()
    private val speechOutput = ServiceProvider.getSpeechOutputEngine()
    private val checklistEngine = ServiceProvider.getChecklistEngine()
    private val settingsRepo = ServiceProvider.getAppSettingsRepository()

    private val gemmaAnalyzer get() = aiSafetyAnalyzer as? GemmaEdgeAnalyzer

    private val _uiState = MutableStateFlow(VoiceInteractionUiState())
    val uiState: StateFlow<VoiceInteractionUiState> = _uiState

    private var silenceLoopJob: Job? = null
    private var transcriptionJob: Job? = null
    private var recordingStartMs = 0L

    fun setVisionFindings(findings: VisionFindings) {
        _uiState.value = _uiState.value.copy(visionFindings = findings)
    }

    fun initInspection(inspectionType: InspectionType) {
        val state = _uiState.value
        if (state.inspectionType != inspectionType || state.checklistItems.isEmpty()) {
            val items = checklistEngine.getChecklist(inspectionType)
            val settings = settingsRepo.getSettings()
            _uiState.value = state.copy(
                inspectionType = inspectionType,
                checklistItems = items,
                currentItemIndex = 0,
                conversationTurns = emptyList(),
                recordingMaxMs = settings.maxRecordingSeconds * 1000L,
                silenceTimeoutMs = settings.silenceTimeoutSeconds * 1000L
            )
            gemmaAnalyzer?.startSession(inspectionType, _uiState.value.visionFindings)
            _uiState.value = _uiState.value.copy(isGemmaLoaded = gemmaAnalyzer?.isModelLoaded() == true)
            if (settings.ttsAutoReadQuestion) {
                viewModelScope.launch {
                    delay(700)
                    items.firstOrNull()?.let { speechOutput.speak(it.description) }
                }
            }
        } else {
            gemmaAnalyzer?.startSession(inspectionType, _uiState.value.visionFindings)
            _uiState.value = _uiState.value.copy(isGemmaLoaded = gemmaAnalyzer?.isModelLoaded() == true)
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording || _uiState.value.isTranscribing || _uiState.value.isAnalyzing) return
        viewModelScope.launch {
            val settings = settingsRepo.getSettings()
            val maxMs = settings.maxRecordingSeconds * 1000L
            val silenceTimeoutMs = settings.silenceTimeoutSeconds * 1000L

            val started = audioRecorder.startRecording()
            if (!started) {
                _uiState.value = _uiState.value.copy(error = "Failed to start recording")
                return@launch
            }
            recordingStartMs = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(
                isRecording = true,
                recordingElapsedMs = 0L,
                silenceCountdownMs = silenceTimeoutMs,
                recordingMaxMs = maxMs,
                silenceTimeoutMs = silenceTimeoutMs,
                error = null
            )

            // Sample first 500ms to calibrate ambient noise baseline
            delay(500)
            val threshold = when (settings.silenceMode) {
                AppSettings.SilenceMode.MANUAL -> settings.manualSilenceThreshold.toFloat()
                AppSettings.SilenceMode.AUTO_CALIBRATE -> {
                    val samples = (1..5).map {
                        delay(100)
                        audioRecorder.getCurrentAmplitude()
                    }
                    // Drop samples that look like speech (> 2× the minimum) so that a worker
                    // speaking immediately after pressing record doesn't inflate the baseline.
                    val minSample = samples.min()
                    val ambientSamples = samples.filter { it <= minSample * 2f }
                    val base = if (ambientSamples.size >= 2) ambientSamples.average().toFloat() else minSample
                    (base * 2.5f).coerceIn(500f, 6000f)
                }
            }

            var silenceAccumMs = 0L
            silenceLoopJob = launch {
                while (_uiState.value.isRecording) {
                    delay(200)
                    val elapsed = System.currentTimeMillis() - recordingStartMs
                    val amplitude = audioRecorder.getCurrentAmplitude()
                    silenceAccumMs = if (amplitude < threshold) silenceAccumMs + 200L else 0L
                    val silenceRemaining = (silenceTimeoutMs - silenceAccumMs).coerceAtLeast(0L)

                    _uiState.value = _uiState.value.copy(
                        recordingElapsedMs = elapsed,
                        silenceCountdownMs = silenceRemaining
                    )

                    if (silenceAccumMs >= silenceTimeoutMs || elapsed >= maxMs) {
                        stopAndAnalyze()
                        break
                    }
                }
            }
        }
    }

    fun stopAndAnalyze() {
        silenceLoopJob?.cancel()
        val durationMs = System.currentTimeMillis() - recordingStartMs
        transcriptionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                isTranscribing = true,
                transcribingProgress = 0f,
                transcribingDurationMs = durationMs,
                animatedTranscript = "",
                silenceCountdownMs = 0L
            )

            val audioUri: Uri? = audioRecorder.stopRecording()
            if (audioUri == null) {
                _uiState.value = _uiState.value.copy(isTranscribing = false, error = "Recording failed")
                return@launch
            }

            // Estimated progress bar — fills over expected transcription time
            val estimatedMs = (durationMs * 1.1f).toLong().coerceAtLeast(2000L)
            val progressJob = launch {
                val start = System.currentTimeMillis()
                while (true) {
                    val p = ((System.currentTimeMillis() - start).toFloat() / estimatedMs).coerceIn(0f, 0.95f)
                    _uiState.value = _uiState.value.copy(transcribingProgress = p)
                    delay(100)
                }
            }

            val transcript = speechToTextEngine.transcribeAudio(audioUri.path ?: "")
            progressJob.cancel()

            // Word-by-word animated reveal of transcript
            val words = transcript.trim().split(" ").filter { it.isNotEmpty() }
            val sb = StringBuilder()
            for (word in words) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(word)
                _uiState.value = _uiState.value.copy(
                    animatedTranscript = sb.toString(),
                    transcribingProgress = 1f
                )
                delay(75)
            }

            _uiState.value = _uiState.value.copy(isTranscribing = false, isAnalyzing = true)

            val state = _uiState.value
            val currentIndex = state.currentItemIndex
            val currentItem = state.checklistItems.getOrNull(currentIndex)

            val result = aiSafetyAnalyzer.analyzeEvidence(
                EvidenceAnalysisRequest(
                    inspectionType = state.inspectionType,
                    checklistStep = currentItem?.description ?: "Safety observation",
                    workerTranscript = transcript,
                    imageUri = null,
                    currentSafetyState = emptyMap(),
                    visionFindings = state.visionFindings,
                    isFirstTurn = state.conversationTurns.isEmpty(),
                    standardRef = currentItem?.standardRef
                )
            )

            val updatedItems = state.checklistItems.toMutableList().also { items ->
                if (currentIndex < items.size) {
                    items[currentIndex] = items[currentIndex].copy(
                        status = result.recommendedDecision.toChecklistStatus()
                    )
                }
            }

            val shouldAdvance = result.workerFollowupQuestion == null
            val nextIndex = if (shouldAdvance) (currentIndex + 1).coerceAtMost(updatedItems.size) else currentIndex

            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                currentTranscription = transcript,
                animatedTranscript = "",
                transcribingProgress = 0f,
                aiResponse = result.spokenSummary,
                aiResult = result,
                checklistItems = updatedItems,
                currentItemIndex = nextIndex,
                evidenceUris = state.evidenceUris + audioUri.toString(),
                voiceTranscripts = state.voiceTranscripts + VoiceTranscript(
                    id = UUID.randomUUID().toString(),
                    reportId = "",
                    timestamp = Instant.now(),
                    userSpeech = transcript,
                    aiResponse = result.spokenSummary,
                    audioUri = audioUri.toString()
                ),
                conversationTurns = state.conversationTurns + ConversationTurn(
                    workerSpeech = transcript,
                    aiResponse = result.spokenSummary,
                    followupQuestion = result.workerFollowupQuestion,
                    decision = result.recommendedDecision,
                    checklistStep = currentItem?.description ?: "",
                    safetyReasoningSummary = result.safetyReasoningSummary
                )
            )

            // Speak result summary, then speak the next question (follow-up or next checklist item)
            if (settingsRepo.getSettings().ttsAutoReadQuestion) {
                val nextQuestion: String? = when {
                    result.workerFollowupQuestion != null -> result.workerFollowupQuestion
                    shouldAdvance -> updatedItems.getOrNull(nextIndex)?.description
                    else -> null
                }
                val textToSpeak = if (nextQuestion != null) {
                    "${result.spokenSummary}. $nextQuestion"
                } else {
                    result.spokenSummary
                }
                speechOutput.speak(textToSpeak)
            } else {
                speechOutput.speak(result.spokenSummary)
            }
        }
    }

    fun cancelTranscriptionAndReRecord() {
        transcriptionJob?.cancel()
        audioRecorder.cancelRecording()
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isTranscribing = false,
            isAnalyzing = false,
            transcribingProgress = 0f,
            animatedTranscript = "",
            error = null
        )
        // Auto-start recording immediately — user said "re-record", not "go back to idle"
        startRecording()
    }

    fun repeatCurrentQuestion() {
        val state = _uiState.value
        val question = state.aiResult?.workerFollowupQuestion
            ?: state.checklistItems.getOrNull(state.currentItemIndex)?.description
            ?: return
        speechOutput.speak(question)
    }

    fun clearSavedReportId() {
        _uiState.value = _uiState.value.copy(savedReportId = null)
    }

    fun saveReport() {
        val state = _uiState.value
        if (state.voiceTranscripts.isEmpty() || state.isSavingReport) return
        viewModelScope.launch {
            _uiState.value = state.copy(isSavingReport = true)
            val overallDecision = state.checklistItems.worstCaseDecision()
            val report = aiSafetyAnalyzer.generateReport(
                ReportGenerationRequest(
                    inspectionType = state.inspectionType,
                    completedChecklist = state.checklistItems,
                    voiceTranscripts = state.voiceTranscripts,
                    overallDecision = overallDecision,
                    severity = overallDecision.toSeverity()
                )
            )
            reportRepository.saveReport(report)
            val transcriptEntities = state.voiceTranscripts.map { t ->
                VoiceTranscriptEntity.create(
                    reportId = report.reportId,
                    userSpeech = t.userSpeech,
                    aiResponse = t.aiResponse ?: "",
                    audioFileUri = t.audioUri
                )
            }
            if (transcriptEntities.isNotEmpty()) {
                ServiceProvider.getAppDatabase().voiceTranscriptDao().insertAll(transcriptEntities)
            }
            gemmaAnalyzer?.closeSession()
            _uiState.value = _uiState.value.copy(isSavingReport = false, savedReportId = report.reportId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        silenceLoopJob?.cancel()
        transcriptionJob?.cancel()
        gemmaAnalyzer?.closeSession()
    }

    private fun List<ChecklistItem>.worstCaseDecision(): SafetyReport.Decision = when {
        any { it.status == ChecklistItem.Status.COMPLETED_STOP_WORK } -> SafetyReport.Decision.STOP_WORK
        any { it.status == ChecklistItem.Status.COMPLETED_FAIL } -> SafetyReport.Decision.FAIL
        any { it.status == ChecklistItem.Status.COMPLETED_WARN } -> SafetyReport.Decision.WARN
        any { it.required && it.status == ChecklistItem.Status.PENDING } -> SafetyReport.Decision.WARN
        else -> SafetyReport.Decision.PASS
    }

    private fun EvidenceAnalysisResult.Decision.toChecklistStatus() = when (this) {
        EvidenceAnalysisResult.Decision.PASS -> ChecklistItem.Status.COMPLETED_PASS
        EvidenceAnalysisResult.Decision.WARN -> ChecklistItem.Status.COMPLETED_WARN
        EvidenceAnalysisResult.Decision.FAIL -> ChecklistItem.Status.COMPLETED_FAIL
        EvidenceAnalysisResult.Decision.STOP_WORK -> ChecklistItem.Status.COMPLETED_STOP_WORK
    }

    private fun SafetyReport.Decision.toSeverity() = when (this) {
        SafetyReport.Decision.PASS -> SafetyReport.Severity.LOW
        SafetyReport.Decision.WARN -> SafetyReport.Severity.MEDIUM
        SafetyReport.Decision.FAIL -> SafetyReport.Severity.HIGH
        SafetyReport.Decision.STOP_WORK -> SafetyReport.Severity.CRITICAL
    }
}

data class ConversationTurn(
    val workerSpeech: String,
    val aiResponse: String,
    val followupQuestion: String?,
    val decision: EvidenceAnalysisResult.Decision,
    val checklistStep: String,
    val safetyReasoningSummary: String = ""
)

data class VoiceInteractionUiState(
    val isGemmaLoaded: Boolean = false,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSavingReport: Boolean = false,
    val inspectionType: InspectionType = InspectionType.PPE_CHECK,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val currentItemIndex: Int = 0,
    val currentTranscription: String = "",
    val animatedTranscript: String = "",
    val transcribingProgress: Float = 0f,
    val transcribingDurationMs: Long = 0L,
    val aiResponse: String = "",
    val aiResult: EvidenceAnalysisResult? = null,
    val visionFindings: VisionFindings = VisionFindings.EMPTY,
    val conversationTurns: List<ConversationTurn> = emptyList(),
    val evidenceUris: List<String> = emptyList(),
    val voiceTranscripts: List<VoiceTranscript> = emptyList(),
    val savedReportId: String? = null,
    val error: String? = null,
    val recordingElapsedMs: Long = 0L,
    val recordingMaxMs: Long = 15_000L,
    val silenceCountdownMs: Long = 5_000L,
    val silenceTimeoutMs: Long = 5_000L,
)
