package com.example.fieldsafesolar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.fieldsafesolar.data.model.EewpRecord
import com.example.fieldsafesolar.ui.theme.FieldSafeColors

@Composable
fun EewpPermitModal(
    onPermitIssued: (EewpRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    var taskDescription by remember { mutableStateOf("") }
    var supervisorName by remember { mutableStateOf("") }
    var incidentEnergy by remember { mutableStateOf("") }
    var workingDistance by remember { mutableStateOf("") }

    // Derive PPE category from incident energy
    val ppeCategory = remember(incidentEnergy) {
        val energy = incidentEnergy.toFloatOrNull()
        when {
            energy == null || energy <= 0f -> "—"
            energy < 4f -> "Category 1 (≥4 cal/cm²)"
            energy < 8f -> "Category 2 (≥8 cal/cm²)"
            energy < 25f -> "Category 3 (≥25 cal/cm²)"
            else -> "Category 4 (≥40 cal/cm²)"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(FieldSafeColors.Surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ENERGIZED ELECTRICAL\nWORK PERMIT",
                color = FieldSafeColors.Warning,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = "NFPA 70E §130.2(B) — Required before working within the Restricted Approach Boundary of energized conductors.",
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))

            EewpField(
                label = "Task Description",
                value = taskDescription,
                onValueChange = { taskDescription = it },
                placeholder = "Describe the energized work to be performed",
                singleLine = false,
            )
            EewpField(
                label = "Authorizing Supervisor",
                value = supervisorName,
                onValueChange = { supervisorName = it },
                placeholder = "Supervisor name",
            )
            EewpField(
                label = "Incident Energy at Working Distance (cal/cm²)",
                value = incidentEnergy,
                onValueChange = { incidentEnergy = it },
                placeholder = "e.g. 4.2",
                keyboardType = KeyboardType.Number,
            )

            if (ppeCategory != "—") {
                Text(
                    text = "Required PPE: $ppeCategory",
                    color = FieldSafeColors.Warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            EewpField(
                label = "Working Distance (ft)",
                value = workingDistance,
                onValueChange = { workingDistance = it },
                placeholder = "e.g. 18",
                keyboardType = KeyboardType.Number,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(60.dp),
                ) {
                    Text("SKIP", color = FieldSafeColors.OnSurfaceVariant, fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        onPermitIssued(
                            EewpRecord(
                                taskDescription = taskDescription.ifBlank { "Not specified" },
                                authorizingSupervisor = supervisorName.ifBlank { "Not specified" },
                                incidentEnergyCal = incidentEnergy.ifBlank { "Not measured" },
                                ppeCategory = ppeCategory,
                                workingDistanceFt = workingDistance.ifBlank { "Not specified" },
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FieldSafeColors.Warning),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("ISSUE PERMIT", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun EewpField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = FieldSafeColors.OnSurfaceVariant, fontSize = 11.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = FieldSafeColors.OnSurfaceVariant.copy(alpha = 0.5f)) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FieldSafeColors.Warning,
                unfocusedBorderColor = FieldSafeColors.SurfaceVariant,
                focusedTextColor = FieldSafeColors.OnSurface,
                unfocusedTextColor = FieldSafeColors.OnSurface,
                cursorColor = FieldSafeColors.Warning,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}
