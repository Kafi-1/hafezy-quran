package com.hafeziquran.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Bottom navigation bar with a raised centre "search" button.
 *
 * The centre button deliberately overflows the bar by [CENTER_FAB_OVERHANG] so it
 * reads as detached — the signature look of the original app. The bar therefore
 * owns a taller-than-visible layout box, and the two side sections are inset so
 * the middle slot stays empty for the button.
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onTabSelected: (TabRoute) -> Unit,
    onSearchClicked: () -> Unit,
) {
    val leftTabs = listOf(TabRoute.Home, TabRoute.Bookmarks)
    val rightTabs = listOf(TabRoute.Books, TabRoute.Tasbeeh)

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    // Keeps the bar above the system navigation bar while the
                    // surface colour still fills behind it.
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftTabs.forEach { tab ->
                    NavBarItem(
                        tab = tab,
                        selected = tab.route == currentRoute,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Reserved gap for the centre button.
                Box(modifier = Modifier.weight(1f))

                rightTabs.forEach { tab ->
                    NavBarItem(
                        tab = tab,
                        selected = tab.route == currentRoute,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Raised centre button. Drawn after the bar so it sits on top.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -CENTER_FAB_OVERHANG)
                .size(CENTER_FAB_SIZE)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSearchClicked,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "সার্চ",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun NavBarItem(
    tab: TabRoute,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint: Color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
        )
    }
}

/** The four tab destinations. Search is *not* a tab — it is the centre button. */
enum class TabRoute(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "হোম", Icons.Outlined.Home),
    Bookmarks("bookmarks", "বুকমার্ক", Icons.Outlined.BookmarkBorder),
    Books("islamic_books", "ইসলামিক বুক", Icons.Outlined.ImportContacts),
    Tasbeeh("tasbeeh", "তাসবীহ", Icons.Outlined.Spa),
}

private val BAR_HEIGHT = 62.dp
private val CENTER_FAB_SIZE = 58.dp
private val CENTER_FAB_OVERHANG = 26.dp
