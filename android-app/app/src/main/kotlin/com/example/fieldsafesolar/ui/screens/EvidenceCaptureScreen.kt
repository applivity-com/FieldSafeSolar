package com.example.fieldsafesolar.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldsafesolar.data.model.InspectionType
import com.example.fieldsafesolar.ui.components.RealWearButton
import com.example.fieldsafesolar.ui.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceCaptureScreen(
    navController: NavController,
    inspectionTypeArg: String?
) {
    val inspectionType = try {
        inspectionTypeArg?.let { InspectionType.valueOf(it) }
    } catch (e: Exception) {
        null
    } ?: InspectionType.PPE_CHECK

    val checklist = when (inspectionType) {
        InspectionType.PPE_CHECK -> listOf(
            "Safety Gloves (electrical rated)",
            "Safety Boots (non-conductive)",
            "Eye Protection",
            "Appropriate Clothing"
        )
        InspectionType.INVERTER_PANEL_CHECK -> listOf(
            "Panel Photo Documentation",
            "Lockout Tag Present",
            "DC Isolator Position",
            "AC Breaker Status",
            "Work Area Clear",
            "Verbal Confirmations"
        )
        InspectionType.WORK_AREA_CHECK -> listOf(
            "Floor Condition",
            "Obstruction Assessment",
            "Moisture Hazards",
            "Fall Hazards"
        )
        InspectionType.SOLAR_COMMISSIONING -> listOf(
            "Visual Inspection",
            "String Polarity",
            "Open-Circuit Voltage",
            "Short-Circuit Current",
            "Insulation Resistance",
            "Anti-Islanding"
        )
    }

    // capturedUris maps checklist index → photo URI
    var capturedUris by remember { mutableStateOf<Map<Int, Uri>>(emptyMap()) }
    // Which item index is currently showing the camera overlay (null = camera hidden)
    var cameraItemIndex by remember { mutableStateOf<Int?>(null) }

    val progress = capturedUris.size.toFloat() / checklist.size

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Capture Evidence") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = "Evidence Progress: ${capturedUris.size}/${checklist.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn {
                        items(checklist.withIndex().toList()) { (index, item) ->
                            ChecklistItemCard(
                                title = item,
                                isCaptured = index in capturedUris,
                                onCaptureClick = { cameraItemIndex = index },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    RealWearButton(
                        label = "Continue to Voice Analysis",
                        onClick = {
                            navController.navigate(
                                Route.VoiceInteraction.createRoute(inspectionType.name)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        isPrimary = true,
                        enabled = capturedUris.size == checklist.size
                    )
                }
            }
        }

        // Camera overlay — shown fullscreen when a capture button is tapped
        cameraItemIndex?.let { itemIndex ->
            CameraScreen(
                title = "Capture: ${checklist[itemIndex]}",
                onPhotoCaptured = { uri ->
                    capturedUris = capturedUris + (itemIndex to uri)
                    cameraItemIndex = null
                },
                onDismiss = {
                    cameraItemIndex = null
                }
            )
        }
    }
}

@Composable
fun ChecklistItemCard(
    title: String,
    isCaptured: Boolean,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (isCaptured) {
                    Text(
                        text = "✓ Evidence captured",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (!isCaptured) {
                IconButton(onClick = onCaptureClick) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Capture photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .height(48.dp)
                            .width(48.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Captured",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(48.dp)
                        .width(48.dp)
                )
            }
        }
    }
}
