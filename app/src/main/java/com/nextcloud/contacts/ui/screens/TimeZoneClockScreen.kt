package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.local.TimeZoneOptions
import com.nextcloud.contacts.ui.components.FormAddRow
import com.nextcloud.contacts.ui.components.GroupedFormSection
import com.nextcloud.contacts.ui.components.modalBottomSheetScrollableContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZoneClockScreen(
    primaryZoneId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val comparisonZones by container.userPreferences.comparisonTimeZoneIds.collectAsState(
        initial = emptyList(),
    )
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    val visibleComparisons = remember(comparisonZones, primaryZoneId) {
        comparisonZones.filter { !zonesEquivalent(it, primaryZoneId) }
    }
    val systemZoneId = ZoneId.systemDefault().id
    val canAddSystemTime = !zonesEquivalent(systemZoneId, primaryZoneId) &&
        !comparisonZones.any { zonesEquivalent(it, systemZoneId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_time_zones)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimeZoneClockCard(
                zoneId = primaryZoneId,
                tick = tick,
                isPrimary = true,
                onRemove = null,
            )

            visibleComparisons.forEach { zoneId ->
                TimeZoneClockCard(
                    zoneId = zoneId,
                    tick = tick,
                    isPrimary = false,
                    onRemove = {
                        scope.launch {
                            container.userPreferences.removeComparisonTimeZone(zoneId)
                        }
                    },
                )
            }

            GroupedFormSection(
                rows = listOf(
                    {
                        FormAddRow(
                            label = stringResource(R.string.add_time),
                            onClick = { showPicker = true },
                        )
                    },
                    {
                        FormAddRow(
                            label = stringResource(R.string.add_system_time),
                            enabled = canAddSystemTime,
                            onClick = {
                                scope.launch {
                                    container.userPreferences.addComparisonTimeZone(systemZoneId)
                                }
                            },
                        )
                    },
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPicker) {
        TimeZonePickerSheet(
            primaryZoneId = primaryZoneId,
            existingComparisonZones = comparisonZones,
            tick = tick,
            onSelect = { zoneId ->
                showPicker = false
                scope.launch {
                    container.userPreferences.addComparisonTimeZone(zoneId)
                }
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun TimeZoneClockCard(
    zoneId: String,
    tick: Long,
    isPrimary: Boolean,
    onRemove: (() -> Unit)?,
) {
    val timeText = remember(zoneId, tick) { formatClockTimeWithSeconds(zoneId) }
    val label = remember(zoneId) { clockZoneLabel(zoneId) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                    ),
                    color = if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (isPrimary) {
                    Text(
                        text = stringResource(R.string.contact_time_zone),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_remove_time_zone),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZonePickerSheet(
    primaryZoneId: String,
    existingComparisonZones: List<String>,
    tick: Long,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val configuration = LocalConfiguration.current
    val maxListHeight = (configuration.screenHeightDp * 0.55f).dp
    var query by remember { mutableStateOf("") }

    val allZones = remember {
        TimeZoneOptions.selectable.map { opt -> opt.id to opt.label }
    }

    val filtered = remember(query, allZones, primaryZoneId, existingComparisonZones, tick) {
        val q = query.trim().lowercase(Locale.getDefault())
        allZones.filter { (id, label) ->
            !zonesEquivalent(primaryZoneId, id) &&
                !existingComparisonZones.any { zonesEquivalent(it, id) } &&
                (q.isEmpty() || id.lowercase(Locale.getDefault()).contains(q) ||
                    label.lowercase(Locale.getDefault()).contains(q))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.add_time_zone),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_time_zones)) },
                singleLine = true,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .modalBottomSheetScrollableContent(),
                overscrollEffect = null,
            ) {
                items(filtered, key = { it.first }) { (zoneId, label) ->
                    val time = formatClockTimeWithSeconds(zoneId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(zoneId) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = zoneId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = time,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

private fun clockZoneLabel(zoneId: String): String {
    val knownInOptions = TimeZoneOptions.selectable.any { zonesEquivalent(it.id, zoneId) }
    if (knownInOptions) {
        return TimeZoneOptions.displayLabel(zoneId)
    }
    val label = TimeZoneOptions.displayLabel(zoneId)
    return if (label != zoneId) label else zoneId.replace('_', ' ')
}

private fun zonesEquivalent(a: String, b: String): Boolean {
    if (a == b) return true
    return runCatching {
        ZoneId.of(a).id == ZoneId.of(b).id
    }.getOrDefault(false)
}

private fun formatClockTimeWithSeconds(zoneId: String): String {
    return try {
        val zone = ZoneId.of(zoneId)
        val now = ZonedDateTime.now(zone)
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(now)
    } catch (_: DateTimeException) {
        "—"
    } catch (_: Exception) {
        "—"
    }
}

