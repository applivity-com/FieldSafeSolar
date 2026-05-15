package com.example.fieldsafesolar.presentation

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldsafesolar.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
private const val TEMP_FILENAME = "gemma-4-E2B-it.litertlm.downloading"

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    object Complete : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

class ModelDownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val modelsDir =
        (application.getExternalFilesDir(null) ?: application.filesDir).resolve("models")
    val modelFile = modelsDir.resolve(MODEL_FILENAME)
    private val tempFile = modelsDir.resolve(TEMP_FILENAME)

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var downloadId = -1L
    private var progressJob: Job? = null

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId) handleDownloadComplete()
        }
    }

    init {
        application.registerReceiver(
            completionReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(completionReceiver)
        progressJob?.cancel()
    }

    fun modelExists(): Boolean = modelFile.exists()

    fun startDownload() {
        if (_state.value is DownloadState.Downloading) return

        // Cancel any stale download for this ID
        if (downloadId != -1L) downloadManager.remove(downloadId)
        tempFile.delete()
        modelsDir.mkdirs()

        val request = DownloadManager.Request(Uri.parse(BuildConfig.MODEL_DOWNLOAD_URL)).apply {
            addRequestHeader("Authorization", "Bearer ${BuildConfig.HF_TOKEN}")
            setTitle("FieldSafe AI Model")
            setDescription("Gemma 4 E2B · 2.58 GB")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(
                getApplication(), null, "models/$TEMP_FILENAME"
            )
        }

        downloadId = downloadManager.enqueue(request)
        _state.value = DownloadState.Downloading(0L, 0L)
        startProgressPolling()
    }

    fun cancelDownload() {
        if (downloadId != -1L) downloadManager.remove(downloadId)
        progressJob?.cancel()
        tempFile.delete()
        _state.value = DownloadState.Idle
    }

    fun retry() {
        _state.value = DownloadState.Idle
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val cursor = downloadManager.query(
                    DownloadManager.Query().setFilterById(downloadId)
                )
                cursor?.use {
                    if (!it.moveToFirst()) return@use
                    val bytesDownloaded = it.getLong(
                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val totalBytes = it.getLong(
                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED -> {
                            _state.value = DownloadState.Downloading(
                                bytesDownloaded.coerceAtLeast(0L),
                                totalBytes.coerceAtLeast(0L)
                            )
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            handleDownloadComplete()
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = it.getInt(
                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            _state.value = DownloadState.Failed("Download failed (code $reason)")
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun handleDownloadComplete() {
        if (_state.value is DownloadState.Complete) return
        progressJob?.cancel()
        if (tempFile.exists() && tempFile.renameTo(modelFile)) {
            _state.value = DownloadState.Complete
        } else if (modelFile.exists()) {
            // rename already happened (double-fire guard)
            _state.value = DownloadState.Complete
        } else {
            _state.value = DownloadState.Failed("Failed to save model file")
        }
    }
}
