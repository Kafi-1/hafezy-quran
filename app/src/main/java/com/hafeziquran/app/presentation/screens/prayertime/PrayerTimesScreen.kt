@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.prayertime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.core.constants.BANGLADESH_DISTRICTS
import com.hafeziquran.app.core.constants.District
import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerName
import com.hafeziquran.app.domain.calendar.IslamicEvents
import com.hafeziquran.app.presentation.theme.Gold
import com.hafeziquran.app.presentation.theme.LocalSpacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prayer times, adhan controls, Hijri calendar and Ramadan sehri/iftar.
 *
 * First run shows a district dropdown + GPS button; the choice is persisted, so
 * later opens go straight to the times. Every adhan switch is scheduled as an
 * exact system alarm (see `AdhanScheduler`), and each prayer can use its own
 * audio file picked from storage.
 */
@Composable
fun PrayerTimesScreen(
    onBack: () -> Unit,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshExactAlarmPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ gates notifications behind a runtime grant; without it the
    // adhan service notification and sehri/iftar reminders are invisible.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Best effort: the app keeps working without the grant. */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var pendingAudioPrayer by remember { mutableStateOf<PrayerName?>(null) }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val prayer = pendingAudioPrayer
        pendingAudioPrayer = null
        if (uri != null && prayer != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setAudioUri(prayer, uri.toString())
        }
    }

    var gpsMessage by remember { mutableStateOf<String?>(null) }
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.useGps { ok ->
                gpsMessage = if (ok) null else "অবস্থান পাওয়া যায়নি, জেলা বেছে নিন"
            }
        } else {
            gpsMessage = "লোকেশন অনুমতি ছাড়া জিপিএস ব্যবহার সম্ভব নয়"
        }
    }

    val requestGps = {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            viewModel.useGps { ok ->
                gpsMessage = if (ok) null else "অবস্থান পাওয়া যায়নি, জেলা বেছে নিন"
            }
        } else {
            gpsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নামাজের সময়") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "রিফ্রেশ")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.location == null) {
            SetupScreen(
                gpsLoading = state.gpsLoading,
                gpsMessage = gpsMessage,
                onSelectDistrict = viewModel::selectDistrict,
                onUseGps = requestGps,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                LocationBar(
                    locationName = state.location?.name.orEmpty(),
                    onChangeLocation = viewModel::changeLocation,
                    onRefresh = viewModel::refresh,
                )
            }

            if (state.stale) {
                item { OfflineBanner() }
            }

            state.error?.let { message ->
                item { ErrorCard(message = message, onRetry = viewModel::refresh) }
            }

            item { TodayCard(state) }

            item {
                PrayerListCard(
                    state = state,
                    onToggleAdhan = viewModel::setAdhanEnabled,
                    onPickAudio = { prayer ->
                        pendingAudioPrayer = prayer
                        audioPicker.launch(arrayOf("audio/*"))
                    },
                )
            }

            item {
                RamadanCard(
                    state = state,
                    onToggleSehri = viewModel::setSehriAlarmEnabled,
                    onToggleIftar = viewModel::setIftarAlarmEnabled,
                )
            }

            item {
                HijriCalendarCard(
                    state = state,
                    onChangeMonth = viewModel::changeMonth,
                )
            }

            if (!state.canScheduleExact) {
                item {
                    ExactAlarmCard(
                        onOpenSettings = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            if (state.loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            item { Spacer(Modifier.height(spacing.sm)) }
        }
    }
}

@Composable
private fun SetupScreen(
    gpsLoading: Boolean,
    gpsMessage: String?,
    onSelectDistrict: (District) -> Unit,
    onUseGps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<District?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "অবস্থান নির্বাচন করুন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "সঠিক নামাজের সময়ের জন্য আপনার জেলা বেছে নিন বা জিপিএস " +
                        "ব্যবহার করুন। এই সেটিংস একবারই দিতে হবে — অ্যাপ নিজে " +
                        "থেকে সংরক্ষণ করবে।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selected?.let { "${it.banglaName} (${it.name})" }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("জেলা") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        BANGLADESH_DISTRICTS.forEach { district ->
                            DropdownMenuItem(
                                text = { Text("${district.banglaName} (${district.name})") },
                                onClick = {
                                    selected = district
                                    expanded = false
                                    onSelectDistrict(district)
                                },
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onUseGps,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !gpsLoading,
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(spacing.sm))
                    Text(if (gpsLoading) "অবস্থান খোঁজা হচ্ছে…" else "জিপিএস দিয়ে অবস্থান নিন")
                }

                gpsMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationBar(
    locationName: String,
    onChangeLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = locationName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "বাংলাদেশ · করাচি পদ্ধতি",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChangeLocation) { Text("পরিবর্তন") }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = "রিফ্রেশ")
        }
    }
}

@Composable
private fun TodayCard(state: PrayerTimesUiState) {
    val spacing = LocalSpacing.current
    val day = state.today
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val date = day?.date ?: LocalDate.now()
            Text(
                text = date.format(ENGLISH_DATE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            state.hijri?.let { hijri ->
                Text(
                    text = "${hijri.day} ${IslamicEvents.monthNameBangla(hijri.monthNumber)} " +
                        "${hijri.year} হিজরি",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${hijri.monthEnglish} · ${hijri.monthArabic}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IslamicEvents.forDate(hijri.monthNumber, hijri.day)?.let { event ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Gold.copy(alpha = 0.22f),
                    ) {
                        Text(
                            text = "${event.title} · ${event.arabicTitle}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerListCard(
    state: PrayerTimesUiState,
    onToggleAdhan: (PrayerName, Boolean) -> Unit,
    onPickAudio: (PrayerName) -> Unit,
) {
    val spacing = LocalSpacing.current
    val times: Map<PrayerName, LocalTime> = state.today?.times.orEmpty()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = spacing.sm)) {
            Text(
                text = "আজকের নামাজের সময়",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = 4.dp),
            )
            PrayerName.entries.forEach { prayer ->
                PrayerRow(
                    prayer = prayer,
                    time = times[prayer],
                    adhanEnabled = state.adhanEnabled[prayer] ?: true,
                    hasCustomAudio = state.audioUris[prayer] != null,
                    onToggleAdhan = { onToggleAdhan(prayer, it) },
                    onPickAudio = { onPickAudio(prayer) },
                )
            }
        }
    }
}

@Composable
private fun PrayerRow(
    prayer: PrayerName,
    time: LocalTime?,
    adhanEnabled: Boolean,
    hasCustomAudio: Boolean,
    onToggleAdhan: (Boolean) -> Unit,
    onPickAudio: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prayer.banglaName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (hasCustomAudio) "কাস্টম আজান সেট" else "ডিফল্ট আজান",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = time?.format(CLOCK)?.asBanglaDigits() ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onPickAudio) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = "${prayer.banglaName}র আজান অডিও",
                tint = if (hasCustomAudio) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Switch(checked = adhanEnabled, onCheckedChange = onToggleAdhan)
    }
}

@Composable
private fun RamadanCard(
    state: PrayerTimesUiState,
    onToggleSehri: (Boolean) -> Unit,
    onToggleIftar: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    val day = state.today
    val ramadanDays = state.monthDays.filter { it.isRamadan }.sortedBy { it.gregorian }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Gold.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (state.isRamadan) "রমজান সেহরি ও ইফতার" else "সেহরি ও ইফতার",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!state.isRamadan) {
                Text(
                    text = "আজ রমজান মাস নয়, তবে দৈনিক সেহরি ও মাগরিবের সময় নিচে দেওয়া হলো।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TimeCell(
                    label = "সেহরি শেষ",
                    time = day?.imsak,
                    modifier = Modifier.weight(1f),
                )
                TimeCell(
                    label = "ইফতার",
                    time = day?.maghrib,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            ReminderRow(
                label = "সেহরির রিমাইন্ডার",
                checked = state.sehriAlarmEnabled,
                onCheckedChange = onToggleSehri,
            )
            ReminderRow(
                label = "ইফতারের রিমাইন্ডার",
                checked = state.iftarAlarmEnabled,
                onCheckedChange = onToggleIftar,
            )

            if (ramadanDays.isNotEmpty()) {
                Text(
                    text = "রমজান মাসের সময়সূচি",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "তারিখ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.1f),
                    )
                    Text(
                        text = "সেহরি",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "ইফতার",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
                ramadanDays.forEach { entry -> RamadanScheduleRow(entry) }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RamadanScheduleRow(day: HijriCalendarDay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${day.hijriDay.toString().asBanglaDigits()} রমজান · " +
                day.gregorian.format(SHORT_DATE),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1.1f),
        )
        Text(
            text = day.imsak?.format(CLOCK)?.asBanglaDigits() ?: "—",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = day.maghrib?.format(CLOCK)?.asBanglaDigits() ?: "—",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TimeCell(label: String, time: LocalTime?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = time?.format(CLOCK)?.asBanglaDigits() ?: "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HijriCalendarCard(
    state: PrayerTimesUiState,
    onChangeMonth: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "হিজরি ক্যালেন্ডার",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChangeMonth(-1) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "আগের মাস")
                }
                Text(
                    text = state.displayedMonth.format(ENGLISH_MONTH),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChangeMonth(1) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "পরের মাস")
                }
            }

            if (state.monthLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (state.monthDays.isEmpty()) {
                Text(
                    text = "ক্যালেন্ডার ডেটা পাওয়া যায়নি। ইন্টারনেট সংযুক্ত হয়ে রিফ্রেশ করুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HijriMonthGrid(days = state.monthDays)

                val monthEvents = IslamicEvents.inMonth(state.monthDays)
                if (monthEvents.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "গুরুত্বপূর্ণ দিন",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                    )
                    monthEvents.forEach { (eventDay, event) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${event.arabicTitle} · " +
                                        "${eventDay.hijriDay.toString().asBanglaDigits()} " +
                                        IslamicEvents.monthNameBangla(
                                            eventDay.hijriMonthNumber,
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = eventDay.gregorian.format(SHORT_DATE),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HijriMonthGrid(days: List<HijriCalendarDay>) {
    val sorted = days.sortedBy { it.gregorian }
    val leading = sorted.firstOrNull()
        ?.let { (it.gregorian.dayOfWeek.value + 1) % 7 }
        ?: 0
    val cells: List<HijriCalendarDay?> = List(leading) { null } + sorted
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (cell != null) {
                            DayCell(
                                day = cell,
                                isToday = cell.gregorian == today,
                                isEvent = IslamicEvents.forDay(cell) != null,
                            )
                        }
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: HijriCalendarDay, isToday: Boolean, isEvent: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val highlight = when {
        isEvent -> Gold.copy(alpha = 0.24f)
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val numberColor = when {
        isEvent -> Gold
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .padding(1.dp)
            .fillMaxWidth()
            .background(color = highlight, shape = shape)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.hijriDay.toString().asBanglaDigits(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isEvent) FontWeight.Bold else FontWeight.Normal,
            color = numberColor,
        )
        Text(
            text = day.gregorian.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "অফলাইন — সংরক্ষিত সর্বশেষ সময় দেখানো হচ্ছে। ইন্টারনেট " +
                    "সংযুক্ত করে রিফ্রেশ করুন।",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRetry) { Text("আবার চেষ্টা করুন") }
        }
    }
}

@Composable
private fun ExactAlarmCard(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "সঠিক সময়ে আজানের জন্য এলার্ম ও রিমাইন্ডার অনুমতি দরকার।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onOpenSettings) { Text("অনুমতি দিন") }
        }
    }
}

/** Renders ASCII digits in Bengali numerals, e.g. "04:47" → "০৪:৪৭". */
private fun String.asBanglaDigits(): String = map { char ->
    if (char in '0'..'9') '০' + (char - '0') else char
}.joinToString("")

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val ENGLISH_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", Locale.ENGLISH)
private val ENGLISH_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Bangladesh's week starts on Saturday. */
private val WEEKDAY_LABELS = listOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র")