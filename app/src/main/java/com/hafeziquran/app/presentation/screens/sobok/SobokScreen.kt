@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.sobok

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.hafeziquran.app.core.util.AlignmentResult
import com.hafeziquran.app.core.util.WordStatus
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.core.util.toReadableSize
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.Surah
import com.hafeziquran.app.presentation.theme.Gold
import com.hafeziquran.app.presentation.theme.LocalSpacing
import kotlin.math.roundToInt

/**
 * Sobok: pick a surah + ayah, recite, and get word-level feedback from the
 * offline Whisper model. If the optional model has not been downloaded yet this
 * screen first shows a one-time download gate.
 */
@Composable
fun SobokScreen(
    onBack: () -> Unit,
    viewModel: SobokViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasMicPermission(context)) }
    var permissionAsked by remember { mutableStateOf(false) }
    var startAfterGrant by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionAsked = true
        val shouldStart = granted && startAfterGrant
        startAfterGrant = false
        if (shouldStart) viewModel.toggleRecording()
    }

    val requestMicPermission = {
        startAfterGrant = true
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Re-check after returning from the system settings page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasMicPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সবক", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        when {
            !state.modelDownloaded -> ModelGateContent(
                state = state,
                onDownload = viewModel::startDownload,
                onCancel = viewModel::cancelDownload,
                onLater = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            state.contentLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> SobokContent(
                state = state,
                hasPermission = hasPermission,
                permissionAsked = permissionAsked,
                onRequestPermission = requestMicPermission,
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                onMicClick = {
                    if (hasPermission) viewModel.toggleRecording() else requestMicPermission()
                },
                onSelectSurah = viewModel::selectSurah,
                onPreviousAyah = viewModel::previousAyah,
                onNextAyah = viewModel::nextAyah,
                onRetry = viewModel::resetResult,
                onRedownload = viewModel::redownloadModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ModelGateContent(
    state: SobokUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        Text(
            text = "সবক",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "তেলাওয়াত যাচাই করুন — সম্পূর্ণ অফলাইনে",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(spacing.md)) {
                Text(
                    text = "কীভাবে কাজ করে",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                BulletLine("১. সূরা ও আয়াত নির্বাচন করুন")
                BulletLine("২. মাইক্রোফোনে আয়াতটি তেলাওয়াত করুন")
                BulletLine("৩. সঠিক শব্দ সবুজ, ভুল/বাদ পড়া শব্দ লাল দেখুন")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "সব প্রসেসিং আপনার ফোনেই হয় — কোনো অডিও কোথাও আপলোড হয় না।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ভয়েস মডেল (একবারই ডাউনলোড)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "প্রায় ৭৮ এমবি — ডাউনলোডের পর ইন্টারনেট ছাড়াই কাজ করবে",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (val download = state.download) {
            is DownloadStatus.InProgress -> DownloadProgress(
                download = download,
                onCancel = onCancel,
            )

            else -> DownloadActions(
                download = download,
                onDownload = onDownload,
                onLater = onLater,
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Gold),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DownloadProgress(
    download: DownloadStatus.InProgress,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val indeterminate = download.total <= 0L
        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } else {
            LinearProgressIndicator(
                progress = { download.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        val label = if (indeterminate) {
            "${download.bytes.toReadableSize()} ডাউনলোড হচ্ছে…"
        } else {
            "${download.bytes.toReadableSize()} / ${download.total.toReadableSize()}  " +
                "(${(download.fraction * 100).roundToInt().coerceIn(0, 100).toBanglaDigits()}%)"
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("বাতিল করুন")
        }
    }
}

@Composable
private fun DownloadActions(
    download: DownloadStatus,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (download is DownloadStatus.Failed) {
            Text(
                text = download.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (download is DownloadStatus.Failed || download is DownloadStatus.Cancelled) {
                    "আবার চেষ্টা করুন"
                } else {
                    "ডাউনলোড করুন"
                },
                fontWeight = FontWeight.Bold,
            )
        }

        TextButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("পরে")
        }
    }
}

@Composable
private fun SobokContent(
    state: SobokUiState,
    hasPermission: Boolean,
    permissionAsked: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onMicClick: () -> Unit,
    onSelectSurah: (Int) -> Unit,
    onPreviousAyah: () -> Unit,
    onNextAyah: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    var showSurahPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SelectorCard(
            surah = state.selectedSurah,
            selectedAyah = state.selectedAyah,
            totalAyahs = state.totalAyahs,
            enabled = state.phase != SobokPhase.RECORDING &&
                state.phase != SobokPhase.PROCESSING,
            onPickSurah = { showSurahPicker = true },
            onPreviousAyah = onPreviousAyah,
            onNextAyah = onNextAyah,
        )

        AyahCard(
            surah = state.selectedSurah,
            selectedAyah = state.selectedAyah,
            ayahText = state.ayahText,
            result = state.result,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!hasPermission) {
            PermissionCard(
                permissionAsked = permissionAsked,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
            )
        }

        MicSection(
            phase = state.phase,
            elapsedSeconds = state.elapsedSeconds,
            hasPermission = hasPermission,
            onMicClick = onMicClick,
        )

        state.errorMessage?.let { message ->
            ErrorCard(
                message = message,
                onRetry = onRetry,
                onRedownload = onRedownload,
            )
        }

        state.result?.let { result ->
            ResultSummary(
                result = result,
                canGoNext = state.canGoNext,
                onRetry = onRetry,
                onNext = onNextAyah,
            )
        }
    }

    if (showSurahPicker) {
        SurahPickerDialog(
            surahs = state.surahs,
            onDismiss = { showSurahPicker = false },
            onSelect = { number ->
                showSurahPicker = false
                onSelectSurah(number)
            },
        )
    }
}

@Composable
private fun SelectorCard(
    surah: Surah?,
    selectedAyah: Int,
    totalAyahs: Int,
    enabled: Boolean,
    onPickSurah: () -> Unit,
    onPreviousAyah: () -> Unit,
    onNextAyah: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                onClick = onPickSurah,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "সূরা",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = surah?.let { "${it.number.toBanglaDigits()}. ${it.bangla}" } ?: "নির্বাচন করুন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "সূরা পরিবর্তন করুন",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "আয়াত",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPreviousAyah, enabled = enabled && selectedAyah > 1) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "পূর্ববর্তী আয়াত")
                }
                Text(
                    text = "${selectedAyah.toBanglaDigits()} / " +
                        if (totalAyahs > 0) totalAyahs.toBanglaDigits() else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNextAyah, enabled = enabled && selectedAyah < totalAyahs) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "পরবর্তী আয়াত")
                }
            }
        }
    }
}

@Composable
private fun AyahCard(
    surah: Surah?,
    selectedAyah: Int,
    ayahText: String,
    result: AlignmentResult?,
    modifier: Modifier = Modifier,
) {
    val correctColor = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
    val text = ayahTextAnnotated(
        ayahText = ayahText,
        result = result,
        correctColor = correctColor,
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "আয়াত ${selectedAyah.toBanglaDigits()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                surah?.let {
                    Text(
                        text = it.bangla,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = text,
                style = TextStyle(
                    fontSize = 26.sp,
                    lineHeight = 52.sp,
                    textDirection = TextDirection.Rtl,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )

            if (result != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(correctColor)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "সঠিক",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    LegendDot(MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "ভুল / বাদ পড়েছে",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun ayahTextAnnotated(
    ayahText: String,
    result: AlignmentResult?,
    correctColor: Color,
): AnnotatedString {
    if (result == null) {
        return buildAnnotatedString { append(ayahText) }
    }
    return buildAnnotatedString {
        result.words.forEachIndexed { index, word ->
            if (index > 0) append(' ')
            val color = when (word.status) {
                WordStatus.CORRECT -> correctColor
                WordStatus.INCORRECT -> Color(0xFFD32F2F)
                WordStatus.NEUTRAL -> Color.Unspecified
            }
            withStyle(SpanStyle(color = color)) { append(word.text) }
        }
    }
}

@Composable
private fun PermissionCard(
    permissionAsked: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "মাইক্রোফোনের অনুমতি প্রয়োজন",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (permissionAsked) {
                    "অনুমতি ছাড়া তেলাওয়াত রেকর্ড করা যাবে না। অনুমতি দিন, অথবা সেটিংস থেকে ম্যানুয়ালি চালু করুন।"
                } else {
                    "তেলাওয়াত রেকর্ড করতে মাইক্রোফোনের অনুমতি দিতে হবে।"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRequestPermission) {
                    Text("অনুমতি দিন")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("সেটিংস")
                }
            }
        }
    }
}

@Composable
private fun MicSection(
    phase: SobokPhase,
    elapsedSeconds: Int,
    hasPermission: Boolean,
    onMicClick: () -> Unit,
) {
    val recording = phase == SobokPhase.RECORDING
    val processing = phase == SobokPhase.PROCESSING

    val status = when {
        !hasPermission -> "মাইক্রোফোনের অনুমতি দিন"
        recording -> "রেকর্ডিং হচ্ছে… থামাতে আবার চাপুন"
        processing -> "অফলাইনে প্রসেস হচ্ছে…"
        phase == SobokPhase.RESULT -> "ফলাফল দেখানো হয়েছে"
        phase == SobokPhase.ERROR -> "আবার চেষ্টা করুন"
        else -> "মাইক্রোফোন চাপুন, তারপর আয়াতটি তেলাওয়াত করুন"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (recording) {
                val transition = rememberInfiniteTransition(label = "mic-pulse")
                val pulse by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.28f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "mic-pulse-scale",
                )
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                )
            }

            Surface(
                onClick = onMicClick,
                enabled = !processing,
                shape = CircleShape,
                color = if (recording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shadowElevation = 6.dp,
                modifier = Modifier.size(92.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (recording) "রেকর্ডিং থামান" else "রেকর্ডিং শুরু করুন",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (recording || processing) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${elapsedSeconds.toBanglaDigits()} সেকেন্ড",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRetry) {
                    Text("আবার চেষ্টা করুন")
                }
                TextButton(onClick = onRedownload) {
                    Text("মডেল পুনরায় ডাউনলোড")
                }
            }
        }
    }
}

@Composable
private fun ResultSummary(
    result: AlignmentResult,
    canGoNext: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ফলাফল",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "${(result.accuracy * 100).roundToInt().toBanglaDigits()}% সঠিক",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "সঠিক শব্দ: ${result.correctWords.toBanglaDigits()} / " +
                    result.scoredWords.toBanglaDigits(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (result.extraWords.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "অতিরিক্ত শব্দ: ${result.extraWords.joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("আবার চেষ্টা")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("পরবর্তী আয়াত")
                }
            }
        }
    }
}

@Composable
private fun SurahPickerDialog(
    surahs: List<Surah>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সূরা নির্বাচন") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(surahs, key = { it.number }) { surah ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(surah.number) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${surah.number.toBanglaDigits()}. ${surah.bangla}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${surah.ayahs.toBanglaDigits()} আয়াত",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        },
    )
}

private fun hasMicPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
