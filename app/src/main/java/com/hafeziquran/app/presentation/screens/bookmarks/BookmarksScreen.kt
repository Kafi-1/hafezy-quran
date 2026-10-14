package com.hafeziquran.app.presentation.screens.bookmarks

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.core.util.toReadableDate
import com.hafeziquran.app.data.local.entity.BookmarkEntity
import com.hafeziquran.app.presentation.components.TabScaffold

/**
 * The "বুকমার্ক" tab: every saved page as an editable, deletable row.
 *
 * Blank titles are filled in by the ViewModel ("পৃষ্ঠা N"), so a row can never
 * render without a name.
 */
@Composable
fun BookmarksScreen(
    onOpenPage: (Int) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    // Which dialog, if any, is open. Held in the screen — it is pure UI state.
    var editor by remember { mutableStateOf<BookmarkEditor?>(null) }
    var pendingDelete by remember { mutableStateOf<BookmarkEntity?>(null) }

    TabScaffold(
        title = "বুকমার্ক",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editor = BookmarkEditor.ForNew },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("যোগ করুন") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (bookmarks.isEmpty()) {
                EmptyBookmarks(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Text(
                            text = "${bookmarks.size.toBanglaDigits()}টি সংরক্ষিত পৃষ্ঠা",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            onClick = { onOpenPage(bookmark.pageNumber) },
                            onEdit = { editor = BookmarkEditor.ForExisting(bookmark) },
                            onDelete = { pendingDelete = bookmark },
                        )
                    }
                }
            }
        }
    }

    editor?.let { target ->
        BookmarkEditorDialog(
            target = target,
            onDismiss = { editor = null },
            onConfirm = { page, title ->
                when (target) {
                    is BookmarkEditor.ForNew -> viewModel.add(page, title)
                    is BookmarkEditor.ForExisting ->
                        viewModel.update(target.bookmark, page, title)
                }
                editor = null
            },
        )
    }

    pendingDelete?.let { bookmark ->
        DeleteBookmarkDialog(
            bookmark = bookmark,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(bookmark)
                pendingDelete = null
            },
        )
    }
}

/** What the add/edit dialog is currently operating on. */
private sealed interface BookmarkEditor {
    data object ForNew : BookmarkEditor
    data class ForExisting(val bookmark: BookmarkEntity) : BookmarkEditor
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(30.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "পৃষ্ঠা ${bookmark.pageNumber.toBanglaDigits()} • " +
                        bookmark.createdAt.toReadableDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "সম্পাদনা",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "মুছুন",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun EmptyBookmarks(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "এখনো কোনো বুকমার্ক নেই",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "পড়ার সময় উপরের বুকমার্ক আইকন চেপে পৃষ্ঠা সংরক্ষণ করুন, " +
                "অথবা নিচের বাটন দিয়ে যোগ করুন।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookmarkEditorDialog(
    target: BookmarkEditor,
    onDismiss: () -> Unit,
    onConfirm: (page: Int, title: String) -> Unit,
) {
    val existing = (target as? BookmarkEditor.ForExisting)?.bookmark
    var pageText by remember { mutableStateOf(existing?.pageNumber?.toString().orEmpty()) }
    var titleText by remember { mutableStateOf(existing?.title.orEmpty()) }

    val page = pageText.toIntOrNull()
    val pageValid = page != null && page > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) "নতুন বুকমার্ক" else "বুকমার্ক সম্পাদনা")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { input -> pageText = input.filter { it.isDigit() } },
                    label = { Text("পৃষ্ঠা নম্বর") },
                    singleLine = true,
                    isError = pageText.isNotEmpty() && !pageValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("নাম (ঐচ্ছিক)") },
                    placeholder = { Text("যেমন: শিক্ষামূলক আয়াত") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(page ?: return@TextButton, titleText) },
                enabled = pageValid,
            ) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}

@Composable
private fun DeleteBookmarkDialog(
    bookmark: BookmarkEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("বুকমার্ক মুছবেন?") },
        text = {
            Text("\"${bookmark.title}\" — পৃষ্ঠা ${bookmark.pageNumber.toBanglaDigits()} মুছে ফেলা হবে।")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("মুছুন", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}
