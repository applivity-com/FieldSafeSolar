package com.example.fieldsafesolar.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.fieldsafesolar.domain.CameraManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * CameraXManager: CameraX implementation of CameraManager
 */
class CameraXManager(private val context: Context) : CameraManager {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = ContextCompat.getMainExecutor(context)
    
    override suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Create preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Create image capture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind any existing use cases
                cameraProvider?.unbindAll()

                // Bind use cases
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                Log.d("CameraXManager", "✓ Camera initialized successfully")
            } catch (e: Exception) {
                Log.e("CameraXManager", "Failed to initialize camera", e)
            }
        }, cameraExecutor)
    }

    override suspend fun capturePhoto(): Uri? = suspendCancellableCoroutine { continuation ->
        val imageCapture = imageCapture ?: run {
            continuation.resumeWith(Result.success(null))
            return@suspendCancellableCoroutine
        }

        val photoFile = File(context.cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.d("CameraXManager", "✓ Photo saved: $savedUri")
                    continuation.resumeWith(Result.success(savedUri))
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXManager", "Photo capture failed", exception)
                    continuation.resumeWith(Result.success(null))
                }
            }
        )
    }

    override fun releaseCamera() {
        cameraProvider?.unbindAll()
        Log.d("CameraXManager", "Camera released")
    }

    override fun isCameraReady(): Boolean = imageCapture != null
}
