package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small "equalizer" style loading indicator — a handful of bars pulsing up
 * and down out of phase with one another. Used anywhere a bare spinner used
 * to sit (the ForYou loading grid, the lyrics loading state, etc).
 */
@Composable
fun PulsingBarsLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 5,
    barWidth: Dp = 6.dp,
    maxBarHeight: Dp = 28.dp,
) {
    val transition = rememberInfiniteTransition(label = "pulsing-bars")

    Row(
        modifier = modifier.height(maxBarHeight),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(barCount) { index ->
            val delay = index * (900 / barCount)
            val scale by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 700,
                        delayMillis = delay,
                        easing = EaseInOutSine,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar-$index",
            )
            if (index > 0) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxBarHeight * scale)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
