@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.recitation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.normalizeArabicWord
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.core.util.toReadableSize
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.recitation.AyahCheckResult
import com.hafeziquran.app.data.recitation.PageAyah
import com.hafeziquran.app.data.recitation.RecitationMetrics
import com.hafeziquran.app.data.recitation.WordMatchStatus
import com.hafeziquran.app.presentation.theme.ArabicFontFamily
import com.hafeziquran.app.presentation.theme.Gold
import com.hafeziquran.app.presentation.theme.LocalSpacing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Full-page live recitation checker.
 *
 * The page text is always readable; ayah-level state only paints the background,
 * a small badge, and (after processing) the words themselves. Recording is never
 * paused or blocked by queue/Whisper work.
 */
@Composable
fun RecitationScreen(
    onBack: () -> Unit,
    viewModel: RecitationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasMicPermission(context)) }
    var startAfterGrant by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        val shouldStart = granted && startAfterGrant
        startAfterGrant = false
        if (shouldStart) viewModel.startListening()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.stopListening()
                Lifecycle.Event.ON_RESUME -> hasPermission = hasMicPermission(context)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestMic = {
        startAfterGrant = true
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showDebug = true })
                        },
                    ) {
                        Text("লাইভ সবক", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "পৃষ্ঠা ${state.page.toBanglaDigits()} / ${state.pageCount.toBanglaDigits()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::previousPage,
                        enabled = state.page > 1,
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "আগের পৃষ্ঠা")
                    }
                    IconButton(
                        onClick = viewModel::nextPage,
                        enabled = state.page < state.pageCount,
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "পরের পৃষ্ঠা")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (state.modelDownloaded && !state.contentLoading && state.ayahs.isNotEmpty()) {
                StartStopBar(
                    isListening = state.isListening,
                    pageFinished = state.pageFinished,
                    onToggle = {
                        if (state.isListening) {
                            viewModel.stopListening()
                        } else if (hasPermission) {
                            viewModel.startListening()
                        } else {
                            requestMic()
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !state.modelDownloaded -> ModelGate(
                    state = state,
                    onDownload = viewModel::startDownload,
                    onCancel = viewModel::cancelDownload,
                )

                state.contentLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.ayahs.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.errorMessage ?: "এই পৃষ্ঠার টেক্সট পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::retryLoadPage,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("আবার চেষ্টা করুন")
                    }
                }

                else -> RecitationBody(
                    state = state,
                    hasPermission = hasPermission,
                    onRequestPermission = requestMic,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onDismissError = viewModel::dismissError,
                )
            }

            if (showDebug) {
                DebugMetricsDialog(
                    metrics = state.metrics,
                    queueBacklogCount = state.queueBacklogCount,
                    isListening = state.isListening,
                    onDismiss = { showDebug = false },
                )
            }
        }
    }
}

@Composable
private fun DebugMetricsDialog(
    metrics: RecitationMetrics,
    queueBacklogCount: Int,
    isListening: Boolean,
    onDismiss: () -> Unit,
) {
    fun row(label: String, value: String) = "${label}: $value"

    val lines = listOf(
        row("segments", metrics.segmentCount.toBanglaDigits()),
        row("failed", metrics.failedCount.toBanglaDigits()),
        row("discarded", metrics.discardedCount.toBanglaDigits()),
        row("dropped", metrics.droppedCount.toBanglaDigits()),
        row("backlog", queueBacklogCount.toBanglaDigits()),
        row("mic", if (isListening) "চালু" else "বন্ধ"),
        row("model load", "${metrics.modelLoadMs.toInt().toBanglaDigits()} ms"),
        row("last queue wait", "${metrics.lastQueueWaitMs.toInt().toBanglaDigits()} ms"),
        row("last inference", "${metrics.lastInferenceMs.toInt().toBanglaDigits()} ms"),
        row("last total", "${metrics.lastTotalMs.toInt().toBanglaDigits()} ms"),
        row("last accuracy", "${(metrics.lastAccuracy * 100).toInt().toBanglaDigits()}%"),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        },
        title = { Text("সবক ডিবাগ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    )
}

@Composable
private fun RecitationBody(
    state: RecitationUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentAyahIndex, state.isListening) {
        if (state.isListening && state.currentAyahIndex in state.ayahs.indices) {
            listState.animateScrollToItem(state.currentAyahIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusHeader(state = state, onDismissError = onDismissError)

        if (!hasPermission) {
            PermissionBanner(
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
            )
        }

        legendRow()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = spacing.md,
                end = spacing.md,
                top = spacing.sm,
                bottom = spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            itemsIndexed(
                items = state.ayahs,
                key = { _, ayah -> "${ayah.surahNumber}:${ayah.ayahNumber}" },
            ) { index, ayah ->
                AyahBlock(
                    index = index,
                    ayah = ayah,
                    ui = state.ayahStates[index] ?: AyahUiState(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatusHeader(
    state: RecitationUiState,
    onDismissError: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val chip = statusAppearance(state)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = chip.container,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MicWave(active = state.isListening, tint = chip.content)
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = chip.content,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (state.queuedSegmentIds.isNotEmpty()) {
                Text(
                    text = "অপেক্ষায় ${state.queuedSegmentIds.size.toBanglaDigits()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.modelDownloaded && !state.modelReady) {
            Text(
                text = "মডেল প্রস্তুত হচ্ছে…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.pageFinished) {
            Text(
                text = "পৃষ্ঠাটি সম্পন্ন হয়েছে। আবার শুনতে চাইলে শুরু করুন।",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (state.metrics.droppedCount > 0) {
            Text(
                text = "কিউ পূর্ণ থাকায় ${state.metrics.droppedCount.toBanglaDigits()}টি অংশ বাদ পড়েছে",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissError) {
                        Text("ঠিক আছে", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private data class StatusAppearance(val label: String, val container: Color, val content: Color)

@Composable
private fun statusAppearance(state: RecitationUiState): StatusAppearance = when {
    state.isListening && state.queueBacklogCount > 0 -> StatusAppearance(
        label = "Listening… • প্রসেসিং",
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    state.isListening -> StatusAppearance(
        label = "Listening…",
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    state.processingSegmentIds.isNotEmpty() || state.queueBacklogCount > 0 -> StatusAppearance(
        label = "Processing…",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    else -> StatusAppearance(
        label = "Paused",
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MicWave(active: Boolean, tint: Color) {
    val transition = rememberInfiniteTransition(label = "micWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "micWavePhase",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val heightFraction = if (active) {
                0.3f + 0.7f * abs(sin((phase + index * 0.25f) * PI)).toFloat()
            } else {
                0.28f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14 * heightFraction).dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (active) 0.9f else 0.4f)),
            )
        }
    }
}

@Composable
private fun legendRow() {
    val colors = wordColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.md, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(colors.correct, "সঠিক")
        LegendDot(colors.wrong, "ভুল")
        LegendDot(colors.missing, "বাদ")
        LegendDot(colors.extra, "অতিরিক্ত")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AyahBlock(
    index: Int,
    ayah: PageAyah,
    ui: AyahUiState,
    modifier: Modifier = Modifier,
) {
    val listening = ui.state == AyahDisplayState.LISTENING

    val pulse = if (listening) {
        val transition = rememberInfiniteTransition(label = "ayahPulse")
        transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ayahPulseAlpha",
        ).value
    } else {
        0f
    }

    val restingColor = when (ui.state) {
        AyahDisplayState.QUEUED, AyahDisplayState.PROCESSING ->
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        AyahDisplayState.ERROR ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    val animatedResting by androidx.compose.animation.animateColorAsState(
        targetValue = restingColor,
        animationSpec = tween(350),
        label = "ayahContainer",
    )
    val container = if (listening) {
        lerp(animatedResting, MaterialTheme.colorScheme.primaryContainer, pulse)
    } else {
        animatedResting
    }

    Card(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (listening) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f + 0.25f * pulse),
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (listening) 0.dp else 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "সূরা ${ayah.surahNumber.toBanglaDigits()} • আয়াত " +
                        ayah.ayahNumber.toBanglaDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                AyahStatusBadge(ui)
            }

            Spacer(Modifier.height(10.dp))

            val result = ui.result
            if (result != null) {
                ResultAyahText(ayah = ayah, result = result)
            } else {
                PlainAyahText(ayah = ayah)
            }
        }
    }
}

@Composable
private fun AyahStatusBadge(ui: AyahUiState) {
    when (ui.state) {
        AyahDisplayState.QUEUED -> BadgeWithSpinner("অপেক্ষায়")
        AyahDisplayState.PROCESSING -> BadgeWithSpinner("প্রসেস হচ্ছে")
        AyahDisplayState.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "যাচাই করা যায়নি",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AyahDisplayState.RESULT_READY -> ui.result?.let { result ->
            Text(
                text = "✓ ${(result.accuracy * 100).toInt().toBanglaDigits()}%",
                style = MaterialTheme.typography.labelSmall,
                color = wordColors().correct,
            )
        }
        AyahDisplayState.LISTENING -> Text(
            text = "শুনছি…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        AyahDisplayState.IDLE -> Unit
    }
}

@Composable
private fun BadgeWithSpinner(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlainAyahText(ayah: PageAyah) {
    val marker = ayahMarker(ayah)
    val text = remember(ayah.text, marker) {
        buildAnnotatedString {
            append(ayah.text)
            append(' ')
            withStyle(SpanStyle(color = Gold)) { append(marker) }
        }
    }
    Text(
        text = text,
        style = arabicTextStyle(),
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultAyahText(ayah: PageAyah, result: AyahCheckResult) {
    val normal = MaterialTheme.colorScheme.onSurface
    val palette = wordColors()

    var animateIn by remember(result.segmentId) { mutableStateOf(0f) }
    LaunchedEffect(result.segmentId) { animateIn = 1f }
    val reveal by animateFloatAsState(
        targetValue = animateIn,
        animationSpec = tween(600),
        label = "wordReveal",
    )

    val marker = ayahMarker(ayah)
    val text = remember(result, reveal, normal, palette, marker) {
        buildAnnotatedString {
            result.words.forEachIndexed { index, word ->
                if (index > 0) append(' ')
                val shown = if (word.status == WordMatchStatus.EXTRA) {
                    word.spokenWord
                } else {
                    word.expectedWord
                }
                val decorative = word.expectedWord.isNotEmpty() &&
                    normalizeArabicWord(word.expectedWord).isEmpty()

                val target = when {
                    decorative || !word.covered -> normal
                    word.status == WordMatchStatus.CORRECT -> palette.correct
                    word.status == WordMatchStatus.WRONG -> palette.wrong
                    word.status == WordMatchStatus.MISSING -> palette.missing
                    word.status == WordMatchStatus.EXTRA -> palette.extra
                    else -> normal
                }
                withStyle(SpanStyle(color = lerp(normal, target, reveal))) {
                    append(shown)
                }
            }
            append(' ')
            withStyle(SpanStyle(color = Gold)) { append(marker) }
        }
    }

    Text(
        text = text,
        style = arabicTextStyle(),
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun arabicTextStyle(): TextStyle = TextStyle(
    fontFamily = ArabicFontFamily,
    fontSize = 24.sp,
    lineHeight = 48.sp,
    textDirection = TextDirection.Rtl,
    color = MaterialTheme.colorScheme.onSurface,
)

private fun ayahMarker(ayah: PageAyah): String = "﴿${ayah.ayahNumber.toBanglaDigits()}﴾"

private data class WordColors(
    val correct: Color,
    val wrong: Color,
    val missing: Color,
    val extra: Color,
)

@Composable
private fun wordColors(): WordColors {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        WordColors(
            correct = Color(0xFF81C784),
            wrong = Color(0xFFEF9A9A),
            missing = Color(0xFFB8A0A0),
            extra = Color(0xFFFFB74D),
        )
    } else {
        WordColors(
            correct = Color(0xFF2E7D32),
            wrong = Color(0xFFD32F2F),
            missing = Color(0xFFA67C7C),
            extra = Color(0xFFEF6C00),
        )
    }
}

@Composable
private fun StartStopBar(
    isListening: Boolean,
    pageFinished: Boolean,
    onToggle: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        isListening -> "রেকর্ডিং থামান"
                        pageFinished -> "আবার শুরু করুন"
                        else -> "শোনা শুরু করুন"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "এক আয়াত পড়ে সামান্য থামুন — বাকিটা ব্যাকগ্রাউন্ডে যাচাই হবে।",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionBanner(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.md, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "লাইভ যাচাইয়ের জন্য মাইক্রোফোন অনুমতি দরকার",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp)) {
                    Text("অনুমতি দিন")
                }
                OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                    Text("সেটিংস")
                }
            }
        }
    }
}

@Composable
private fun ModelGate(
    state: RecitationUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "লাইভ সবকের জন্য অফলাইন Whisper মডেল দরকার",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "একবার ডাউনলোড করলেই ইন্টারনেট ছাড়া আয়াত ধরে ধরে যাচাই করতে পারবেন। মডেলটি প্রায় ৭৫ MB।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        when (val download = state.download) {
            is DownloadStatus.InProgress -> {
                val fraction = download.fraction
                if (fraction > 0f) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${download.bytes.toReadableSize()} / ${download.total.toReadableSize()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) { Text("বাতিল করুন") }
            }

            is DownloadStatus.Paused -> {
                Text(
                    text = "ডাউনলোড বিরতিতে আছে",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDownload, shape = RoundedCornerShape(14.dp)) {
                    Text("আবার শুরু করুন")
                }
            }

            is DownloadStatus.Failed -> {
                Text(
                    text = download.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDownload, shape = RoundedCornerShape(14.dp)) {
                    Text("আবার চেষ্টা করুন")
                }
            }

            is DownloadStatus.Completed -> {
                Text(
                    text = "মডেল প্রস্তুত হচ্ছে…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            else -> Button(
                onClick = onDownload,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("মডেল ডাউনলোড করুন")
            }
        }
    }
}

private fun hasMicPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
