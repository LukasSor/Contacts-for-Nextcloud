package com.nextcloud.contacts.data.local

import org.json.JSONObject
import java.net.URI

/** One website favicon row cached locally for stale-while-revalidate UI. */
data class CachedWebsiteLogo(
    val websiteUrl: String,
    val iconSourceUrl: String,
    val fileName: String,
    val etag: String = "",
    val lastModified: String = "",
)

object WebsiteLogoCache {
    fun normalizeWebsiteKey(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return runCatching {
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching withScheme.lowercase()
            if (scheme != "http" && scheme != "https") return@runCatching ""
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return@runCatching withScheme.lowercase()
            val port = uri.port
            val defaultPort = if (scheme == "https") 443 else 80
            val portPart = if (port > 0 && port != defaultPort) ":$port" else ""
            val path = uri.path.orEmpty().trimEnd('/')
            val pathPart = if (path.isBlank() || path == "/") "" else path
            "$scheme://$host$portPart$pathPart"
        }.getOrDefault(withScheme.lowercase())
    }

    fun parse(raw: String?): Map<String, CachedWebsiteLogo> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(trimmed)
            buildMap {
                root.keys().forEach { key ->
                    val entry = root.optJSONObject(key) ?: return@forEach
                    val websiteUrl = entry.optString("websiteUrl").trim().ifBlank { key.trim() }
                    val iconSourceUrl = entry.optString("iconSourceUrl").trim()
                    val fileName = entry.optString("fileName").trim()
                    if (websiteUrl.isBlank() || fileName.isBlank()) return@forEach
                    val cacheKey = normalizeWebsiteKey(websiteUrl).ifBlank { websiteUrl.lowercase() }
                    put(
                        cacheKey,
                        CachedWebsiteLogo(
                            websiteUrl = websiteUrl,
                            iconSourceUrl = iconSourceUrl,
                            fileName = fileName,
                            etag = entry.optString("etag").trim(),
                            lastModified = entry.optString("lastModified").trim(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(entries: Map<String, CachedWebsiteLogo>): String {
        if (entries.isEmpty()) return ""
        val root = JSONObject()
        entries.values
            .sortedBy { normalizeWebsiteKey(it.websiteUrl) }
            .forEach { logo ->
                val key = normalizeWebsiteKey(logo.websiteUrl).ifBlank { logo.websiteUrl.lowercase() }
                root.put(
                    key,
                    JSONObject()
                        .put("websiteUrl", logo.websiteUrl)
                        .put("iconSourceUrl", logo.iconSourceUrl)
                        .put("fileName", logo.fileName)
                        .put("etag", logo.etag)
                        .put("lastModified", logo.lastModified),
                )
            }
        return root.toString()
    }

    fun get(raw: String?, websiteUrl: String?): CachedWebsiteLogo? {
        val key = normalizeWebsiteKey(websiteUrl.orEmpty()).takeIf { it.isNotBlank() } ?: return null
        return parse(raw)[key]
    }

    fun withEntry(raw: String?, websiteUrl: String, logo: CachedWebsiteLogo): String {
        val key = normalizeWebsiteKey(websiteUrl).ifBlank { websiteUrl.trim().lowercase() }
        if (key.isBlank()) return raw.orEmpty()
        val merged = parse(raw).toMutableMap()
        merged[key] = logo
        return encode(merged)
    }

    fun merge(winnerRaw: String?, loserRaw: String?): String {
        val merged = parse(loserRaw).toMutableMap()
        merged.putAll(parse(winnerRaw))
        return encode(merged)
    }
}
