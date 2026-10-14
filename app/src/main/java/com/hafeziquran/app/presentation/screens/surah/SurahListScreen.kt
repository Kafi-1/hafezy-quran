@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.surah

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.presentation.components.ListSearchField
import com.hafeziquran.app.presentation.components.NumberedListRow

/**
 * All 114 surahs, searchable, each row jumping to the surah's first page.
 */
@Composable
fun SurahListScreen(
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    viewModel: SurahListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সূরা সমূহ (${114.toBanglaDigits()})") },
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
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item {
                        ListSearchField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            placeholder = "সূরার নাম খুঁজুন…",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    items(state.results, key = { it.surah.number }) { row ->
                        NumberedListRow(
                            numberLabel = row.surah.number.toBanglaDigits(),
                            title = row.surah.bangla,
                            subtitle = "${row.surah.meaning} • ${row.surah.ayahs.toBanglaDigits()} আয়াত • ${row.surah.revelation}",
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
