package com.nextcloud.contacts.data.remote

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object RemoteUrlNormalizer {

    fun storageKey(server: String, href: String): String {
        val path = decodePath(davPath(href))
        return path.trimEnd('/')
    }

    fun requestUrl(server: String, storedOrHref: String): String {
        val path = if (storedOrHref.startsWith("http://") || storedOrHref.startsWith("https://")) {
            decodePath(davPath(storedOrHref))
        } else {
            decodePath(storedOrHref)
        }
        return server.trimEnd('/') + path
    }

    fun fileName(storedOrHref: String): String =
        decodePath(davPath(storedOrHref)).substringAfterLast('/')

    fun same(server: String, a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return storageKey(server, a) == storageKey(server, b)
    }

    private fun davPath(href: String): String {
        val trimmed = href.trim()
        val path = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> URI(trimmed).path.orEmpty()
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }
        return path.ifBlank { trimmed }
    }

    private fun decodePath(path: String): String =
        URLDecoder.decode(path, StandardCharsets.UTF_8)
}
