@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.presentation.components.ListSearchField
import com.hafeziquran.app.presentation.components.NumberedListRow

/**
 * The screen behind the bottom bar's centre button: a single field that matches
 * either a surah name or an exact page number.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সার্চ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListSearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "সূরার নাম বা পৃষ্ঠা নম্বর…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                state.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    state.pageJump?.let { page ->
                        item {
                            PageJumpCard(
                                page = page,
                                onClick = { onOpenPage(page) },
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 8.dp,
                                ),
                            )
                        }
                    }

                    if (state.surahResults.isEmpty() && state.pageJump == null) {
                        item {
                            Text(
                                text = if (state.query.isBlank()) {
                                    "সূরার নাম লিখুন বা একটি পৃষ্ঠা নম্বর দিন।"
                                } else {
                                    "কিছু পাওয়া যায়নি।"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 40.dp),
                            )
                        }
                    }

                    items(state.surahResults, key = { it.surah.number }) { row ->
                        NumberedListRow(
                            numberLabel = row.surah.number.toBanglaDigits(),
                            title = row.surah.bangla,
                            subtitle = "${row.surah.meaning} • ${row.surah.ayahs} আয়াত",
                            arabic = row.surah.arabic,
                            pageLabel = "পৃষ্ঠা ${row.page.toBanglaDigits()}",
                            onClick = { onOpenPage(row.page) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageJumpCard(
    page: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "পৃষ্ঠা ${page.toBanglaDigits()} এ যান",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
