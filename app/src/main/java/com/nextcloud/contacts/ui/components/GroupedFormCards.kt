package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import com.nextcloud.contacts.util.parseIsoOrBasicLocalDate
import com.nextcloud.contacts.ui.util.localizedFieldLabel
import com.nextcloud.contacts.ui.util.visibleLabel
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nextcloud.contacts.data.local.ContactFieldOptions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

object GroupedFormDefaults {
    val CornerOuter = 24.dp
    val CornerInner = 0.dp
    val SectionSpacing = 16.dp
    val RowPadding = 16.dp
}

fun groupedFormItemShape(index: Int, count: Int): Shape {
    val outer = GroupedFormDefaults.CornerOuter
    val inner = GroupedFormDefaults.CornerInner
    return when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
fun GroupedFormSection(
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    if (rows.isEmpty()) return
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = groupedFormItemShape(index, rows.size),
                color = cardColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    row()
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
                            thickness = 1.dp,
                            color = dividerColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormTextRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onRemove: (() -> Unit)? = null,
    valueFocus: FormValueFocus? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Row(
        modifier = modifier
            .formBringIntoView(valueFocus)
            .fillMaxWidth()
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .formValueFocusRequester(valueFocus),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.RemoveCircleOutline,
                contentDescription = stringResource(R.string.action_remove),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onRemove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDateRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueFocus: FormValueFocus? = null,
    freeFormInputs: Boolean = false,
) {
    if (freeFormInputs) {
        FormTextRow(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            icon = icon,
            modifier = modifier,
            valueFocus = valueFocus,
        )
        return
    }
    var showPicker by remember { mutableStateOf(false) }
    val displayValue = remember(value) { formatDateForDisplay(value) }

    Row(
        modifier = modifier
            .formBringIntoView(valueFocus)
            .formValueFocusRequester(valueFocus)
            .focusable()
            .fillMaxWidth()
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = displayValue.ifBlank { placeholder },
            style = MaterialTheme.typography.bodyLarge,
            color = if (displayValue.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f)
                .clickable { showPicker = true },
        )
        if (value.isNotBlank()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_clear_date),
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onValueChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LaunchedEffect(valueFocus?.requester) {
        if (valueFocus != null && value.isBlank()) {
            showPicker = true
        }
    }

    if (showPicker) {
        val initialMillis = remember(value) { parseDateToUtcMillis(value) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = 1900..2100,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onValueChange(formatUtcMillisToIsoDate(millis))
                        }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun parseDateToUtcMillis(value: String): Long? {
    val trimmed = value.trim()
    val date = parseIsoOrBasicLocalDate(trimmed) ?: return null
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun formatUtcMillisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

private fun formatDateForDisplay(value: String): String {
    val millis = parseDateToUtcMillis(value) ?: return value.trim()
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(date)
}

@Composable
fun FormTypedTextRow(
    type: String,
    onTypeChange: (String) -> Unit,
    typeOptions: List<ContactFieldOptions.Option>,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    typePickerTitle: String = stringResource(R.string.picker_select_type),
    /** When [typeOptions] is empty (e.g. Website), label for blank TYPE — matches web readableName. */
    noTypeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
    valueFocus: FormValueFocus? = null,
    freeFormInputs: Boolean = false,
    typePlaceholder: String = stringResource(R.string.label_type),
    valueSingleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var showPicker by remember { mutableStateOf(false) }
    val canPickType = typeOptions.isNotEmpty() && !freeFormInputs
    val typeLabel = when {
        !canPickType && noTypeLabel != null -> {
            if (type.isBlank()) noTypeLabel else localizedFieldLabel(typeOptions, type)
        }
        else -> localizedFieldLabel(typeOptions, type)
    }
    Row(
        modifier = modifier
            .formBringIntoView(valueFocus)
            .fillMaxWidth()
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (freeFormInputs) {
                BasicTextField(
                    value = type,
                    onValueChange = onTypeChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (type.isEmpty()) {
                            Text(
                                text = typePlaceholder,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            } else {
                Row(
                    modifier = if (canPickType) {
                        Modifier.clickable { showPicker = true }
                    } else {
                        Modifier
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (canPickType) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .formValueFocusRequester(valueFocus),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                keyboardOptions = keyboardOptions,
                singleLine = valueSingleLine,
                minLines = if (valueSingleLine) 1 else 2,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.RemoveCircleOutline,
                contentDescription = stringResource(R.string.action_remove),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onRemove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showPicker && canPickType) {
        FormOptionPickerDialog(
            title = typePickerTitle,
            options = typeOptions,
            selectedId = type,
            onSelect = {
                onTypeChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun FormSelectRow(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<ContactFieldOptions.Option>,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    pickerTitle: String = placeholder,
    onRemove: (() -> Unit)? = null,
    labelForValue: ((String) -> String)? = null,
    valueFocus: FormValueFocus? = null,
    freeFormInputs: Boolean = false,
) {
    if (freeFormInputs) {
        FormTextRow(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            icon = icon,
            modifier = modifier,
            onRemove = onRemove,
            valueFocus = valueFocus,
        )
        return
    }
    var showPicker by remember { mutableStateOf(false) }
    val displayLabel = when {
        value.isBlank() -> placeholder
        labelForValue != null -> labelForValue(value)
        else -> localizedFieldLabel(options, value)
    }
    Row(
        modifier = modifier
            .formBringIntoView(valueFocus)
            .fillMaxWidth()
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .formValueFocusRequester(valueFocus)
                .focusable()
                .clickable { showPicker = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.RemoveCircleOutline,
                contentDescription = stringResource(R.string.action_remove),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onRemove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LaunchedEffect(valueFocus?.requester) {
        if (valueFocus != null && value.isBlank()) {
            showPicker = true
        }
    }
    if (showPicker) {
        FormOptionPickerDialog(
            title = pickerTitle,
            options = options,
            selectedId = value,
            onSelect = {
                onValueChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun FormOptionPickerDialog(
    title: String,
    options: List<ContactFieldOptions.Option>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedOption = ContactFieldOptions.matchOption(options, selectedId)
    var query by remember { mutableStateOf("") }
    val showSearch = options.size >= 12
    val visibleLabels = options.associate { option -> option.id to option.visibleLabel() }
    val filtered = remember(options, query, visibleLabels) {
        val q = query.trim()
        if (q.isEmpty()) {
            options
        } else {
            options.filter { option ->
                option.id.isBlank() ||
                    option.label.contains(q, ignoreCase = true) ||
                    visibleLabels[option.id].orEmpty().contains(q, ignoreCase = true) ||
                    option.id.contains(q, ignoreCase = true)
            }
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = options.indexOfFirst { option ->
            selectedOption?.id.equals(option.id, ignoreCase = true) == true
        }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                if (showSearch) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.action_search),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable { query = "" }
                                        .padding(4.dp),
                                )
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (showSearch) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .padding(top = 8.dp),
                    ) {
                        if (filtered.isEmpty()) {
                            Text(
                                text = stringResource(R.string.empty_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 14.dp),
                            ) {
                                items(
                                    items = filtered,
                                    key = { "${it.id}|${it.label}" },
                                ) { option ->
                                    OptionPickerRow(
                                        option = option,
                                        selectedMatchedId = selectedOption?.id,
                                        onSelect = onSelect,
                                    )
                                }
                            }
                            LazyListScrollbar(
                                listState = listState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                                    .width(10.dp),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        filtered.forEach { option ->
                            OptionPickerRow(
                                option = option,
                                selectedMatchedId = selectedOption?.id,
                                onSelect = onSelect,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun OptionPickerRow(
    option: ContactFieldOptions.Option,
    selectedMatchedId: String?,
    onSelect: (String) -> Unit,
) {
    val isSelected = selectedMatchedId?.equals(option.id, ignoreCase = true) == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(option.id) }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.visibleLabel(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LazyListScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visible = layoutInfo.visibleItemsInfo
    if (totalItems <= 1 || visible.isEmpty()) return
    if (visible.size >= totalItems) return

    val scope = rememberCoroutineScope()
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    fun scrollToFraction(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * (totalItems - 1)).roundToInt()
        scope.launch { listState.scrollToItem(target) }
    }

    Canvas(
        modifier = modifier
            .pointerInput(totalItems) {
                detectTapGestures { offset ->
                    scrollToFraction(offset.y / size.height.coerceAtLeast(1))
                }
            }
            .pointerInput(totalItems) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    scrollToFraction(change.position.y / size.height.coerceAtLeast(1))
                }
            },
    ) {
        val trackHeight = size.height
        val visibleFraction = (visible.size.toFloat() / totalItems).coerceIn(0.08f, 1f)
        val thumbHeight = (trackHeight * visibleFraction).coerceIn(36.dp.toPx(), trackHeight)
        val maxIndex = (totalItems - visible.size).coerceAtLeast(1)
        val scrollFraction = (listState.firstVisibleItemIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
        val thumbTop = scrollFraction * (trackHeight - thumbHeight)

        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(x = size.width / 2f, y = size.width / 2f),
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(x = size.width / 2f, y = size.width / 2f),
        )
    }
}

@Composable
fun FormAddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
    }
}

@Composable
fun GroupedFormScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
fun GroupedFormSpacer() {
    Spacer(modifier = Modifier.padding(bottom = GroupedFormDefaults.SectionSpacing))
}
