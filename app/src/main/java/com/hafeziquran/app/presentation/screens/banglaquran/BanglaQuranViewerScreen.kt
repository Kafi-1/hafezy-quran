package com.hafeziquran.app.presentation.screens.banglaquran

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.core.util.toIntOrNullBangla
import com.hafeziquran.app.data.pdf.TocEntry
import com.hafeziquran.app.presentation.screens.reader.ZoomablePage
import com.hafeziquran.app.presentation.theme.ReaderNightVeil
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * In-app viewer for the downloaded Bangla translation.
 *
 * Left-to-right [HorizontalPager] (a translation reads like a normal book, not
 * the right-to-left mushaf), zoom/pan on every page, a night veil, a scrubber,
 * go-to-page, and — when the file carries an outline — a searchable surah jump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanglaQuranViewerScreen(
    onBack: () -> Unit,
    viewModel: BanglaQuranViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showGoToDialog by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val pageWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }

    KeepScreenOn()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isReady) {
                            "পৃষ্ঠা ${uiState.currentPage.toBanglaDigits()} / " +
                                uiState.pageCount.toBanglaDigits()
                        } else {
                            "বাংলা কোরআন"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setNightMode(!uiState.nightMode) }) {
                        Icon(
                            imageVector = if (uiState.nightMode) {
                                Icons.Outlined.LightMode
                            } else {
                                Icons.Outlined.DarkMode
                            },
                            contentDescription = "নাইট মোড",
                        )
                    }
                    IconButton(onClick = viewModel::toggleBookmark, enabled = uiState.isReady) {
                        Icon(
                            imageVector = if (uiState.isBookmarked) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = "বুকমার্ক",
                            tint = if (uiState.isBookmarked) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (uiState.toc.isNotEmpty()) {
                        IconButton(
                            onClick = { showTocDialog = true },
                            enabled = uiState.isReady,
                        ) {
                            Icon(Icons.Outlined.Toc, contentDescription = "সূচিপত্র")
                        }
                    }
                    IconButton(onClick = { showGoToDialog = true }, enabled = uiState.isReady) {
                        Icon(Icons.Outlined.Search, contentDescription = "পৃষ্ঠায় যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::retry,
                )
                uiState.isReady -> ViewerBody(
                    uiState = uiState,
                    pageWidthPx = pageWidthPx,
                    viewModel = viewModel,
                )
            }
        }
    }

    if (showGoToDialog && uiState.isReady) {
        GoToPageDialog(
            pageCount = uiState.pageCount,
            currentPage = uiState.currentPage,
            onDismiss = { showGoToDialog = false },
            onGo = { page ->
                showGoToDialog = false
                viewModel.requestJump(page)
            },
        )
    }

    if (showTocDialog && uiState.toc.isNotEmpty()) {
        TocDialog(
            entries = uiState.toc,
            onDismiss = { showTocDialog = false },
            onSelect = { page ->
                showTocDialog = false
                viewModel.requestJump(page)
            },
        )
    }
}

@Composable
private fun ViewerBody(
    uiState: BanglaQuranViewerUiState,
    pageWidthPx: Int,
    viewModel: BanglaQuranViewerViewModel,
) {
    var zoomedPage by remember { mutableStateOf(-1) }

    Box(Modifier.fillMaxSize()) {
        val pagerState = rememberPagerState(
            initialPage = (uiState.currentPage - 1)
                .coerceIn(0, (uiState.pageCount - 1).coerceAtLeast(0)),
            pageCount = { uiState.pageCount },
        )

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                zoomedPage = -1
                viewModel.onPageSettled(page + 1)
                viewModel.prefetchNeighbours(page + 1, pageWidthPx)
            }
        }

        LaunchedEffect(uiState.jumpRequest) {
            val target = uiState.jumpRequest ?: return@LaunchedEffect
            val index = (target - 1).coerceIn(0, uiState.pageCount - 1)
            if (abs(index - pagerState.currentPage) > 2) {
                pagerState.scrollToPage(index)
            } else {
                pagerState.animateScrollToPage(index)
            }
            viewModel.consumeJumpRequest()
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = zoomedPage != pagerState.settledPage,
            // A translation reads left-to-right, so pages run 1 → 2 → 3 naturally.
            reverseLayout = false,
            modifier = Modifier.fillMaxSize(),
            key = { it },
        ) { pageIndex ->
            PageContent(
                pageIndex = pageIndex,
                pageWidthPx = pageWidthPx,
                viewModel = viewModel,
                onZoomChange = { page, zoomed -> zoomedPage = if (zoomed) page else -1 },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (uiState.nightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ReaderNightVeil),
            )
        }

        PageScrubber(
            currentPage = uiState.currentPage,
            pageCount = uiState.pageCount,
            onSeek = viewModel::requestJump,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PageContent(
    pageIndex: Int,
    pageWidthPx: Int,
    viewModel: BanglaQuranViewerViewModel,
    onZoomChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var retryKey by remember(pageIndex) { mutableIntStateOf(0) }

    val page by produceState<Resource<Bitmap>?>(
        initialValue = null,
        pageIndex,
        pageWidthPx,
        retryKey,
    ) {
        value = viewModel.renderPage(pageIndex, pageWidthPx)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val result = page) {
            null, is Resource.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            is Resource.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = result.message ?: "পৃষ্ঠা লোড করা যায়নি",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { retryKey++ }) { Text("আবার চেষ্টা করুন") }
            }

            is Resource.Success -> ZoomablePage(
                bitmap = result.data,
                page = pageIndex,
                onZoomChange = onZoomChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "লোড হচ্ছে…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "বাংলা কোরআন খোলা যায়নি",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text("আবার চেষ্টা করুন") }
        }
    }
}

@Composable
private fun PageScrubber(
    currentPage: Int,
    pageCount: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    LaunchedEffect(currentPage) { sliderValue = currentPage.toFloat() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "পৃষ্ঠা ${currentPage.toBanglaDigits()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pageCount.toBanglaDigits(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                onValueChangeFinished = { onSeek(sliderValue.roundToInt()) },
            )
        }
    }
}

@Composable
private fun GoToPageDialog(
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentPage.toString()) }
    val parsed = text.toIntOrNullBangla()
    val isValid = parsed != null && parsed in 1..pageCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পৃষ্ঠায় যান") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                singleLine = true,
                isError = text.isNotEmpty() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("পৃষ্ঠা নম্বর (১ - ${pageCount.toBanglaDigits()})") },
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onGo) }, enabled = isValid) { Text("যান") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}

/**
 * Searchable outline ("সূচিপত্র"). A query matches a title substring *or* an exact
 * page number, so both "ইয়াসীন" and "৪৪৮" find their entry.
 */
@Composable
private fun TocDialog(
    entries: List<TocEntry>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val pageQuery = query.toIntOrNullBangla()

    val filtered = remember(query, entries) {
        if (query.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.title.contains(query, ignoreCase = true) ||
                    (pageQuery != null && entry.page == pageQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সূচিপত্র") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("সূরার নাম বা পৃষ্ঠা নম্বর") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = "কিছু পাওয়া যায়নি",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { "${it.title}-${it.page}" }) { entry ->
                                Surface(
                                    onClick = { onSelect(entry.page) },
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = entry.page.toBanglaDigits(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        },
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    LaunchedEffect(Unit) { view.keepScreenOn = true }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }
}
