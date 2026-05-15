package com.example.fieldsafesolar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fieldsafesolar.presentation.DownloadState
import com.example.fieldsafesolar.presentation.ModelDownloadViewModel
import com.example.fieldsafesolar.ui.theme.FieldSafeColors

@Composable
fun ModelDownloadScreen(
    onComplete: () -> Unit,
    onDemoMode: () -> Unit,
    viewModel: ModelDownloadViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (!viewModel.modelExists()) viewModel.startDownload()
    }

    LaunchedEffect(state) {
        if (state is DownloadState.Complete) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldSafeColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        ) {
            Text(
                text = "FIELDSAFE SOLAR",
                color = FieldSafeColors.Primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(40.dp))

            when (val s = state) {
                is DownloadState.Idle -> {
                    Text(
                        text = "Preparing download…",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }

                is DownloadState.Downloading -> {
                    val progress = if (s.totalBytes > 0) {
                        s.bytesDownloaded.toFloat() / s.totalBytes.toFloat()
                    } else null

                    Text(
                        text = "Setting up AI Model",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Gemma 4 E2B · First launch only",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = FieldSafeColors.Primary,
                            trackColor = Color.DarkGray
                        )
                        Spacer(Modifier.height(12.dp))
                        val dlGb = s.bytesDownloaded / 1_073_741_824.0
                        val totalGb = s.totalBytes / 1_073_741_824.0
                        val pct = (progress * 100).toInt()
                        Text(
                            text = "%.2f GB / %.2f GB  ($pct%%)".format(dlGb, totalGb),
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = FieldSafeColors.Primary,
                            trackColor = Color.DarkGray
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Connecting…",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Wi-Fi recommended · Only needed once",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                is DownloadState.Complete -> {
                    Text(
                        text = "AI Model Ready",
                        color = FieldSafeColors.Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                is DownloadState.Failed -> {
                    Text(
                        text = "Download Failed",
                        color = FieldSafeColors.Danger,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = s.reason,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.retry(); viewModel.startDownload() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.Primary)
                    ) {
                        Text("RETRY", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            if (state !is DownloadState.Complete) {
                Button(
                    onClick = {
                        viewModel.cancelDownload()
                        onDemoMode()
                    },
                    modifier = Modifier
                        .width(200.dp)
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.Warning)
                ) {
                    Text(
                        text = "DEMO MODE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Skip download · Uses simulated AI",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
