package com.nextcloud.contacts.data

/**
 * Groups active contacts that share a phone, an email, or a full display name.
 * Opening the list does not merge anyone.
 */
object ContactDuplicates {
    enum class Match { PHONE, EMAIL, NAME }

    data class Group(
        val contactIds: List<Long>,
        val names: List<String>,
        val matches: Set<Match>,
    )

    data class MergeCandidate(
        val id: Long,
        val hasRemote: Boolean,
        val lastModified: Long,
    )

    fun find(
        names: Map<Long, String>,
        phonesByContact: Map<Long, List<String>>,
        emailsByContact: Map<Long, List<String>>,
    ): List<Group> {
        if (names.isEmpty()) return emptyList()
        val buckets = linkedMapOf<Set<Long>, MutableSet<Match>>()
        fun add(ids: Set<Long>, match: Match) {
            if (ids.size < 2) return
            buckets.getOrPut(ids) { linkedSetOf() }.add(match)
        }
        sharedKeys(names.keys, phonesByContact, ::phoneKey).forEach { add(it, Match.PHONE) }
        sharedKeys(names.keys, emailsByContact, ::emailKey).forEach { add(it, Match.EMAIL) }
        val byName = mutableMapOf<String, MutableSet<Long>>()
        for ((id, name) in names) {
            val key = normalizedDisplayName(name)
            if (key.isEmpty()) continue
            byName.getOrPut(key) { mutableSetOf() }.add(id)
        }
        for (ids in byName.values) add(ids, Match.NAME)
        return buckets.map { (ids, matches) ->
            val ordered = ids.sorted()
            Group(
                contactIds = ordered,
                names = ordered.map { names[it].orEmpty() },
                matches = matches,
            )
        }.sortedWith(compareBy({ it.contactIds.first() }, { it.contactIds.size }))
    }

    /**
     * One row per shared phone, email, or full name.
     * People who match in more than one way appear once in each matching block.
     */
    data class ValueGroup(
        val match: Match,
        val value: String,
        val contactIds: List<Long>,
        val names: List<String>,
    )

    fun findValues(
        names: Map<Long, String>,
        phonesByContact: Map<Long, List<String>>,
        emailsByContact: Map<Long, List<String>>,
    ): List<ValueGroup> {
        if (names.isEmpty()) return emptyList()
        val rows = mutableListOf<ValueGroup>()
        fun add(match: Match, value: String, ids: Set<Long>) {
            if (ids.size < 2) return
            val ordered = ids.sorted()
            rows += ValueGroup(
                match = match,
                value = value,
                contactIds = ordered,
                names = ordered.map { names[it].orEmpty() },
            )
        }
        for ((value, ids) in sharedValueBuckets(names.keys, phonesByContact, ::phoneKey)) {
            add(Match.PHONE, value, ids)
        }
        for ((value, ids) in sharedValueBuckets(names.keys, emailsByContact, ::emailKey)) {
            add(Match.EMAIL, value, ids)
        }
        val nameBuckets = linkedMapOf<String, ValueBucket>()
        for ((id, name) in names) {
            val key = normalizedDisplayName(name)
            if (key.isEmpty()) continue
            val trimmed = name.trim()
            val bucket = nameBuckets.getOrPut(key) { ValueBucket(mutableSetOf(), trimmed, id) }
            bucket.ids.add(id)
            if (id < bucket.displayOwner) {
                bucket.display = trimmed
                bucket.displayOwner = id
            }
        }
        for (bucket in nameBuckets.values) add(Match.NAME, bucket.display, bucket.ids)
        return rows.sortedWith(
            compareBy({ it.match.ordinal }, { it.contactIds.first() }, { it.value }),
        )
    }

    /** Server card wins, then the newer edit, then the lower id. */
    fun pickMergeKeeperId(candidates: List<MergeCandidate>): Long {
        require(candidates.isNotEmpty())
        return candidates.maxWith(
            compareBy<MergeCandidate> { it.hasRemote }
                .thenBy { it.lastModified }
                .thenBy { -it.id },
        ).id
    }

    private data class ValueBucket(
        val ids: MutableSet<Long>,
        var display: String,
        var displayOwner: Long,
    )

    private fun phoneKey(value: String): String =
        normalizedPhone(value).filter { it.isDigit() }

    private fun emailKey(value: String): String = normalizedEmail(value)

    private fun sharedKeys(
        active: Set<Long>,
        values: Map<Long, List<String>>,
        keyOf: (String) -> String,
    ): List<Set<Long>> {
        val buckets = mutableMapOf<String, MutableSet<Long>>()
        for ((id, list) in values) {
            if (id !in active) continue
            for (value in list) {
                val key = keyOf(value)
                if (key.isEmpty()) continue
                buckets.getOrPut(key) { mutableSetOf() }.add(id)
            }
        }
        return buckets.values.map { it.toSet() }
    }

    /** Same keys as [sharedKeys], plus one display string from the lowest contact id. */
    private fun sharedValueBuckets(
        active: Set<Long>,
        values: Map<Long, List<String>>,
        keyOf: (String) -> String,
    ): List<Pair<String, Set<Long>>> {
        val buckets = linkedMapOf<String, ValueBucket>()
        for ((id, list) in values) {
            if (id !in active) continue
            for (value in list) {
                val key = keyOf(value)
                if (key.isEmpty()) continue
                val trimmed = value.trim()
                val bucket = buckets.getOrPut(key) { ValueBucket(mutableSetOf(), trimmed, id) }
                bucket.ids.add(id)
                if (id < bucket.displayOwner) {
                    bucket.display = trimmed
                    bucket.displayOwner = id
                }
            }
        }
        return buckets.values.map { it.display to it.ids.toSet() }
    }
}
