package com.applivity.fieldsafesolar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.ui.components.RealWearButton
import com.applivity.fieldsafesolar.ui.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF0A0A0A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚡",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "FieldSafe Solar",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "AI · Vision · Voice · Offline",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                IconButton(
                    onClick = { navController.navigate(Route.Settings.route) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("⚙", fontSize = 22.sp, color = Color.White.copy(alpha = 0.6f))
                }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "START INSPECTION",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                InspectionTypeButton(
                    icon = "🦺",
                    label = "PPE Check",
                    description = "Gloves · Hard hat · Eye protection · Hi-vis vest",
                    accentColor = Color(0xFF2E7D32),
                    onClick = {
                        navController.navigate(
                            Route.EvidenceCapture.createRoute(InspectionType.PPE_CHECK.name)
                        )
                    }
                )

                InspectionTypeButton(
                    icon = "⚡",
                    label = "Inverter / Panel Check",
                    description = "Lockout/tagout · De-energization · DC/AC isolation",
                    accentColor = Color(0xFF1565C0),
                    onClick = {
                        navController.navigate(
                            Route.EvidenceCapture.createRoute(InspectionType.INVERTER_PANEL_CHECK.name)
                        )
                    }
                )

                InspectionTypeButton(
                    icon = "🔍",
                    label = "Work Area Check",
                    description = "Hazards · Floor · Moisture · Fall risks",
                    accentColor = Color(0xFF6A1B9A),
                    onClick = {
                        navController.navigate(
                            Route.EvidenceCapture.createRoute(InspectionType.WORK_AREA_CHECK.name)
                        )
                    }
                )

                InspectionTypeButton(
                    icon = "☀️",
                    label = "Solar Commissioning",
                    description = "IEC 62446-1 · Voc · Isc · Insulation · Anti-islanding",
                    accentColor = Color(0xFFE65100),
                    onClick = {
                        navController.navigate(
                            Route.EvidenceCapture.createRoute(InspectionType.SOLAR_COMMISSIONING.name)
                        )
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // AI capability strip
                AiCapabilityStrip()

                RealWearButton(
                    label = "📋  View Past Reports",
                    onClick = { navController.navigate(Route.ReportList.route) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    isPrimary = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionTypeButton(
    icon: String,
    label: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(96.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 56.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Text(text = icon, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(text = "→", color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AiCapabilityStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CapabilityChip("👁", "Vision")
        CapabilityChip("🎤", "Voice")
        CapabilityChip("🧠", "Gemma 4")
        CapabilityChip("📴", "Offline")
    }
}

@Composable
private fun CapabilityChip(icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 20.sp)
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
