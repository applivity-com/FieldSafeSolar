package com.applivity.fieldsafesolar.di

import android.content.Context
import android.util.Log
import com.applivity.fieldsafesolar.data.db.AppDatabase
import com.applivity.fieldsafesolar.data.model.BatchAnalysisRequest
import com.applivity.fieldsafesolar.data.model.BatchAnalysisResult
import com.applivity.fieldsafesolar.data.model.EewpRecord
import com.applivity.fieldsafesolar.data.repository.AppSettingsRepository
import com.applivity.fieldsafesolar.data.repository.CameraXManager
import com.applivity.fieldsafesolar.data.repository.MlKitVisionAnalyzer
import com.applivity.fieldsafesolar.data.repository.LocalChecklistEngine
import com.applivity.fieldsafesolar.data.repository.MediaRecorderAudioRecorder
import com.applivity.fieldsafesolar.data.repository.AndroidTextToSpeechEngine
import com.applivity.fieldsafesolar.data.repository.RoomReportRepository
import com.applivity.fieldsafesolar.data.repository.DemoStubAnalyzer
import com.applivity.fieldsafesolar.data.repository.GemmaEdgeAnalyzer
import com.applivity.fieldsafesolar.data.repository.WhisperCppSpeechToTextEngine
import com.applivity.fieldsafesolar.domain.AiSafetyAnalyzer
import com.applivity.fieldsafesolar.domain.AudioRecorder
import com.applivity.fieldsafesolar.domain.CameraManager
import com.applivity.fieldsafesolar.domain.ChecklistEngine
import com.applivity.fieldsafesolar.domain.ReportRepository
import com.applivity.fieldsafesolar.domain.SpeechToTextEngine
import com.applivity.fieldsafesolar.domain.SpeechOutputEngine

/**
 * Service Provider: Dependency Injection container for all services.
 * Centralizes instantiation and access to all repositories, engines, and utilities.
 */
object ServiceProvider {
    
    private lateinit var appDatabase: AppDatabase
    private lateinit var reportRepository: ReportRepository
    private lateinit var aiSafetyAnalyzer: AiSafetyAnalyzer
    private lateinit var checklistEngine: ChecklistEngine
    private lateinit var speechToTextEngine: SpeechToTextEngine
    private lateinit var speechOutputEngine: SpeechOutputEngine
    private lateinit var cameraManager: CameraManager
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var visionAnalyzer: MlKitVisionAnalyzer
    private lateinit var appSettingsRepository: AppSettingsRepository

    private var initialized = false

    // Transient cross-screen data — cleared after use
    @Volatile private var pendingBatch: BatchAnalysisRequest? = null
    @Volatile private var pendingResult: BatchAnalysisResult? = null
    @Volatile private var jhaConfirmed: Boolean = false
    @Volatile private var pendingEewpRecord: EewpRecord? = null
    
    /**
     * Initialize all services. Should be called once from Application.onCreate()
     */
    fun initialize(context: Context) {
        if (initialized) {
            Log.w("ServiceProvider", "Already initialized")
            return
        }
        
        try {
            Log.d("ServiceProvider", "Initializing services...")
            
            // 1. Initialize Room Database (singleton)
            appDatabase = AppDatabase.getInstance(context)
            Log.d("ServiceProvider", "✓ Room database initialized")
            
            // 2. Initialize Repositories
            reportRepository = RoomReportRepository(
                appDatabase.safetyReportDao(),
                appDatabase.evidenceDao()
            )
            Log.d("ServiceProvider", "✓ Report repository initialized")
            
            // 3. Initialize ChecklistEngine (in-memory)
            checklistEngine = LocalChecklistEngine()
            Log.d("ServiceProvider", "✓ Checklist engine initialized")
            
            // 4. Initialize AI Safety Analyzer (GemmaEdgeAnalyzer loads model lazily on first use;
            //    falls back to DemoStubAnalyzer internally if model files are absent)
            aiSafetyAnalyzer = GemmaEdgeAnalyzer(context)
            Log.d("ServiceProvider", "✓ AI Safety Analyzer initialized (Gemma lazy-load)")
            
            // 5. Initialize Speech-to-Text Engine (Whisper — model loads lazily on first use)
            speechToTextEngine = WhisperCppSpeechToTextEngine(context)
            Log.d("ServiceProvider", "✓ Speech-to-Text engine initialized")
            
            // 6. Initialize Speech Output Engine (Android TTS)
            speechOutputEngine = try {
                AndroidTextToSpeechEngine(context)
            } catch (e: Exception) {
                Log.e("ServiceProvider", "Failed to initialize TTS, using stub", e)
                object : SpeechOutputEngine {
                    override fun speak(text: String) { Log.w("ServiceProvider", "TTS stub: $text") }
                    override fun speakAndThen(text: String, onDone: () -> Unit) { Log.w("ServiceProvider", "TTS stub: $text"); onDone() }
                    override fun stopSpeaking() {}
                }
            }
            Log.d("ServiceProvider", "✓ Speech Output Engine initialized")
            
            // 7. Initialize Camera Manager (CameraX)
            cameraManager = CameraXManager(context)
            Log.d("ServiceProvider", "✓ Camera Manager initialized")
            
            // 8. Initialize Audio Recorder (MediaRecorder)
            audioRecorder = MediaRecorderAudioRecorder(context)
            Log.d("ServiceProvider", "✓ Audio Recorder initialized")

            // 9. Initialize ML Kit Vision Analyzer (on-device, no internet required)
            visionAnalyzer = MlKitVisionAnalyzer()
            Log.d("ServiceProvider", "✓ ML Kit Vision Analyzer initialized")

            // 10. Initialize App Settings Repository
            appSettingsRepository = AppSettingsRepository(context)
            Log.d("ServiceProvider", "✓ App Settings Repository initialized")

            initialized = true
            Log.d("ServiceProvider", "✅ All services initialized successfully")
            
        } catch (e: Exception) {
            Log.e("ServiceProvider", "Fatal error during initialization", e)
            throw RuntimeException("Failed to initialize ServiceProvider", e)
        }
    }
    
    // Getters for all services
    
    fun getReportRepository(): ReportRepository {
        checkInitialized()
        return reportRepository
    }
    
    fun getAiSafetyAnalyzer(): AiSafetyAnalyzer {
        checkInitialized()
        return aiSafetyAnalyzer
    }
    
    fun getChecklistEngine(): ChecklistEngine {
        checkInitialized()
        return checklistEngine
    }
    
    fun getSpeechToTextEngine(): SpeechToTextEngine {
        checkInitialized()
        return speechToTextEngine
    }
    
    fun getSpeechOutputEngine(): SpeechOutputEngine {
        checkInitialized()
        return speechOutputEngine
    }
    
    fun getAppDatabase(): AppDatabase {
        checkInitialized()
        return appDatabase
    }

    fun getAnalysisStatusFlow(): kotlinx.coroutines.flow.StateFlow<String> {
        val analyzer = aiSafetyAnalyzer
        return if (analyzer is GemmaEdgeAnalyzer) analyzer.statusFlow
        else kotlinx.coroutines.flow.MutableStateFlow("Demo mode")
    }

    fun isGemmaModelLoaded(): Boolean {
        val analyzer = aiSafetyAnalyzer
        return analyzer is GemmaEdgeAnalyzer && analyzer.isModelLoaded()
    }

    fun getModelFilePath(): String {
        val analyzer = aiSafetyAnalyzer
        return if (analyzer is GemmaEdgeAnalyzer) analyzer.getModelFilePath()
        else "N/A (Demo mode)"
    }

    fun getCameraManager(): CameraManager {
        checkInitialized()
        return cameraManager
    }
    
    fun getAudioRecorder(): AudioRecorder {
        checkInitialized()
        return audioRecorder
    }

    fun getVisionAnalyzer(): MlKitVisionAnalyzer {
        checkInitialized()
        return visionAnalyzer
    }

    fun getAppSettingsRepository(): AppSettingsRepository {
        checkInitialized()
        return appSettingsRepository
    }

    fun setPendingBatch(request: BatchAnalysisRequest) { pendingBatch = request }
    fun getPendingBatch(): BatchAnalysisRequest? = pendingBatch
    fun clearPendingBatch() { pendingBatch = null }

    fun setPendingResult(result: BatchAnalysisResult) { pendingResult = result }
    fun getPendingResult(): BatchAnalysisResult? = pendingResult
    fun clearPendingResult() { pendingResult = null }

    fun setJhaConfirmed(confirmed: Boolean) { jhaConfirmed = confirmed }
    fun getJhaConfirmed(): Boolean = jhaConfirmed
    fun clearJhaConfirmed() { jhaConfirmed = false }

    fun setPendingEewpRecord(record: EewpRecord) { pendingEewpRecord = record }
    fun getPendingEewpRecord(): EewpRecord? = pendingEewpRecord
    fun clearPendingEewpRecord() { pendingEewpRecord = null }

    private fun checkInitialized() {
        if (!initialized) {
            throw IllegalStateException("ServiceProvider not initialized. Call initialize(context) first.")
        }
    }
}
