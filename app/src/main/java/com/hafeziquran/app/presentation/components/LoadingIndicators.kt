package com.hafeziquran.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A soft "breathing" placeholder for content that is still loading.
 *
 * A translucent highlight sweeps across the surface diagonal, so a stack of
 * these read as one skeleton screen instead of a spinner. Purely decorative —
 * callers size it to the slot the real content will occupy.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "shimmerSweep",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val span = 900f
    val head = sweep * span

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(head - span / 2f, 0f),
                    end = Offset(head + span / 2f, span),
                ),
            ),
    )
}

/**
 * A rotating ring of eight dots that fade in sequence — a lightweight,
 * on-brand alternative to a plain circular spinner (no image assets, no
 * per-frame allocation beyond the draw calls).
 */
@Composable
fun IslamicLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 46.dp,
) {
    val transition = rememberInfiniteTransition(label = "islamicLoader")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "islamicLoaderAngle",
    )

    val dotCount = 8
    val dotRadius = size / 14f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val orbit = this.size.minDimension / 2f - dotRadius.toPx() * 1.5f
            for (i in 0 until dotCount) {
                val radians = Math.toRadians((angle + i * 360f / dotCount).toDouble())
                val center = Offset(
                    x = this.size.width / 2f + (orbit * cos(radians)).toFloat(),
                    y = this.size.height / 2f + (orbit * sin(radians)).toFloat(),
                )
                // Leading dot is brightest; the tail fades away.
                val alpha = 0.15f + 0.85f * (i.toFloat() / (dotCount - 1))
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = dotRadius.toPx(),
                    center = center,
                )
            }
        }
    }
}
