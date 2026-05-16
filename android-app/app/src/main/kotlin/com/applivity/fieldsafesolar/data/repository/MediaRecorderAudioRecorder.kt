package com.applivity.fieldsafesolar.data.repository

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import com.applivity.fieldsafesolar.domain.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * Records 16kHz mono PCM and writes a standard WAV file compatible with Whisper.cpp.
 * Uses AudioRecord (not MediaRecorder) to produce raw PCM without container encoding.
 */
class MediaRecorderAudioRecorder(private val context: Context) : AudioRecorder {

    private val sampleRate = 16000
    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private var outputFile: File? = null

    @Volatile private var _isRecording = false
    @Volatile private var _currentAmplitude: Float = 0f
    private var startTime = 0L

    override suspend fun startRecording(): Boolean {
        if (_isRecording) return false
        return withContext(Dispatchers.IO) {
            try {
                pcmBuffer.reset()
                outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")

                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 4
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioRecorder", "AudioRecord failed to initialize")
                    record.release()
                    return@withContext false
                }

                audioRecord = record
                _isRecording = true
                startTime = System.currentTimeMillis()
                record.startRecording()

                recordingJob = scope.launch {
                    val buffer = ShortArray(minBufferSize / 2)
                    while (_isRecording) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                        if (read > 0) {
                            // Compute RMS amplitude for silence detection
                            var sum = 0.0
                            for (i in 0 until read) sum += buffer[i].toDouble() * buffer[i].toDouble()
                            _currentAmplitude = sqrt(sum / read).toFloat()

                            val bytes = ByteArray(read * 2)
                            for (i in 0 until read) {
                                bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                                bytes[i * 2 + 1] = ((buffer[i].toInt() ushr 8) and 0xFF).toByte()
                            }
                            synchronized(pcmBuffer) { pcmBuffer.write(bytes) }
                        }
                    }
                }

                Log.d("AudioRecorder", "Recording started: ${outputFile?.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to start recording", e)
                _isRecording = false
                false
            }
        }
    }

    override suspend fun stopRecording(): Uri? {
        if (!_isRecording) return null
        return withContext(Dispatchers.IO) {
            try {
                _isRecording = false
                recordingJob?.join()
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                val pcmData = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
                val file = outputFile ?: return@withContext null

                writeWavFile(file, pcmData)
                Log.d("AudioRecorder", "WAV saved: ${file.absolutePath} (${file.length()} bytes, ${pcmData.size / 2 / sampleRate}s)")
                Uri.fromFile(file)
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to stop recording", e)
                null
            }
        }
    }

    override fun isRecording(): Boolean = _isRecording

    override fun getRecordingDuration(): Int =
        if (_isRecording) ((System.currentTimeMillis() - startTime) / 1000).toInt() else 0

    override fun cancelRecording() {
        _isRecording = false
        _currentAmplitude = 0f
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        outputFile?.delete()
        outputFile = null
    }

    override fun getCurrentAmplitude(): Float = _currentAmplitude

    // Writes a standard 44-byte RIFF/WAV header followed by 16-bit PCM data.
    private fun writeWavFile(file: File, pcmData: ByteArray) {
        RandomAccessFile(file, "rw").use { raf ->
            val dataSize = pcmData.size
            val byteRate = sampleRate * 2  // mono, 16-bit

            fun wi(v: Int) = raf.write(byteArrayOf(
                (v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte(),
                ((v ushr 16) and 0xFF).toByte(), ((v ushr 24) and 0xFF).toByte()
            ))
            fun ws(v: Int) = raf.write(byteArrayOf(
                (v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte()
            ))

            raf.write("RIFF".toByteArray()); wi(dataSize + 36)
            raf.write("WAVEfmt ".toByteArray()); wi(16)
            ws(1); ws(1)                   // PCM, mono
            wi(sampleRate); wi(byteRate)   // sample rate, byte rate
            ws(2); ws(16)                  // block align, bits per sample
            raf.write("data".toByteArray()); wi(dataSize)
            raf.write(pcmData)
        }
    }
}
