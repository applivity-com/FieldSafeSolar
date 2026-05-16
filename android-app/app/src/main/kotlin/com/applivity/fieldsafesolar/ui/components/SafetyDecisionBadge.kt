package com.applivity.fieldsafesolar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applivity.fieldsafesolar.data.model.SafetyReport.Decision
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors

@Composable
fun SafetyDecisionBadge(
    decision: Decision,
    modifier: Modifier = Modifier
) {
    data class BadgeStyle(val bg: Color, val fg: Color, val icon: ImageVector, val label: String)

    val style = when (decision) {
        Decision.PASS      -> BadgeStyle(FieldSafeColors.SafeGreen, Color.White, Icons.Filled.Check, "PASS")
        Decision.WARN      -> BadgeStyle(FieldSafeColors.WarningAmber, Color.Black, Icons.Filled.Warning, "WARN")
        Decision.FAIL      -> BadgeStyle(FieldSafeColors.DangerRed, Color.White, Icons.Filled.Close, "FAIL")
        Decision.STOP_WORK -> BadgeStyle(FieldSafeColors.StopWorkRed, Color.White, Icons.Filled.Close, "STOP WORK")
    }

    Box(
        modifier = modifier
            .background(style.bg, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = style.icon,
                contentDescription = style.label,
                tint = style.fg,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "  ${style.label}",
                color = style.fg,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
