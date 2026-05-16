package com.applivity.fieldsafesolar.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors

/**
 * RealWearButton: Large, glove-friendly button for field use
 * Minimum 80dp height for touch with work gloves
 */
@Composable
fun RealWearButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) FieldSafeColors.Primary else FieldSafeColors.Secondary,
            contentColor = FieldSafeColors.OnPrimary,
            disabledContainerColor = FieldSafeColors.OutlineVariant,
            disabledContentColor = FieldSafeColors.OnSurface
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
