package com.hafeziquran.app.presentation.screens.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * A single mushaf page with pinch-zoom, pan and double-tap-to-zoom.
 *
 * Scale and translation are [Animatable]s so double-tap zooms *animate* while
 * finger-driven pinch stays 1:1. Every gesture writes through `snapTo`; the
 * Animatable's mutex guarantees the newest gesture wins and stale ones are
 * cancelled, which is what keeps fast pinching from fighting itself.
 *
 * @param onZoomChange reports whether this page is currently magnified, so the
 *   parent pager can disable its own swipe while the user is panning a zoomed
 *   page.
 */
@Composable
fun ZoomablePage(
    bitmap: Bitmap,
    page: Int,
    onZoomChange: (page: Int, zoomed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scale = remember(page) { Animatable(1f) }
    val translation = remember(page) { Animatable(Offset.Zero, Offset.VectorConverter) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Max translation that still keeps the (scaled) image covering the viewport.
    fun maxOffset(scaleValue: Float): Offset {
        if (containerSize == IntSize.Zero) return Offset.Zero
        val overhangX = ((scaleValue - 1f) * containerSize.width) / 2f
        val overhangY = ((scaleValue - 1f) * containerSize.height) / 2f
        return Offset(overhangX.coerceAtLeast(0f), overhangY.coerceAtLeast(0f))
    }

    // Only ever *report* a change. Adjacent pages compose while the settled page
    // is magnified, and an unconditional "false" from a freshly-composed
    // neighbour would wrongly re-enable the pager's swipe mid-zoom.
    var lastReportedZoom by remember(page) { mutableStateOf(false) }

    LaunchedEffect(scale.value) {
        val zoomed = scale.value > ZOOM_THRESHOLD
        if (zoomed != lastReportedZoom) {
            lastReportedZoom = zoomed
            onZoomChange(page, zoomed)
        }
    }
    DisposableEffect(page) {
        onDispose { if (lastReportedZoom) onZoomChange(page, false) }
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(page) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > ZOOM_THRESHOLD) {
                                launch { scale.animateTo(1f, spring()) }
                                translation.animateTo(Offset.Zero, spring())
                            } else {
                                val target = DOUBLE_TAP_SCALE
                                // Anchor the zoom on the tapped point, offset from
                                // the viewport centre, then clamp into bounds.
                                val rawX = (size.width / 2f - tap.x) * (target - 1f)
                                val rawY = (size.height / 2f - tap.y) * (target - 1f)
                                val bound = maxOffset(target)
                                launch { scale.animateTo(target, spring()) }
                                translation.animateTo(
                                    Offset(
                                        rawX.coerceIn(-bound.x, bound.x),
                                        rawY.coerceIn(-bound.y, bound.y),
                                    ),
                                    spring(),
                                )
                            }
                        }
                    },
                )
            }
            .transformable(
                state = rememberTransformableState { zoomChange, panChange, _ ->
                    scope.launch {
                        val newScale = (scale.value * zoomChange).coerceIn(1f, MAX_SCALE)
                        scale.snapTo(newScale)

                        val bound = maxOffset(newScale)
                        val newOffset = if (newScale <= ZOOM_THRESHOLD) {
                            Offset.Zero
                        } else {
                            Offset(
                                (translation.value.x + panChange.x).coerceIn(-bound.x, bound.x),
                                (translation.value.y + panChange.y).coerceIn(-bound.y, bound.y),
                            )
                        }
                        translation.snapTo(newOffset)
                    }
                },
            ),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = translation.value.x
                    translationY = translation.value.y
                },
        )
    }
}

private const val ZOOM_THRESHOLD = 1.05f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
