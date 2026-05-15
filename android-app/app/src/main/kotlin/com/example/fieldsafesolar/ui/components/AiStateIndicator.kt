package com.example.fieldsafesolar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ThinkingDotsAnimation(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dotphase",
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0..2) {
            val alpha = if (phase.toInt() == i) 1f else 0.3f
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color.copy(alpha = alpha), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun VoiceWaveAnimation(
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val normalizedAmp = (amplitude / 32767f).coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val barCount = 20
        val barWidth = size.width / (barCount * 2f)
        val maxBarHeight = size.height * 0.8f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (size.width / barCount) + barWidth
            val sineOffset = sin(phase + i * 0.5f)
            val barHeight = (maxBarHeight * (0.2f + 0.8f * normalizedAmp * ((sineOffset + 1f) / 2f)))
                .coerceAtLeast(4.dp.toPx())
            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
