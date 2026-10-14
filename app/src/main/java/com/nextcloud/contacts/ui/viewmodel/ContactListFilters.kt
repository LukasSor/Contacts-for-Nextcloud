package com.nextcloud.contacts.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.ContactCategories
import com.nextcloud.contacts.data.local.ContactListItem
import com.nextcloud.contacts.util.formatContactDateLabel
import com.nextcloud.contacts.util.parseContactDateForCountdown
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class ContactListSortField {
    DISPLAY_NAME,
    FIRST_NAME,
    LAST_NAME,
    NICKNAME,
    ORGANIZATION,
    BIRTHDAY,
}

enum class ContactListSortDirection {
    ASCENDING,
    DESCENDING,
}

data class ContactListFilterState(
    val selectedGroups: Set<String> = emptySet(),
    val sortField: ContactListSortField = ContactListSortField.DISPLAY_NAME,
    val sortDirection: ContactListSortDirection = ContactListSortDirection.ASCENDING,
    val showFavoritesOnly: Boolean = false,
    val showHasPhone: Boolean = false,
    val showHasEmail: Boolean = false,
    val showHasNextcloudAccount: Boolean = false,
) {
    val isDefaultSort: Boolean
        get() = sortField == ContactListSortField.DISPLAY_NAME &&
            sortDirection == ContactListSortDirection.ASCENDING

    val hasActiveShowFilters: Boolean
        get() = showFavoritesOnly || showHasPhone || showHasEmail || showHasNextcloudAccount

    val hasActiveFilters: Boolean
        get() = selectedGroups.isNotEmpty() || !isDefaultSort || hasActiveShowFilters
}

@Composable
fun ContactListSortField.label(): String = when (this) {
    ContactListSortField.DISPLAY_NAME -> stringResource(R.string.sort_display_name)
    ContactListSortField.FIRST_NAME -> stringResource(R.string.sort_first_name)
    ContactListSortField.LAST_NAME -> stringResource(R.string.sort_last_name)
    ContactListSortField.NICKNAME -> stringResource(R.string.field_nickname)
    ContactListSortField.ORGANIZATION -> stringResource(R.string.field_organization)
    ContactListSortField.BIRTHDAY -> stringResource(R.string.field_birthday)
}

@Composable
fun ContactListSortDirection.label(): String = when (this) {
    ContactListSortDirection.ASCENDING -> stringResource(R.string.sort_ascending)
    ContactListSortDirection.DESCENDING -> stringResource(R.string.sort_descending)
}

@Composable
fun ContactListFilterState.sortChipLabel(): String =
    stringResource(R.string.sort_chip, sortField.label(), sortDirection.label())

fun applyContactListFilters(
    contacts: List<ContactListItem>,
    filters: ContactListFilterState,
): List<ContactListItem> {
    var filtered = contacts
    if (filters.showFavoritesOnly) {
        filtered = filtered.filter { it.isFavorite }
    }
    if (filters.showHasPhone) {
        filtered = filtered.filter { it.hasPhone }
    }
    if (filters.showHasEmail) {
        filtered = filtered.filter { it.hasEmail }
    }
    if (filters.showHasNextcloudAccount) {
        filtered = filtered.filter { it.hasNextcloudAccount }
    }
    filtered = if (filters.selectedGroups.isEmpty()) {
        filtered
    } else {
        filtered.filter { contact ->
            val groups = ContactCategories.parse(contact.categories)
            groups.any { group ->
                filters.selectedGroups.any { selected ->
                    selected.equals(group, ignoreCase = true)
                }
            }
        }
    }
    if (filters.isDefaultSort) {
        // Room already returns displayName ASC; preserve stable order for favorites grouping.
        return filtered
    }
    return filtered.sortedWith(contactListSortComparator(filters.sortField, filters.sortDirection))
}

fun sortKeyForField(contact: ContactListItem, field: ContactListSortField): String =
    when (field) {
        ContactListSortField.DISPLAY_NAME -> contact.displayName
        ContactListSortField.FIRST_NAME -> contact.givenName
        ContactListSortField.LAST_NAME -> contact.familyName
        ContactListSortField.NICKNAME -> contact.nickname
        ContactListSortField.ORGANIZATION -> contact.organization
        ContactListSortField.BIRTHDAY -> birthdaySectionLabel(contact.birthday)
    }.trim()

fun birthdaySectionLabel(birthday: String): String {
    val parsed = parseContactDateForCountdown(birthday) ?: return "#"
    return Month.of(parsed.month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
}

fun contactBirthdayRowLabel(birthday: String): String? {
    val parsed = parseContactDateForCountdown(birthday) ?: return null
    return formatContactDateLabel(parsed)
}

fun contactListSortComparator(
    field: ContactListSortField,
    direction: ContactListSortDirection,
): Comparator<ContactListItem> {
    if (field == ContactListSortField.BIRTHDAY) {
        return birthdaySortComparator(direction)
    }
    val nameOrder = String.CASE_INSENSITIVE_ORDER
    return Comparator { left, right ->
        val leftKey = sortKeyForField(left, field)
        val rightKey = sortKeyForField(right, field)
        val leftBlank = leftKey.isEmpty()
        val rightBlank = rightKey.isEmpty()
        when {
            leftBlank && rightBlank -> left.id.compareTo(right.id)
            leftBlank -> 1
            rightBlank -> -1
            else -> {
                val cmp = nameOrder.compare(leftKey, rightKey)
                val directed = if (direction == ContactListSortDirection.DESCENDING) -cmp else cmp
                if (directed != 0) directed else left.id.compareTo(right.id)
            }
        }
    }
}

private fun birthdaySortComparator(
    direction: ContactListSortDirection,
): Comparator<ContactListItem> {
    return Comparator { left, right ->
        val leftParsed = parseContactDateForCountdown(left.birthday)
        val rightParsed = parseContactDateForCountdown(right.birthday)
        when {
            leftParsed == null && rightParsed == null -> left.id.compareTo(right.id)
            leftParsed == null -> 1
            rightParsed == null -> -1
            else -> {
                var cmp = leftParsed.month.compareTo(rightParsed.month)
                if (cmp == 0) {
                    cmp = leftParsed.day.compareTo(rightParsed.day)
                }
                if (cmp == 0 && leftParsed.year != null && rightParsed.year != null) {
                    cmp = leftParsed.year.compareTo(rightParsed.year)
                }
                val directed = if (direction == ContactListSortDirection.DESCENDING) -cmp else cmp
                if (directed != 0) directed else left.id.compareTo(right.id)
            }
        }
    }
}
