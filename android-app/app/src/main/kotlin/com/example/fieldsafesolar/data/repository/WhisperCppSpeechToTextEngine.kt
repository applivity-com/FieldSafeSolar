package com.example.fieldsafesolar.data.repository

import android.content.Context
import android.util.Log
import com.example.fieldsafesolar.domain.SpeechToTextEngine
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperCppSpeechToTextEngine(private val context: Context) : SpeechToTextEngine {

    private var whisperContext: WhisperContext? = null
    private val loadMutex = Mutex()
    private var modelLoaded = false
    private val LOG_TAG = "WhisperCppSTT"

    // Call at app startup to pre-load the model so first transcription has no cold-start delay.
    suspend fun warmUp() = ensureModelLoaded()

    // Loads model from assets on first transcription call — no file copy needed.
    private suspend fun ensureModelLoaded() {
        if (modelLoaded) return
        loadMutex.withLock {
            if (modelLoaded) return
            try {
                whisperContext = WhisperContext.createContextFromAsset(context.assets, "models/ggml-tiny.en.bin")
                modelLoaded = true
                Log.d(LOG_TAG, "Whisper model loaded from assets")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to load Whisper model from assets", e)
            }
        }
    }

    override suspend fun transcribeAudio(audioFilePath: String): String = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        val ctx = whisperContext ?: return@withContext "[STT unavailable: model not loaded]"

        val audioFile = File(audioFilePath)
        if (!audioFile.exists()) {
            Log.e(LOG_TAG, "Audio file not found: $audioFilePath")
            return@withContext "[STT error: audio file not found]"
        }

        val audioData = readWavFile(audioFile)
            ?: return@withContext "[STT error: failed to read audio]"

        return@withContext try {
            ctx.transcribeData(audioData, printTimestamp = false).trim()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Transcription failed", e)
            "[STT error: transcription failed]"
        }
    }

    // Reads 16-bit PCM mono 16kHz WAV into normalized FloatArray for Whisper.
    private fun readWavFile(file: File): FloatArray? {
        return try {
            file.inputStream().use { inputStream ->
                inputStream.skip(44) // skip WAV header
                val bufferSize = 4096
                val byteBuffer = ByteBuffer.allocate(bufferSize)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                val floatSamples = mutableListOf<Float>()
                var bytesRead: Int
                while (inputStream.read(byteBuffer.array(), 0, bufferSize).also { bytesRead = it } != -1) {
                    byteBuffer.rewind()
                    for (i in 0 until bytesRead step 2) {
                        if (i + 1 < bytesRead) {
                            floatSamples.add(byteBuffer.getShort(i) / 32767.0f)
                        }
                    }
                    byteBuffer.clear()
                }
                floatSamples.toFloatArray()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error reading WAV file", e)
            null
        }
    }

    fun release() {
        runBlocking { whisperContext?.release() }
        whisperContext = null
        modelLoaded = false
        Log.d(LOG_TAG, "Whisper context released")
    }
}
