package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularDownloadProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(
        modifier = modifier.size(size),
        onDraw = {
            val diameter = size.toPx()
            val radius = diameter / 2
            val stroke = strokeWidth.toPx()
            val center = Offset(radius, radius)

            // Background circle
            drawArc(
                color = backgroundColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(diameter - stroke, diameter - stroke),
                topLeft = Offset(stroke / 2, stroke / 2)
            )

            // Progress arc
            if (progress > 0f) {
                val sweepAngle = 360f * progress
                drawArc(
                    color = progressColor,
                    startAngle = -90f, // Start from top
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = Size(diameter - stroke, diameter - stroke),
                    topLeft = Offset(stroke / 2, stroke / 2)
                )
            }
        }
    )
}

@Composable
fun AnimatedCircularDownloadProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "download-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse",
    )

    CircularDownloadProgress(
        progress = progress,
        modifier = modifier,
        size = size * pulseScale,
        strokeWidth = strokeWidth,
        backgroundColor = backgroundColor,
        progressColor = progressColor,
    )
}