package com.nextcloud.contacts.ui.list

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.nextcloud.contacts.data.local.ContactListItem
import com.nextcloud.contacts.ui.viewmodel.ContactListSortDirection
import com.nextcloud.contacts.ui.viewmodel.ContactListSortField
import com.nextcloud.contacts.ui.viewmodel.sortKeyForField
import com.nextcloud.contacts.util.AvatarBitmapCache

/** One lazy-list item per row or header, so a row can recycle on its own. */
@Immutable
sealed interface ContactListEntry {
    val key: String
    val contentType: String

    @Immutable
    data class FavoritesHeader(override val key: String = "header_favorites") : ContactListEntry {
        override val contentType: String = "favorites_header"
    }

    @Immutable
    data class LetterHeader(
        val letter: String,
        override val key: String = "header_$letter",
    ) : ContactListEntry {
        override val contentType: String = "letter_header"
    }

    /** Bitmap for the first screen. Later rows use [AvatarBitmapCache.peek]. */
    @Immutable
    data class ContactRow(
        val contact: ContactListItem,
        val indexInGroup: Int,
        val groupSize: Int,
        val sectionId: String,
        val thumbnail: ImageBitmap? = null,
        override val key: String = "contact_${contact.id}",
    ) : ContactListEntry {
        // Same content type for every row. Corner shape is cheap; a new composition is not.
        override val contentType: String = "contact_row"
    }
}

/** Room order, no section headers. First paint while grouping is still running. */
fun buildFlatPreviewContactListEntries(contacts: List<ContactListItem>): List<ContactListEntry> {
    return contacts.map { contact ->
        ContactListEntry.ContactRow(
            contact = contact,
            indexInGroup = 0,
            groupSize = 1,
            sectionId = "preview",
        )
    }
}

fun buildContactListEntries(
    contacts: List<ContactListItem>,
    sortField: ContactListSortField = ContactListSortField.DISPLAY_NAME,
    sortDirection: ContactListSortDirection = ContactListSortDirection.ASCENDING,
): List<ContactListEntry> {
    val useFavoritesSection = sortField == ContactListSortField.DISPLAY_NAME &&
        sortDirection == ContactListSortDirection.ASCENDING

    return if (useFavoritesSection) {
        buildDefaultDisplayNameEntries(contacts)
    } else {
        buildFlatSortedEntries(contacts, sortField, sortDirection)
    }
}

private fun buildDefaultDisplayNameEntries(contacts: List<ContactListItem>): List<ContactListEntry> {
    val favorites = contacts.filter { it.isFavorite }
    val nonFavorites = contacts.filter { !it.isFavorite }
    val groups = groupContactsByLetter(
        contacts = nonFavorites,
        letterFor = { contactLetter(it.displayName) },
        sortDirection = ContactListSortDirection.ASCENDING,
        sortWithinGroup = compareBy(String.CASE_INSENSITIVE_ORDER, ContactListItem::displayName),
    )

    return buildList {
        if (favorites.isNotEmpty()) {
            add(ContactListEntry.FavoritesHeader())
            favorites.forEachIndexed { index, contact ->
                add(
                    ContactListEntry.ContactRow(
                        contact = contact,
                        indexInGroup = index,
                        groupSize = favorites.size,
                        sectionId = "favorites",
                    ),
                )
            }
        }
        for (group in groups) {
            add(ContactListEntry.LetterHeader(group.letter))
            group.contacts.forEachIndexed { index, contact ->
                add(
                    ContactListEntry.ContactRow(
                        contact = contact,
                        indexInGroup = index,
                        groupSize = group.contacts.size,
                        sectionId = group.letter,
                    ),
                )
            }
        }
    }
}

private fun buildFlatSortedEntries(
    contacts: List<ContactListItem>,
    sortField: ContactListSortField,
    sortDirection: ContactListSortDirection,
): List<ContactListEntry> {
    // [applyContactListFilters] already sorted. Do not sort again inside a letter.
    val groups = groupContactsByLetterPreservingOrder(
        contacts = contacts,
        letterFor = { contactLetter(sortKeyForField(it, sortField)) },
        sortDirection = sortDirection,
    )

    return buildList {
        for (group in groups) {
            add(ContactListEntry.LetterHeader(group.letter))
            group.contacts.forEachIndexed { index, contact ->
                add(
                    ContactListEntry.ContactRow(
                        contact = contact,
                        indexInGroup = index,
                        groupSize = group.contacts.size,
                        sectionId = group.letter,
                    ),
                )
            }
        }
    }
}

/** Contact ids in visual list order (favorites → A → Z), for avatar prefetch. */
fun contactIdsInVisualOrder(entries: List<ContactListEntry>): List<Long> {
    return entries.mapNotNull { (it as? ContactListEntry.ContactRow)?.contact?.id }
}

/** Contact-row ordinal at or before each lazy index, or -1. Built once per list snapshot. */
fun lazyIndexToContactOrdinal(entries: List<ContactListEntry>): IntArray {
    val ordinals = IntArray(entries.size)
    var ordinal = -1
    for (i in entries.indices) {
        if (entries[i] is ContactListEntry.ContactRow) {
            ordinal++
        }
        ordinals[i] = ordinal
    }
    return ordinals
}

/** Copies cached thumbs onto the first [limit] rows. Does not decode. */
fun attachCachedThumbnails(
    entries: List<ContactListEntry>,
    sizePx: Int,
    limit: Int = Int.MAX_VALUE,
): List<ContactListEntry> {
    var remaining = limit
    return entries.map { entry ->
        if (entry !is ContactListEntry.ContactRow || remaining <= 0) {
            entry
        } else {
            remaining--
            val thumb = entry.thumbnail ?: AvatarBitmapCache.peek(entry.contact.id, sizePx)
            if (thumb != null && thumb !== entry.thumbnail) {
                entry.copy(thumbnail = thumb)
            } else {
                entry
            }
        }
    }
}

private data class ContactLetterGroup(
    val letter: String,
    val contacts: List<ContactListItem>,
)

private fun groupContactsByLetterPreservingOrder(
    contacts: List<ContactListItem>,
    letterFor: (ContactListItem) -> String,
    sortDirection: ContactListSortDirection,
): List<ContactLetterGroup> {
    val grouped = linkedMapOf<String, MutableList<ContactListItem>>()
    for (contact in contacts) {
        val letter = letterFor(contact)
        grouped.getOrPut(letter) { mutableListOf() }.add(contact)
    }
    val sectionKey: (String) -> String = { letter ->
        if (letter == "#") {
            if (sortDirection == ContactListSortDirection.DESCENDING) " " else "{"
        } else {
            letter
        }
    }
    val sections = grouped.map { (letter, groupContacts) ->
        ContactLetterGroup(letter = letter, contacts = groupContacts)
    }.sortedBy { sectionKey(it.letter) }
    return if (sortDirection == ContactListSortDirection.DESCENDING) sections.reversed() else sections
}

private fun groupContactsByLetter(
    contacts: List<ContactListItem>,
    letterFor: (ContactListItem) -> String,
    sortDirection: ContactListSortDirection,
    sortWithinGroup: Comparator<ContactListItem>,
): List<ContactLetterGroup> {
    val sectionKey: (String) -> String = { letter ->
        if (letter == "#") {
            if (sortDirection == ContactListSortDirection.DESCENDING) " " else "{"
        } else {
            letter
        }
    }
    val sections = contacts
        .groupBy(letterFor)
        .map { (letter, groupContacts) ->
            ContactLetterGroup(
                letter = letter,
                contacts = groupContacts.sortedWith(sortWithinGroup),
            )
        }
        .sortedBy { sectionKey(it.letter) }
    return if (sortDirection == ContactListSortDirection.DESCENDING) sections.reversed() else sections
}

private fun contactLetter(value: String): String {
    val first = value.trim().firstOrNull()?.uppercaseChar()
    return when {
        first == null -> "#"
        first.isLetter() -> first.toString()
        else -> "#"
    }
}
