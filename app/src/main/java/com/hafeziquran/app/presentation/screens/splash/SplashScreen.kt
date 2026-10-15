package com.hafeziquran.app.presentation.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hafeziquran.app.R
import com.hafeziquran.app.presentation.theme.Gold
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Total on-screen time of the splash, in milliseconds. */
private const val HOLD_MS = 2200

/**
 * Animated launch screen: an eight-point Islamic star spins and scales into a
 * spinning gold ring while the app name fades in, then [onFinished] fires and the
 * graph swaps to Home.
 *
 * Drawn entirely with [Canvas] animations (`Animatable`s) so it needs no image
 * assets and stays cheap — the work is a handful of vector paths, not bitmaps.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val ringRotation = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
        launch { textAlpha.animateTo(1f, tween(700, delayMillis = 350)) }
        launch {
            ringRotation.animateTo(180f, tween(HOLD_MS, easing = LinearEasing))
        }
        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .scale(scale.value),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIslamicStar(
                        rotationDegrees = ringRotation.value,
                        strokeColor = Gold.copy(alpha = 0.55f),
                        strokeWidthPx = 3.dp.toPx(),
                    )
                }
                Canvas(modifier = Modifier.size(118.dp)) {
                    drawIslamicStar(
                        rotationDegrees = -ringRotation.value * 1.6f,
                        strokeColor = Gold,
                        strokeWidthPx = 5.dp.toPx(),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Gold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(width = 132.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = progress.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .background(Gold),
            )
        }
    }
}

/**
 * Strokes an eight-point star (two overlapping squares, the classic Islamic
 * motif) centred in the draw scope, rotated by [rotationDegrees].
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIslamicStar(
    rotationDegrees: Float,
    strokeColor: androidx.compose.ui.graphics.Color,
    strokeWidthPx: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = size.minDimension / 2f
    val inner = outer * 0.42f
    val points = 8
    val rotation = rotationDegrees * PI / 180.0

    val path = Path()
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (PI * i / points) - (PI / 2) + rotation
        val x = cx + (radius * cos(angle)).toFloat()
        val y = cy + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(path = path, color = strokeColor, style = Stroke(width = strokeWidthPx))
}
