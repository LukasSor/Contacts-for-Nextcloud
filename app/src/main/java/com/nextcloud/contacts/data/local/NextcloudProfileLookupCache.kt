package com.nextcloud.contacts.data.local

import com.nextcloud.contacts.data.remote.CloudUser
import org.json.JSONObject

/** One sharees/OCS profile row cached locally for stale-while-revalidate UI. */
data class CachedNextcloudProfile(
    val cloudId: String,
    val displayName: String,
    val avatarUrl: String,
    val userId: String,
) {
    fun toCloudUser(): CloudUser = CloudUser(
        id = userId,
        displayName = displayName,
        cloudId = cloudId,
        avatarUrl = avatarUrl,
    )

    companion object {
        fun fromCloudUser(user: CloudUser): CachedNextcloudProfile = CachedNextcloudProfile(
            cloudId = user.cloudId.trim(),
            displayName = user.displayName.trim(),
            avatarUrl = user.avatarUrl.trim(),
            userId = user.id.trim(),
        )
    }
}

object NextcloudProfileLookupCache {
    fun parse(raw: String?): Map<String, CachedNextcloudProfile> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(trimmed)
            buildMap {
                root.keys().forEach { key ->
                    val entry = root.optJSONObject(key) ?: return@forEach
                    val cloudId = entry.optString("cloudId").trim().ifBlank { key.trim() }
                    val displayName = entry.optString("displayName").trim()
                    val avatarUrl = entry.optString("avatarUrl").trim()
                    val userId = entry.optString("userId").trim()
                    if (cloudId.isBlank() || displayName.isBlank()) return@forEach
                    val cacheKey = cloudId.lowercase()
                    put(
                        cacheKey,
                        CachedNextcloudProfile(
                            cloudId = cloudId,
                            displayName = displayName,
                            avatarUrl = avatarUrl,
                            userId = userId.ifBlank { cloudId.substringBefore('@') },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(entries: Map<String, CachedNextcloudProfile>): String {
        if (entries.isEmpty()) return ""
        val root = JSONObject()
        entries.values
            .sortedBy { it.cloudId.lowercase() }
            .forEach { profile ->
                val key = profile.cloudId.lowercase()
                root.put(
                    key,
                    JSONObject()
                        .put("cloudId", profile.cloudId)
                        .put("displayName", profile.displayName)
                        .put("avatarUrl", profile.avatarUrl)
                        .put("userId", profile.userId),
                )
            }
        return root.toString()
    }

    fun get(raw: String?, lookupCloudId: String?): CachedNextcloudProfile? {
        val key = lookupCloudId?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return parse(raw)[key]
    }

    fun withEntry(raw: String?, cloudId: String, profile: CachedNextcloudProfile): String {
        val key = cloudId.trim().lowercase()
        if (key.isBlank()) return raw.orEmpty()
        val merged = parse(raw).toMutableMap()
        merged[key] = profile
        return encode(merged)
    }

    fun withoutKey(raw: String?, cloudId: String): String {
        val key = cloudId.trim().lowercase()
        if (key.isBlank()) return raw.orEmpty()
        val merged = parse(raw).toMutableMap()
        merged.remove(key)
        return encode(merged)
    }

    fun merge(winnerRaw: String?, loserRaw: String?): String {
        val merged = parse(loserRaw).toMutableMap()
        merged.putAll(parse(winnerRaw))
        return encode(merged)
    }
}
