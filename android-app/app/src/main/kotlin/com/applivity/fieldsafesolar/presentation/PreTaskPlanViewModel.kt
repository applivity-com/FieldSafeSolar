package com.applivity.fieldsafesolar.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applivity.fieldsafesolar.di.ServiceProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class JhaQuestion(
    val text: String,
    val standardRef: String,
)

enum class JhaAnswerState { PENDING, CONFIRMED, NOT_CONFIRMED }

enum class PreTaskInputState { READING, IDLE, RECORDING, TRANSCRIBING, ANSWERED }

data class PreTaskPlanUiState(
    val currentIndex: Int = 0,
    val answers: List<JhaAnswerState> = List(4) { JhaAnswerState.PENDING },
    val inputState: PreTaskInputState = PreTaskInputState.READING,
    val transcript: String = "",
    val isComplete: Boolean = false,
    val error: String? = null,
    val recordingAmplitude: Float = 0f,
)

class PreTaskPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val audioRecorder = ServiceProvider.getAudioRecorder()
    private val sttEngine = ServiceProvider.getSpeechToTextEngine()
    private val ttsEngine = ServiceProvider.getSpeechOutputEngine()

    val questions = listOf(
        JhaQuestion(
            text = "Have all energy sources and hazards for this task been identified?",
            standardRef = "OSHA 1910.147(c)(6)"
        ),
        JhaQuestion(
            text = "Are all team members briefed on the hazards and emergency procedures for this task?",
            standardRef = "OSHA 1926.21"
        ),
        JhaQuestion(
            text = "Is the Emergency Action Plan accessible and is the egress route known?",
            standardRef = "OSHA 1910.38"
        ),
        JhaQuestion(
            text = "Does every team member understand their Stop Work Authority — the right to halt work immediately if conditions become unsafe?",
            standardRef = "ANSI/SEIA 301-2025"
        ),
    )

    private val _uiState = MutableStateFlow(PreTaskPlanUiState())
    val uiState: StateFlow<PreTaskPlanUiState> = _uiState

    private var amplitudeJob: Job? = null
    private var recordingStartMs = 0L

    fun init() {
        if (_uiState.value.inputState != PreTaskInputState.READING) return
        speakCurrentQuestion()
    }

    fun startRecording() {
        val state = _uiState.value
        if (state.inputState == PreTaskInputState.RECORDING || state.inputState == PreTaskInputState.TRANSCRIBING) return
        viewModelScope.launch {
            val started = audioRecorder.startRecording()
            if (!started) {
                _uiState.value = _uiState.value.copy(error = "Could not start recording")
                return@launch
            }
            recordingStartMs = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(inputState = PreTaskInputState.RECORDING, error = null)
            amplitudeJob = launch {
                while (_uiState.value.inputState == PreTaskInputState.RECORDING) {
                    _uiState.value = _uiState.value.copy(recordingAmplitude = audioRecorder.getCurrentAmplitude())
                    delay(100)
                }
            }
        }
    }

    fun stopAndTranscribe() {
        amplitudeJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(inputState = PreTaskInputState.TRANSCRIBING)
            val uri: Uri? = audioRecorder.stopRecording()
            val transcript = if (uri != null) sttEngine.transcribeAudio(uri.path ?: "") else ""
            val isYes = classifyYes(transcript)
            applyAnswer(if (isYes) JhaAnswerState.CONFIRMED else JhaAnswerState.NOT_CONFIRMED, transcript)
        }
    }

    fun answerManually(confirmed: Boolean) {
        applyAnswer(if (confirmed) JhaAnswerState.CONFIRMED else JhaAnswerState.NOT_CONFIRMED, "")
    }

    fun confirmComplete() {
        val allConfirmed = _uiState.value.answers.all { it == JhaAnswerState.CONFIRMED }
        ServiceProvider.setJhaConfirmed(allConfirmed)
        _uiState.value = _uiState.value.copy(isComplete = true)
    }

    private fun applyAnswer(answer: JhaAnswerState, transcript: String) {
        val state = _uiState.value
        val newAnswers = state.answers.toMutableList().also { it[state.currentIndex] = answer }
        val nextIndex = state.currentIndex + 1
        val allAnswered = nextIndex >= questions.size
        _uiState.value = state.copy(
            answers = newAnswers,
            transcript = transcript,
            inputState = if (allAnswered) PreTaskInputState.ANSWERED else PreTaskInputState.READING,
            currentIndex = if (allAnswered) state.currentIndex else nextIndex,
        )
        if (!allAnswered) {
            viewModelScope.launch {
                delay(600)
                speakCurrentQuestion()
            }
        }
    }

    private fun speakCurrentQuestion() {
        val idx = _uiState.value.currentIndex
        val q = questions.getOrNull(idx) ?: return
        _uiState.value = _uiState.value.copy(inputState = PreTaskInputState.READING)
        viewModelScope.launch {
            ttsEngine.speak(q.text)
            delay(800)
            _uiState.value = _uiState.value.copy(inputState = PreTaskInputState.IDLE)
        }
    }

    fun replayQuestion() {
        speakCurrentQuestion()
    }

    private fun classifyYes(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("yes") || lower.contains("confirm") ||
            lower.contains("affirmative") || lower.contains("correct") ||
            lower.contains("ready") || lower.contains("done")
    }

    override fun onCleared() {
        super.onCleared()
        amplitudeJob?.cancel()
    }
}
