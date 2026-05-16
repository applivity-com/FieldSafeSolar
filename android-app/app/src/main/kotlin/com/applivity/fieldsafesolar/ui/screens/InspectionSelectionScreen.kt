package com.applivity.fieldsafesolar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.ui.components.RealWearButton
import com.applivity.fieldsafesolar.ui.navigation.Route

/**
 * InspectionSelectionScreen: Confirmation screen showing inspection type details
 * Allows user to start inspection or go back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionSelectionScreen(
    navController: NavController,
    inspectionTypeArg: String?
) {
    val inspectionType = try {
        inspectionTypeArg?.let { InspectionType.valueOf(it) }
    } catch (e: Exception) {
        null
    } ?: InspectionType.PPE_CHECK

    val (title, description, checklistItems) = when (inspectionType) {
        InspectionType.PPE_CHECK -> Triple(
            "PPE Check",
            "Verify personal protective equipment compliance before work begins.",
            listOf(
                "✓ Safety Gloves (electrical rated)",
                "✓ Safety Boots (non-conductive)",
                "✓ Eye Protection",
                "✓ Appropriate Clothing"
            )
        )

        InspectionType.INVERTER_PANEL_CHECK -> Triple(
            "Inverter/Panel Check",
            "Verify safe panel configuration and lockout procedures.",
            listOf(
                "✓ Panel Photo Documentation",
                "✓ Lockout Tag Present",
                "✓ DC Isolator Position",
                "✓ AC Breaker Status",
                "✓ Work Area Clear",
                "✓ Verbal Confirmations"
            )
        )

        InspectionType.WORK_AREA_CHECK -> Triple(
            "Work Area Check",
            "Verify work environment safety before proceeding.",
            listOf(
                "✓ Floor Condition",
                "✓ Obstruction Assessment",
                "✓ Moisture Hazards",
                "✓ Fall Hazards"
            )
        )

        InspectionType.SOLAR_COMMISSIONING -> Triple(
            "Solar Commissioning",
            "IEC 62446-1 pre-energization checks for solar PV systems.",
            listOf(
                "✓ Visual Inspection",
                "✓ String Polarity",
                "✓ Open-Circuit Voltage",
                "✓ Short-Circuit Current",
                "✓ Insulation Resistance",
                "✓ Anti-Islanding"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                // Description
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Checklist Preview
                Text(
                    text = "This inspection will verify:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                checklistItems.forEach { item ->
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Start Button
                RealWearButton(
                    label = "Start Inspection",
                    onClick = {
                        navController.navigate(
                            Route.VoiceInteraction.createRoute(inspectionType.name)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    isPrimary = true
                )
            }
        }
    }
}
