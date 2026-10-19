@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.util.AppLinks
import com.hafeziquran.app.core.util.toReadableSize
import com.hafeziquran.app.data.auth.AuthUser
import com.hafeziquran.app.data.preferences.ThemeMode
import com.hafeziquran.app.presentation.theme.AppColorTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val sobokModel by viewModel.sobokModel.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDeleteModel by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সেটিংস") },
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionTitle("অ্যাকাউন্ট")
            AccountRow(
                user = user,
                onLogin = onOpenAuth,
                onLogout = viewModel::signOut,
            )

            SectionTitle("থিম")
            ThemeModeRow(
                selected = themeMode,
                onSelect = viewModel::setThemeMode,
            )

            SectionTitle("রঙের থিম")
            ColorThemeRow(
                selected = colorTheme,
                onSelect = viewModel::setColorTheme,
            )

            SectionTitle("সবক")
            SobokModelRow(
                downloaded = sobokModel.downloaded,
                sizeBytes = sobokModel.sizeBytes,
                onDelete = { confirmDeleteModel = true },
            )

            SectionTitle("তথ্য")
            SettingLinkRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "প্রাইভেসি পলিসি",
                onClick = onOpenPrivacy,
            )
            SettingLinkRow(
                icon = Icons.Outlined.Info,
                title = "অ্যাপ সম্পর্কে",
                onClick = onOpenAbout,
            )

            SectionTitle("অ্যাপ")
            SettingLinkRow(
                icon = Icons.Outlined.Share,
                title = "অ্যাপ শেয়ার করুন",
                onClick = { AppLinks.shareApp(context) },
            )
            SettingLinkRow(
                icon = Icons.Outlined.StarRate,
                title = "অ্যাপ রেট করুন",
                onClick = { AppLinks.rateApp(context) },
            )
            SettingLinkRow(
                icon = Icons.Outlined.Email,
                title = "ডেভেলপারের সাথে যোগাযোগ",
                onClick = { AppLinks.contactDeveloper(context) },
            )

            Text(
                text = "সংস্করণ ${viewModel.versionName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
        }

        if (confirmDeleteModel) {
            AlertDialog(
                onDismissRequest = { confirmDeleteModel = false },
                title = { Text("ভয়েস মডেল ডিলিট করবেন?") },
                text = {
                    Text(
                        "সবক ভয়েস মডেলটি ফোন থেকে মুছে ফেলা হবে। " +
                            "আবার ব্যবহার করতে হলে আবার ডাউনলোড করতে হবে।",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDeleteModel = false
                            viewModel.deleteSobokModel()
                        },
                    ) {
                        Text("ডিলিট করুন", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteModel = false }) {
                        Text("বাতিল")
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SobokModelRow(
    downloaded: Boolean,
    sizeBytes: Long,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ভয়েস মডেল",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (downloaded) {
                        "ডাউনলোড করা আছে (${sizeBytes.toReadableSize()})"
                    } else {
                        "ডাউনলোড করা নেই"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloaded) {
                TextButton(onClick = onDelete) {
                    Text("ডিলিট", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun AccountRow(
    user: AuthUser?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = user == null, onClick = onLogin)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.displayName?.takeIf { it.isNotBlank() }
                        ?: user?.email
                        ?: "লগইন করুন",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        user == null -> "ঐচ্ছিক — অতিথি হিসেবেও সব ফিচার চলবে"
                        user.displayName.isNullOrBlank() -> "লগইন করা আছে"
                        else -> user.email.orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (user == null) {
                TextButton(onClick = onLogin) { Text("লগইন") }
            } else {
                TextButton(onClick = onLogout) {
                    Text("লগআউট", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ThemeModeRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(mode.label()) },
            )
        }
    }
}

@Composable
private fun ColorThemeRow(
    selected: AppColorTheme,
    onSelect: (AppColorTheme) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppColorTheme.entries.forEach { theme ->
                val isSelected = theme == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(theme.primary, CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape,
                            )
                            .clickable { onSelect(theme) },
                    )
                }
            }
        }
        Text(
            text = selected.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SettingLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "সিস্টেম"
    ThemeMode.LIGHT -> "লাইট"
    ThemeMode.DARK -> "ডার্ক"
}
