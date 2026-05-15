package com.example.fieldsafesolar.presentation

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldsafesolar.data.model.BatchAnalysisRequest
import com.example.fieldsafesolar.data.model.ChecklistItem
import com.example.fieldsafesolar.data.model.InspectionType
import com.example.fieldsafesolar.data.model.QuestionAnswer
import com.example.fieldsafesolar.data.model.VisionDetection
import com.example.fieldsafesolar.di.ServiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class AppMode { WORKER, INSPECTOR }

enum class InputState { IDLE, RECORDING, TRANSCRIBING, CONFIRM_CUSTOM, PROCESSING_PHOTO, FINALIZING }

data class QuestionSliderUiState(
    val mode: AppMode = AppMode.WORKER,
    val inspectionType: InspectionType? = null,
    val questions: List<ChecklistItem> = emptyList(),
    val currentIndex: Int = 0,
    val answers: List<QuestionAnswer> = emptyList(),
    val inputState: InputState = InputState.IDLE,
    val recordingAmplitude: Float = 0f,
    val customTranscript: String = "",
    val showPhotoPrompt: Boolean = false,
    val pendingAnswer: QuestionAnswer? = null,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val showRecordingNotice: Boolean = false,
) {
    val currentQuestion: ChecklistItem? get() = questions.getOrNull(currentIndex)
    val progress: Float get() = if (questions.isEmpty()) 0f else currentIndex.toFloat() / questions.size
    val questionNumber: Int get() = currentIndex + 1
    val totalQuestions: Int get() = questions.size
}

class QuestionSliderViewModel(application: Application) : AndroidViewModel(application) {

    private val checklistEngine = ServiceProvider.getChecklistEngine()
    private val audioRecorder = ServiceProvider.getAudioRecorder()
    private val sttEngine = ServiceProvider.getSpeechToTextEngine()
    private val ttsEngine = ServiceProvider.getSpeechOutputEngine()
    private val visionAnalyzer = ServiceProvider.getVisionAnalyzer()

    private val _uiState = MutableStateFlow(QuestionSliderUiState())
    val uiState: StateFlow<QuestionSliderUiState> = _uiState

    private var amplitudePollingJob: Job? = null
    private var noticeShownThisSession = false

    // Background transcription queue — serialized via mutex (WhisperContext is not concurrent-safe)
    // Single-thread executor serializes Whisper calls (WhisperContext is not thread-safe).
    // Future.get(timeout) guarantees a return even when native JNI blocks indefinitely.
    private val whisperExecutor = Executors.newSingleThreadExecutor()
    private val pendingTranscriptionJobs = ConcurrentHashMap<String, Job>()
    private val _pendingCount = MutableStateFlow(0)
    val pendingTranscriptionCount: StateFlow<Int> = _pendingCount

    fun init(inspectionType: InspectionType, mode: AppMode) {
        val state = _uiState.value
        if (state.inspectionType == inspectionType && state.questions.isNotEmpty()) return
        val questions = checklistEngine.getChecklist(inspectionType)
        _uiState.value = QuestionSliderUiState(
            mode = mode,
            inspectionType = inspectionType,
            questions = questions,
        )
        speakCurrentQuestion()
    }

    fun answerYes() {
        val q = _uiState.value.currentQuestion ?: return
        val answer = QuestionAnswer(
            questionId = q.id,
            questionText = q.description,
            standardRef = q.standardRef,
            answerType = QuestionAnswer.AnswerType.YES,
        )
        _uiState.value = _uiState.value.copy(pendingAnswer = answer, showPhotoPrompt = true)
    }

    fun answerNo() {
        val q = _uiState.value.currentQuestion ?: return
        val answer = QuestionAnswer(
            questionId = q.id,
            questionText = q.description,
            standardRef = q.standardRef,
            answerType = QuestionAnswer.AnswerType.NO,
        )
        _uiState.value = _uiState.value.copy(pendingAnswer = answer, showPhotoPrompt = true)
    }

    fun skipQuestion() {
        val q = _uiState.value.currentQuestion ?: return
        commitAndAdvance(
            QuestionAnswer(
                questionId = q.id,
                questionText = q.description,
                standardRef = q.standardRef,
                answerType = QuestionAnswer.AnswerType.SKIP,
            )
        )
    }

    fun startCustomRecording() {
        if (!noticeShownThisSession) {
            val seen = getApplication<Application>()
                .getSharedPreferences("fss_notices", Context.MODE_PRIVATE)
                .getBoolean("recording_notice_seen", false)
            if (!seen) {
                _uiState.value = _uiState.value.copy(showRecordingNotice = true)
                return
            }
        }
        _uiState.value = _uiState.value.copy(inputState = InputState.RECORDING, customTranscript = "")
        viewModelScope.launch {
            val started = audioRecorder.startRecording()
            if (!started) {
                _uiState.value = _uiState.value.copy(
                    inputState = InputState.IDLE,
                    errorMessage = "Could not start microphone",
                )
                return@launch
            }
            amplitudePollingJob = viewModelScope.launch {
                while (audioRecorder.isRecording()) {
                    _uiState.value = _uiState.value.copy(recordingAmplitude = audioRecorder.getCurrentAmplitude())
                    delay(200)
                }
            }
        }
    }

    // Main recording path: stop → commit immediately with null transcript → transcribe in background
    fun stopCustomRecordingAsync() {
        amplitudePollingJob?.cancel()
        amplitudePollingJob = null
        viewModelScope.launch {
            val uri = audioRecorder.stopRecording() ?: run {
                _uiState.value = _uiState.value.copy(inputState = InputState.IDLE, recordingAmplitude = 0f)
                return@launch
            }
            val q = _uiState.value.currentQuestion ?: return@launch

            val audioPath = withContext(Dispatchers.IO) { copyToTranscriptionTemp(uri, q.id) }

            val answer = QuestionAnswer(
                questionId = q.id,
                questionText = q.description,
                standardRef = q.standardRef,
                answerType = QuestionAnswer.AnswerType.CUSTOM,
                customTranscript = null,
            )

            // Show photo prompt before advancing for all question types; transcription runs in background
            _uiState.value = _uiState.value.copy(
                recordingAmplitude = 0f,
                inputState = InputState.IDLE,
                pendingAnswer = answer,
                showPhotoPrompt = true,
            )

            _pendingCount.value++
            val job = viewModelScope.launch {
                // whisperExecutor serializes calls; Future.get(30s) genuinely unblocks even
                // if the JNI thread is stuck — unlike withTimeoutOrNull which cannot interrupt native code.
                val raw = withContext(Dispatchers.IO) {
                    val future = whisperExecutor.submit<String> {
                        val result = try { kotlinx.coroutines.runBlocking { sttEngine.transcribeAudio(audioPath) } }
                        catch (_: Exception) { "" }
                        try { File(audioPath).delete() } catch (_: Exception) {}
                        result
                    }
                    try { future.get(5, TimeUnit.MINUTES) }
                    catch (_: TimeoutException) { "" }
                    catch (_: Exception) { "" }
                }
                // Strip trailing voice command that RealWear picks up when the worker says "Stop"
                val transcript = stripTrailingStopCommand(raw)
                // Update answer whether it's still pending (open-ended waiting for photo) or already committed
                _uiState.value = _uiState.value.let { s ->
                    val updatedPending = if (s.pendingAnswer?.questionId == q.id)
                        s.pendingAnswer.copy(customTranscript = transcript) else s.pendingAnswer
                    s.copy(
                        pendingAnswer = updatedPending,
                        answers = s.answers.map { a ->
                            if (a.questionId == q.id) a.copy(customTranscript = transcript) else a
                        }
                    )
                }
                pendingTranscriptionJobs.remove(q.id)
                _pendingCount.value = maxOf(0, _pendingCount.value - 1)
            }
            pendingTranscriptionJobs[q.id] = job
        }
    }

    // Legacy blocking path — kept so existing call sites compile; not reached from main UI flow
    fun stopCustomRecording() {
        amplitudePollingJob?.cancel()
        amplitudePollingJob = null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(inputState = InputState.TRANSCRIBING, recordingAmplitude = 0f)
            val uri = audioRecorder.stopRecording()
            val transcript = if (uri != null) {
                try { sttEngine.transcribeAudio(uri.path ?: "") } catch (_: Exception) { "" }
            } else ""
            _uiState.value = _uiState.value.copy(inputState = InputState.CONFIRM_CUSTOM, customTranscript = transcript)
        }
    }

    fun confirmCustomAnswer() {
        val q = _uiState.value.currentQuestion ?: return
        val transcript = _uiState.value.customTranscript
        val answer = QuestionAnswer(
            questionId = q.id,
            questionText = q.description,
            standardRef = q.standardRef,
            answerType = QuestionAnswer.AnswerType.CUSTOM,
            customTranscript = transcript,
        )
        _uiState.value = _uiState.value.copy(inputState = InputState.IDLE, pendingAnswer = answer, showPhotoPrompt = true)
    }

    fun dismissRecordingNotice(dontShowAgain: Boolean) {
        noticeShownThisSession = true
        if (dontShowAgain) {
            getApplication<Application>()
                .getSharedPreferences("fss_notices", Context.MODE_PRIVATE)
                .edit().putBoolean("recording_notice_seen", true).apply()
        }
        _uiState.value = _uiState.value.copy(showRecordingNotice = false)
        startCustomRecording()
    }

    fun reRecord() {
        _uiState.value = _uiState.value.copy(inputState = InputState.IDLE, customTranscript = "")
    }

    fun onPhotoCaptured(uri: Uri) {
        val pending = _uiState.value.pendingAnswer ?: return
        _uiState.value = _uiState.value.copy(inputState = InputState.PROCESSING_PHOTO)
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                if (bitmap != null) {
                    val detections = visionAnalyzer.analyzeFrame(bitmap)
                    val ocrText = visionAnalyzer.extractText(bitmap)
                    val annotatedPath = withContext(Dispatchers.IO) { saveAnnotatedPhoto(bitmap, detections) }
                    bitmap.recycle()
                    commitAndAdvance(pending.copy(
                        photoFindings = detections.map { "${it.label} ${(it.confidence * 100).toInt()}%" },
                        ocrText = ocrText?.take(300),
                        photoPath = annotatedPath,
                    ))
                } else {
                    commitAndAdvance(pending)
                }
            } catch (_: Exception) {
                commitAndAdvance(pending)
            }
        }
    }

    fun skipPhoto() {
        val pending = _uiState.value.pendingAnswer ?: return
        commitAndAdvance(pending)
    }

    fun goBack() {
        val state = _uiState.value
        if (state.currentIndex <= 0) return
        state.answers.lastOrNull()?.let { removed ->
            pendingTranscriptionJobs.remove(removed.questionId)?.let { job ->
                job.cancel()
                _pendingCount.value = maxOf(0, _pendingCount.value - 1)
            }
        }
        val newAnswers = state.answers.dropLast(1)
        _uiState.value = state.copy(
            currentIndex = state.currentIndex - 1,
            answers = newAnswers,
            inputState = InputState.IDLE,
            showPhotoPrompt = false,
            pendingAnswer = null,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // Called from FINALIZING review screen when worker confirms transcripts look correct
    fun confirmTranscriptReview() {
        val state = _uiState.value
        state.inspectionType?.let { t ->
            ServiceProvider.setPendingBatch(BatchAnalysisRequest(t, state.answers))
        }
        _uiState.value = state.copy(isComplete = true, inputState = InputState.IDLE)
    }

    private fun commitAndAdvance(answer: QuestionAnswer) {
        val state = _uiState.value
        val newAnswers = state.answers + answer
        val next = state.currentIndex + 1
        if (next >= state.questions.size) {
            val hasCustom = newAnswers.any { it.answerType == QuestionAnswer.AnswerType.CUSTOM }
            if (hasCustom) {
                _uiState.value = state.copy(
                    answers = newAnswers,
                    inputState = InputState.FINALIZING,
                    showPhotoPrompt = false,
                    pendingAnswer = null,
                )
            } else {
                state.inspectionType?.let { inspType ->
                    ServiceProvider.setPendingBatch(BatchAnalysisRequest(inspType, newAnswers))
                }
                _uiState.value = state.copy(
                    answers = newAnswers,
                    isComplete = true,
                    showPhotoPrompt = false,
                    pendingAnswer = null,
                    inputState = InputState.IDLE,
                )
            }
        } else {
            _uiState.value = state.copy(
                answers = newAnswers,
                currentIndex = next,
                inputState = InputState.IDLE,
                showPhotoPrompt = false,
                pendingAnswer = null,
                customTranscript = "",
            )
            speakCurrentQuestion()
        }
    }

    fun replayQuestion() = speakCurrentQuestion()

    private fun speakCurrentQuestion() {
        val q = _uiState.value.currentQuestion ?: return
        viewModelScope.launch { ttsEngine.speak(q.description) }
    }

    private fun copyToTranscriptionTemp(uri: Uri, questionId: String): String {
        val sourcePath = uri.path ?: return ""
        val dir = File(getApplication<Application>().filesDir, "audio_tmp").also { it.mkdirs() }
        val dest = File(dir, "$questionId.wav")
        try { File(sourcePath).copyTo(dest, overwrite = true) } catch (_: Exception) {}
        return dest.absolutePath
    }

    private fun saveAnnotatedPhoto(source: Bitmap, detections: List<VisionDetection>): String? {
        return try {
            val dir = File(getApplication<Application>().filesDir, "photos").also { it.mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            if (detections.isEmpty()) {
                dest.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 65, it) }
                return dest.absolutePath
            }
            val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotated)
            val bannerH = (source.height * 0.13f).coerceAtLeast(72f)
            val bannerTop = source.height - bannerH
            canvas.drawRect(
                0f, bannerTop, source.width.toFloat(), source.height.toFloat(),
                Paint().apply { color = Color.argb(210, 0, 0, 0); style = Paint.Style.FILL }
            )
            val textSizePx = (source.height * 0.038f).coerceIn(22f, 52f)
            val textPaint = Paint().apply {
                color = Color.WHITE; textSize = textSizePx
                typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            val labelText = detections.take(4).joinToString("  |  ") { d ->
                "${d.label} ${(d.confidence * 100).toInt()}%"
            }
            canvas.drawText(labelText, 16f, bannerTop + bannerH / 2f + textSizePx / 3f, textPaint)
            dest.outputStream().use { annotated.compress(Bitmap.CompressFormat.JPEG, 65, it) }
            annotated.recycle()
            dest.absolutePath
        } catch (_: Exception) { null }
    }

    private fun stripTrailingStopCommand(transcript: String): String {
        val t = transcript.trimEnd()
        val lower = t.lowercase()
        val suffixes = listOf("stop recording.", "stop recording", "stop.", "stop")
        for (suffix in suffixes) {
            if (lower.endsWith(suffix)) {
                return t.dropLast(suffix.length).trimEnd().trimEnd('.').trimEnd()
            }
        }
        return t
    }

    override fun onCleared() {
        super.onCleared()
        amplitudePollingJob?.cancel()
        pendingTranscriptionJobs.values.forEach { it.cancel() }
        pendingTranscriptionJobs.clear()
        whisperExecutor.shutdownNow()
        try { audioRecorder.cancelRecording() } catch (_: Exception) {}
        ttsEngine.stopSpeaking()
    }
}
