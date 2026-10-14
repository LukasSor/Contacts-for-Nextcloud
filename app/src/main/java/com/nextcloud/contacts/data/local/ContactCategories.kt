package com.nextcloud.contacts.data.local

/** Parse / join Nextcloud contact CATEGORIES (comma-separated). */
object ContactCategories {
    fun parse(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun join(groups: List<String>): String =
        groups.map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")

    fun add(raw: String, group: String): String {
        val name = group.trim()
        if (name.isBlank()) return raw
        val current = parse(raw)
        if (current.any { it.equals(name, ignoreCase = true) }) return raw
        return join(current + name)
    }

    fun remove(raw: String, group: String): String {
        val name = group.trim()
        if (name.isBlank()) return raw
        return join(parse(raw).filterNot { it.equals(name, ignoreCase = true) })
    }

    fun distinctSorted(groups: Iterable<String>): List<String> {
        val seen = linkedMapOf<String, String>()
        for (raw in groups) {
            val name = raw.trim()
            if (name.isBlank()) continue
            val key = name.lowercase()
            if (key !in seen) seen[key] = name
        }
        return seen.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
