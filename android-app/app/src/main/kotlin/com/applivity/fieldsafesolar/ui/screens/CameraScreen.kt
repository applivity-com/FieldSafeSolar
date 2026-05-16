package com.applivity.fieldsafesolar.ui.screens

import android.net.Uri
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applivity.fieldsafesolar.di.ServiceProvider
import com.applivity.fieldsafesolar.domain.CameraManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * CameraScreen: Full-screen camera preview with capture button
 * Used for capturing evidence photos during inspection
 */
@Composable
fun CameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Capture Evidence Photo"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { ServiceProvider.getCameraManager() }

    var isCameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }

    // Initialize camera
    LaunchedEffect(Unit) {
        cameraManager.initializeCamera(lifecycleOwner, previewView)
        isCameraReady = cameraManager.isCameraReady()
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.releaseCamera()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Position photo and tap capture",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Capture Button
            IconButton(
                onClick = {
                    if (!isCapturing && isCameraReady) {
                        isCapturing = true
                        MainScope().launch {
                            try {
                                val uri = cameraManager.capturePhoto()
                                if (uri != null) {
                                    Log.d("CameraScreen", "✓ Photo captured: $uri")
                                    onPhotoCaptured(uri)
                                    onDismiss()
                                } else {
                                    Log.e("CameraScreen", "Photo capture returned null")
                                    isCapturing = false
                                }
                            } catch (e: Exception) {
                                Log.e("CameraScreen", "Capture error", e)
                                isCapturing = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        if (isCameraReady && !isCapturing)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Capture photo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isCapturing) "Capturing..." else "Tap to capture",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
