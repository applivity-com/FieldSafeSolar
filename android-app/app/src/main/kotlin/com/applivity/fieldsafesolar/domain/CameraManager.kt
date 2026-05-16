package com.applivity.fieldsafesolar.domain

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * CameraManager interface: Abstracts camera operations
 */
interface CameraManager {
    /**
     * Initialize camera with lifecycle binding
     */
    suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    )

    /**
     * Capture photo and save to cache
     * Returns URI to saved photo
     */
    suspend fun capturePhoto(): Uri?

    /**
     * Release camera resources
     */
    fun releaseCamera()

    /**
     * Check if camera is ready
     */
    fun isCameraReady(): Boolean
}
