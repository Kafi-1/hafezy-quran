@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.books

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.core.util.toReadableSize
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.BookType
import com.hafeziquran.app.data.model.IslamicBook
import com.hafeziquran.app.presentation.components.TabScaffold
import com.hafeziquran.app.presentation.theme.Gold

/**
 * The "ইসলামিক বুক" tab. Each row is either a bundled book (opens directly) or a
 * downloadable one (a Download button that turns into a progress bar, then into
 * a tappable card). Failures surface both inline and as a snackbar.
 */
@Composable
fun IslamicBooksScreen(
    onOpenBook: (IslamicBook) -> Unit,
    viewModel: BooksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One snackbar per failure: remember which book last reported one so a
    // recomposition (or a second row failing) does not re-toast.
    var lastFailedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.rows) {
        val failed = state.rows.firstOrNull { it.download is DownloadStatus.Failed }
        if (failed == null) {
            lastFailedId = null
        } else if (failed.book.id != lastFailedId) {
            lastFailedId = failed.book.id
            snackbarHostState.showSnackbar((failed.download as DownloadStatus.Failed).message)
        }
    }

    TabScaffold(
        title = "ইসলামিক বুক",
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )

                state.errorMessage != null -> Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(state.rows, key = { it.book.id }) { row ->
                        BookCard(
                            row = row,
                            onClick = { onOpenBook(row.book) },
                            onDownload = { viewModel.download(row.book) },
                            onCancel = { viewModel.cancelDownload(row.book) },
                            onDelete = { viewModel.deleteDownload(row.book) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    row: BookRow,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val book = row.book
    Card(
        onClick = onClick,
        // Only a fully-present file opens; the inner Download button still works
        // because a disabled card does not consume its children's clicks.
        enabled = row.isOpenable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.author.isNotBlank()) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (book.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = book.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // A bundled book whose PDF is missing is the only "not yet" case.
                if (book.type == BookType.LOCAL && !row.isOpenable) {
                    Spacer(Modifier.width(8.dp))
                    SoonBadge()
                }
            }

            if (book.type == BookType.DOWNLOAD) {
                Spacer(Modifier.height(12.dp))
                DownloadSection(
                    row = row,
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun DownloadSection(
    row: BookRow,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    when (val download = row.download) {
        is DownloadStatus.InProgress, is DownloadStatus.Paused -> {
            val fraction = when (download) {
                is DownloadStatus.InProgress -> download.fraction
                is DownloadStatus.Paused -> download.fraction
                else -> 0f
            }
            val bytes = when (download) {
                is DownloadStatus.InProgress -> download.bytes
                is DownloadStatus.Paused -> download.bytes
                else -> 0L
            }
            val total = when (download) {
                is DownloadStatus.InProgress -> download.total
                is DownloadStatus.Paused -> download.total
                else -> 0L
            }
            DownloadProgressRow(
                fraction = fraction,
                bytes = bytes,
                total = total,
                onCancel = onCancel,
            )
        }

        else -> {
            if (row.isOpenable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadDone,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ডাউনলোড সম্পন্ন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDelete) {
                        Text("মুছুন", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                if (download is DownloadStatus.Failed) {
                    Text(
                        text = download.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (download is DownloadStatus.Cancelled) {
                    Text(
                        text = "ডাউনলোড বাতিল হয়েছে",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (download is DownloadStatus.Failed) {
                            "আবার চেষ্টা করুন"
                        } else {
                            "ডাউনলোড করুন"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressRow(
    fraction: Float,
    bytes: Long,
    total: Long,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val indeterminate = total <= 0L
        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (indeterminate) {
                    "${bytes.toReadableSize()} ডাউনলোড হচ্ছে..."
                } else {
                    "${bytes.toReadableSize()} / ${total.toReadableSize()}  " +
                        "(${fraction.times(100).toInt().toBanglaDigits()}%)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text("বাতিল করুন", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SoonBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "শীঘ্রই",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
