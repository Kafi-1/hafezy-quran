package com.hafeziquran.app.presentation.screens.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.preferences.ReadingMode
import com.hafeziquran.app.presentation.theme.ReaderNightVeil
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The mushaf reader.
 *
 * Horizontal mode uses an RTL [HorizontalPager] so dragging *right* advances the
 * page — exactly like a physical mushaf. Each page is independently zoomable;
 * while a page is magnified the pager's own swipe is disabled so panning works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showGoToDialog by remember { mutableStateOf(false) }

    // Render pages at the panel's true pixel width for a sharp, 1:1 scan.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val pageWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }

    // Never let the screen sleep on the mushaf page.
    KeepScreenOn(enabled = uiState.keepScreenOn)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isReady) {
                            "পৃষ্ঠা ${uiState.currentPage} / ${uiState.pageCount}"
                        } else {
                            "কোরআন শরীফ"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                        )
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
                    IconButton(
                        onClick = { viewModel.toggleBookmark() },
                        enabled = uiState.isReady,
                    ) {
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
                    IconButton(
                        onClick = { showGoToDialog = true },
                        enabled = uiState.isReady,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Numbers,
                            contentDescription = "পৃষ্ঠায় যান",
                        )
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
                uiState.isReady -> ReaderBody(
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
}

/**
 * Hosts the pager (or vertical list) plus the night veil and the page scrubber.
 */
@Composable
private fun ReaderBody(
    uiState: ReaderUiState,
    pageWidthPx: Int,
    viewModel: ReaderViewModel,
) {
    val scope = rememberCoroutineScope()

    // 0-based page currently magnified, or -1. Kept at screen level so the pager
    // knows to yield its swipe gesture to the zooming page.
    var zoomedPage by remember { mutableStateOf(-1) }

    Box(Modifier.fillMaxSize()) {
        if (uiState.readingMode == ReadingMode.HORIZONTAL) {
            val pagerState = rememberPagerState(
                initialPage = (uiState.currentPage - 1).coerceAtLeast(0),
                pageCount = { uiState.pageCount },
            )

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    zoomedPage = -1
                    viewModel.onPageSettled(page + 1)
                    viewModel.prefetchNeighbours(page + 1, pageWidthPx)
                }
            }

            // A "go to page" request arriving from the dialog.
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
                // Being magnified hands the gesture to the page, not the pager.
                userScrollEnabled = zoomedPage != pagerState.settledPage,
                // Mushaf order: page 2 lives to the *left* of page 1, so dragging
                // right advances forward.
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                key = { it },
            ) { pageIndex ->
                PageContent(
                    pageIndex = pageIndex,
                    pageWidthPx = pageWidthPx,
                    viewModel = viewModel,
                    enableZoom = true,
                    onZoomChange = { page, zoomed ->
                        zoomedPage = if (zoomed) page else -1
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (uiState.currentPage - 1).coerceAtLeast(0),
            )

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                    viewModel.onPageSettled(index + 1)
                    viewModel.prefetchNeighbours(index + 1, pageWidthPx)
                }
            }

            LaunchedEffect(uiState.jumpRequest) {
                val target = uiState.jumpRequest ?: return@LaunchedEffect
                listState.scrollToItem((target - 1).coerceIn(0, uiState.pageCount - 1))
                viewModel.consumeJumpRequest()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count = uiState.pageCount, key = { it }) { pageIndex ->
                    PageContent(
                        pageIndex = pageIndex,
                        pageWidthPx = pageWidthPx,
                        viewModel = viewModel,
                        enableZoom = false,
                        onZoomChange = { _, _ -> },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f / uiState.pageAspectRatio),
                    )
                }
            }
        }

        // Night veil sits *above* the paper but below the controls. It has no
        // pointerInput, so taps and swipes pass straight through to the page.
        if (uiState.nightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ReaderNightVeil),
            )
        }

        // Page scrubber — essential for a 612-page mushaf.
        PageScrubber(
            currentPage = uiState.currentPage,
            pageCount = uiState.pageCount,
            onSeek = { page ->
                viewModel.requestJump(page)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Renders one page. Decoding is keyed on the page index plus the target width,
 * so a rotation re-renders at the new width and a failed page can be retried by
 * bumping [retryKey] without leaving the screen.
 */
@Composable
private fun PageContent(
    pageIndex: Int,
    pageWidthPx: Int,
    viewModel: ReaderViewModel,
    enableZoom: Boolean,
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

            is Resource.Success -> if (enableZoom) {
                ZoomablePage(
                    bitmap = result.data,
                    page = pageIndex,
                    onZoomChange = onZoomChange,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                androidx.compose.foundation.Image(
                    bitmap = result.data.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                text = "কুরআন লোড করা যায়নি",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("আবার চেষ্টা করুন") }
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
    // Local, so dragging feels immediate; the pager only moves on release.
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
                    text = "পৃষ্ঠা $currentPage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$pageCount",
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
    val parsed = text.toIntOrNull()
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
                supportingText = { Text("পৃষ্ঠা নম্বর (১ - $pageCount)") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onGo) },
                enabled = isValid,
            ) { Text("যান") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}

/**
 * Ties the window flag to the preference. Cleared on dispose so leaving the
 * reader restores normal screen timeout.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(enabled) { view.keepScreenOn = enabled }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }
}
