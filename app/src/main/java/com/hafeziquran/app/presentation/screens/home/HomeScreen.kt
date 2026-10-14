@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.R
import com.hafeziquran.app.core.util.AppLinks
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.presentation.components.AdBanner
import com.hafeziquran.app.presentation.theme.Gold
import com.hafeziquran.app.presentation.theme.LocalSpacing

/**
 * Landing screen: curved brand header, a continue-reading card with a live
 * thumbnail and progress, a strip of popular surahs, and a 2×3 service grid.
 */
@Composable
fun HomeScreen(
    onResumeReading: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onSurahList: () -> Unit,
    onParaList: () -> Unit,
    onPageSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onTasbeeh: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item { HomeHeader(onSettings = onSettings) }

            item {
                ContinueReadingCard(
                    state = state,
                    renderPreview = viewModel::renderPreview,
                    onClick = {
                        state.lastReadPage?.let(onOpenReader) ?: onResumeReading()
                    },
                    modifier = Modifier.padding(horizontal = spacing.md),
                )
            }

            item {
                SectionTitle(
                    text = "গুরুত্বপূর্ণ সূরা",
                    modifier = Modifier.padding(horizontal = spacing.md),
                )
            }

            item {
                FeaturedStrip(
                    featured = state.featured,
                    onOpenPage = onOpenReader,
                )
            }

            item {
                SectionTitle(
                    text = "সেবাসমূহ",
                    modifier = Modifier.padding(horizontal = spacing.md),
                )
            }

            item {
                FeatureGrid(
                    onSurahList = onSurahList,
                    onParaList = onParaList,
                    onPageSearch = onPageSearch,
                    onRecent = { state.lastReadPage?.let(onOpenReader) ?: onResumeReading() },
                    onBookmarks = onBookmarks,
                    onTasbeeh = onTasbeeh,
                    modifier = Modifier.padding(horizontal = spacing.md),
                )
            }

            item {
                Box(Modifier.padding(horizontal = spacing.md)) { AdBanner() }
            }
        }
    }
}

/**
 * Curved deep-green header. Draws behind the status bar (so the curve reads as
 * the top of the screen) and pads its own content below it.
 */
@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
                shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
            )
            .statusBarsPadding()
            .padding(horizontal = spacing.md, vertical = spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.width(spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "আসসালামু আলাইকুম",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gold,
                )
            }

            AdBadge()

            IconButton(onClick = { AppLinks.rateApp(context) }) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "রেটিং দিন",
                    tint = Gold,
                )
            }

            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "সেটিংস",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Small translucent "Ad" pill, as in the original header. */
@Composable
private fun AdBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Text(
            text = "Ad",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ContinueReadingCard(
    state: HomeUiState,
    renderPreview: suspend (Int, Int) -> Resource<android.graphics.Bitmap>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val targetPage = state.lastReadPage ?: 1

    val preview by produceState<Resource<android.graphics.Bitmap>?>(
        initialValue = null,
        key1 = targetPage,
    ) {
        value = renderPreview(targetPage, PREVIEW_WIDTH_PX)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewThumbnail(
                    preview = preview,
                    modifier = Modifier.width(78.dp),
                )

                Spacer(Modifier.width(spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.lastReadPage == null) "পড়া শুরু করুন" else "পড়া চালিয়ে যান",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoPill(
                            text = state.lastReadSurah?.let { "সূরা: ${it.bangla}" }
                                ?: "সূরা: আল-ফাতিহা",
                        )
                        InfoPill(
                            text = state.lastReadPara?.let { "পারা: ${it.number}" } ?: "পারা: ১",
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "পৃষ্ঠা $targetPage" +
                            if (state.totalPages > 0) " / ${state.totalPages}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(spacing.md))

            LinearProgressIndicator(
                progress = { state.readProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(spacing.md))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.lastReadPage == null) "পড়া শুরু করুন" else "পড়া চালিয়ে যান",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PreviewThumbnail(
    preview: Resource<android.graphics.Bitmap>?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (preview is Resource.Success) {
            Image(
                bitmap = preview.data.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Loading and failure share one quiet placeholder; the whole card
            // stays tappable so a decode failure never traps the user.
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
private fun FeaturedStrip(
    featured: List<FeaturedLink>,
    onOpenPage: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(featured, key = { it.label }) { link ->
            Surface(
                onClick = { onOpenPage(link.page) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = link.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "পৃষ্ঠা ${link.page}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureGrid(
    onSurahList: () -> Unit,
    onParaList: () -> Unit,
    onPageSearch: () -> Unit,
    onRecent: () -> Unit,
    onBookmarks: () -> Unit,
    onTasbeeh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        FeatureItem("সূরা সমূহ", "১১৪টি সূরা", Icons.Outlined.MenuBook, onSurahList),
        FeatureItem("পারা সমূহ", "৩০ পারা", Icons.Outlined.FormatListNumbered, onParaList),
        FeatureItem("পৃষ্ঠা সার্চ", "যেকোনো পৃষ্ঠায় যান", Icons.Outlined.Numbers, onPageSearch),
        FeatureItem("সর্বশেষ", "শেষ পড়া পৃষ্ঠা", Icons.Outlined.History, onRecent),
        FeatureItem("বুকমার্ক", "সংরক্ষিত পৃষ্ঠা", Icons.Outlined.BookmarkBorder, onBookmarks),
        FeatureItem("তাসবীহ", "ডিজিটাল কাউন্টার", Icons.Outlined.Spa, onTasbeeh),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { feature ->
                    FeatureCard(feature = feature, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: FeatureItem, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current

    Card(
        onClick = feature.onClick,
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class FeatureItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Wider than the on-screen thumbnail so it stays crisp on xxhdpi displays. */
private const val PREVIEW_WIDTH_PX = 420
