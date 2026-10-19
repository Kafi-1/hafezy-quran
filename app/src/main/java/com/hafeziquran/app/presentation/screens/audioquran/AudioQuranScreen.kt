package com.hafeziquran.app.presentation.screens.audioquran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.Reciter
import com.hafeziquran.app.data.model.SurahInfo
import com.hafeziquran.app.presentation.theme.Gold

/**
 * Audio Quran: pick a reciter, search the 114 surahs by Bangla / Arabic / English
 * name or number, stream or download each one, and control playback. Playback
 * itself runs in the background service, so this screen can be closed without
 * stopping the recitation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQuranScreen(
    onBack: () -> Unit,
    viewModel: AudioQuranViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<SurahInfo?>(null) }
    var showFullPlayer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("অডিও কোরআন", style = MaterialTheme.typography.titleMedium) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ReciterSelector(
                    selected = state.reciter,
                    onSelect = viewModel::setReciter,
                )
    
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("সূরার নাম বা নম্বর খুঁজুন") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
    
                DownloadAllBar(
                    state = state,
                    onDownloadAll = viewModel::downloadAll,
                    onPauseAll = viewModel::pauseDownloadAll,
                    onCancelAll = viewModel::cancelDownloadAll,
                    onDeleteAll = {
                        viewModel.deleteAll()
                    },
                )
    
                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
    
                    state.error != null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
    
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.surahs, key = { it.number }) { surah ->
                            SurahRow(
                                surah = surah,
                                status = state.downloads[surah.number] ?: DownloadStatus.Idle,
                                isCurrent = state.nowPlaying?.number == surah.number,
                                isPlaying = state.isPlaying,
                                onPlay = {
                                    viewModel.play(surah.number)
                                    showFullPlayer = true
                                },
                                onDownload = { viewModel.download(surah.number) },
                                onPause = { viewModel.pauseDownload(surah.number) },
                                onCancel = { viewModel.cancelDownload(surah.number) },
                                onDelete = { deleteTarget = surah },
                            )
                        }
                    }
                }
    
                state.nowPlaying?.let { surah ->
                    MiniPlayer(
                        surah = surah,
                        reciter = state.reciter,
                        isPlaying = state.isPlaying,
                        buffering = state.buffering,
                        onToggle = viewModel::togglePlayPause,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onOpen = { showFullPlayer = true },
                    )
                }
            }
        }

        if (showFullPlayer) {
            state.nowPlaying?.let { surah ->
                NowPlayingScreen(
                    surah = surah,
                    reciter = state.reciter,
                    isPlaying = state.isPlaying,
                    buffering = state.buffering,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    speed = state.speed,
                    onSeek = viewModel::seekTo,
                    onSpeed = viewModel::setSpeed,
                    onToggle = viewModel::togglePlayPause,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onClose = { showFullPlayer = false },
                )
            }
        }
    }

    deleteTarget?.let { surah ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("ডাউনলোড মুছবেন?") },
            text = { Text("${surah.bangla} সূরার ডাউনলোড করা অডিওটি মুছে যাবে।") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(surah.number)
                        deleteTarget = null
                    },
                ) { Text("মুছুন") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("বাতিল") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReciterSelector(
    selected: Reciter,
    onSelect: (Reciter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Reciter.entries.toList(), key = { it.slug }) { reciter ->
            FilterChip(
                selected = reciter == selected,
                onClick = { onSelect(reciter) },
                label = { Text(reciter.banglaName) },
            )
        }
    }
}

@Composable
private fun DownloadAllBar(
    state: AudioQuranUiState,
    onDownloadAll: () -> Unit,
    onPauseAll: () -> Unit,
    onCancelAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val batch = state.batch
    if (batch.running) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)) {
            LinearProgressIndicator(
                progress = { batch.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${batch.done.toBanglaDigits()} / ${batch.total.toBanglaDigits()} ডাউনলোড হচ্ছে…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPauseAll) { Text("বিরতি") }
                TextButton(onClick = onCancelAll) { Text("বাতিল") }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ডাউনলোড: ${state.downloaded.size.toBanglaDigits()} / ${114.toBanglaDigits()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state.downloaded.size < 114) {
            TextButton(onClick = onDownloadAll) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("সব ডাউনলোড")
            }
        }
        if (state.downloaded.isNotEmpty()) {
            TextButton(onClick = onDeleteAll) { Text("সব মুছুন") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahRow(
    surah: SurahInfo,
    status: DownloadStatus,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val background = if (isCurrent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        color = background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = surah.number.toBanglaDigits(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.bangla,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${surah.arabic}  •  ${surah.ayahs.toBanglaDigits()} আয়াত",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isCurrent) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }

            DownloadControl(
                status = status,
                onDownload = onDownload,
                onPause = onPause,
                onCancel = onCancel,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun DownloadControl(
    status: DownloadStatus,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    when (status) {
        is DownloadStatus.InProgress -> IconButton(onClick = onPause) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { status.fraction },
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "ডাউনলোড বিরতি দিন",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        is DownloadStatus.Paused -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "ডাউনলোড চালিয়ে যান",
                tint = Gold,
            )
        }

        is DownloadStatus.Completed -> IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DownloadDone,
                contentDescription = "ডাউনলোড করা হয়েছে",
                tint = Gold,
            )
        }

        is DownloadStatus.Failed -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "আবার চেষ্টা করুন",
                tint = MaterialTheme.colorScheme.error,
            )
        }

        else -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "ডাউনলোড",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    surah: SurahInfo,
    reciter: Reciter,
    isPlaying: Boolean,
    buffering: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.bangla,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reciter.banglaName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "পূর্ববর্তী",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Button(
                onClick = onToggle,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (buffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "বিরতি" else "চালান",
                    )
                }
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "পরবর্তী",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Full-screen Now Playing. Drawn as an overlay above the list so it can own the
 * whole screen (including system-bar areas) without a navigation destination —
 * closing it simply reveals the mini player again.
 */
@Composable
private fun NowPlayingScreen(
    surah: SurahInfo,
    reciter: Reciter,
    isPlaying: Boolean,
    buffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primary, scheme.tertiary),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "ছোট করুন",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(
                    text = "এখন চলছে",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimary,
                )
                // Balances the close button so the title stays centred.
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(0.6f))

            // Avatar placeholder: a soft disc with the recitation glyph.
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = scheme.onPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(96.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = surah.bangla,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${surah.arabic}  •  ${surah.ayahs.toBanglaDigits()} আয়াত",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = reciter.banglaName,
                style = MaterialTheme.typography.labelLarge,
                color = Gold,
            )

            Spacer(Modifier.weight(1f))

            Slider(
                value = (dragPosition ?: positionMs.toFloat()).coerceIn(0f, duration.toFloat()),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    dragPosition?.let { onSeek(it.toLong()) }
                    dragPosition = null
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = scheme.onPrimary,
                    activeTrackColor = scheme.onPrimary,
                    inactiveTrackColor = scheme.onPrimary.copy(alpha = 0.30f),
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatPlaybackTime(dragPosition?.toLong() ?: positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimary.copy(alpha = 0.8f),
                )
                Text(
                    text = formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimary.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "পূর্ববর্তী",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }

                Spacer(Modifier.width(24.dp))

                Button(
                    onClick = onToggle,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.onPrimary,
                        contentColor = scheme.primary,
                    ),
                ) {
                    if (buffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = scheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "বিরতি" else "চালান",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Spacer(Modifier.width(24.dp))

                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "পরবর্তী",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEED_OPTIONS.forEach { option ->
                    val selected = kotlin.math.abs(speed - option) < 0.01f
                    Surface(
                        onClick = { onSpeed(option) },
                        shape = RoundedCornerShape(50),
                        color = scheme.onPrimary.copy(alpha = if (selected) 0.28f else 0.10f),
                    ) {
                        Text(
                            text = speedLabel(option),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = scheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val total = (ms / 1_000L).coerceAtLeast(0L)
    val minutes = (total / 60L).toInt()
    val seconds = (total % 60L).toInt()
    val mm = if (minutes < 10) "০${minutes.toBanglaDigits()}" else minutes.toBanglaDigits()
    val ss = if (seconds < 10) "০${seconds.toBanglaDigits()}" else seconds.toBanglaDigits()
    return "$mm:$ss"
}

private fun speedLabel(speed: Float): String = when {
    kotlin.math.abs(speed - 0.75f) < 0.01f -> "০.৭৫×"
    kotlin.math.abs(speed - 1.25f) < 0.01f -> "১.২৫×"
    kotlin.math.abs(speed - 1.5f) < 0.01f -> "১.৫×"
    kotlin.math.abs(speed - 2f) < 0.01f -> "২×"
    else -> "১×"
}

private val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
