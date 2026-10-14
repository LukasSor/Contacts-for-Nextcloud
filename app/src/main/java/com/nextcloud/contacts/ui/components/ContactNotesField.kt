package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactNotesFormatSegmentedControl(
    notesMarkdown: Boolean,
    onNotesMarkdownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        false to stringResource(R.string.notes_text),
        true to stringResource(R.string.notes_markdown),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (markdown, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                onClick = { onNotesMarkdownChange(markdown) },
                selected = notesMarkdown == markdown,
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun ContactNotesGroupedField(
    notes: String,
    onNotesChange: (String) -> Unit,
    notesMarkdown: Boolean,
    onNotesMarkdownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.field_note),
    minLines: Int = 3,
    freeFormInputs: Boolean = false,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GroupedFormDefaults.CornerOuter),
        color = cardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!freeFormInputs) {
                ContactNotesFormatSegmentedControl(
                    notesMarkdown = notesMarkdown,
                    onNotesMarkdownChange = onNotesMarkdownChange,
                    modifier = Modifier.padding(
                        horizontal = GroupedFormDefaults.RowPadding,
                        vertical = 12.dp,
                    ),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
                    thickness = 1.dp,
                    color = dividerColor,
                )
            }
            FormTextRow(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = placeholder,
                icon = Icons.AutoMirrored.Filled.Notes,
                singleLine = false,
                minLines = minLines,
            )
        }
    }
}

@Composable
fun ContactNotesMarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    if (markdown.isBlank()) return
    Markdown(
        markdown,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeText = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
            linkText = MaterialTheme.colorScheme.primary,
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.headlineMedium,
            h2 = MaterialTheme.typography.headlineSmall,
            h3 = MaterialTheme.typography.titleLarge,
            h4 = MaterialTheme.typography.titleMedium,
            h5 = MaterialTheme.typography.titleSmall,
            h6 = MaterialTheme.typography.titleSmall,
            text = MaterialTheme.typography.bodyLarge,
            code = MaterialTheme.typography.bodyMedium,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactNotesEditSheet(
    initialNotes: String,
    initialNotesMarkdown: Boolean,
    onDismiss: () -> Unit,
    onSave: (notes: String, notesMarkdown: Boolean) -> Unit,
) {
    var notes by remember(initialNotes, initialNotesMarkdown) { mutableStateOf(initialNotes) }
    var notesMarkdown by remember(initialNotes, initialNotesMarkdown) {
        mutableStateOf(initialNotesMarkdown)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    fun commitIfChanged() {
        if (notes != initialNotes || notesMarkdown != initialNotesMarkdown) {
            onSave(notes, notesMarkdown)
        } else {
            onDismiss()
        }
    }

    fun hideSheet(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) then()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { commitIfChanged() },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .modalBottomSheetScrollableContent()
                .padding(horizontal = 16.dp)
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.field_note),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ContactNotesGroupedField(
                notes = notes,
                onNotesChange = { notes = it },
                notesMarkdown = notesMarkdown,
                onNotesMarkdownChange = { notesMarkdown = it },
                minLines = 6,
            )
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(
                onClick = { hideSheet { commitIfChanged() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}
