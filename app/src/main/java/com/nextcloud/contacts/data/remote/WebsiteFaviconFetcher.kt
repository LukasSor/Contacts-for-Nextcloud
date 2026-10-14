package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.auth.LoginFlowService
import com.nextcloud.contacts.data.local.WebsiteLogoCache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class WebsiteFaviconFetchResult {
    data object NotModified : WebsiteFaviconFetchResult()

    data class Updated(
        val bytes: ByteArray,
        val iconSourceUrl: String,
        val etag: String,
        val lastModified: String,
    ) : WebsiteFaviconFetchResult()

    data object Failed : WebsiteFaviconFetchResult()
}

class WebsiteFaviconFetcher(
    private val httpClient: OkHttpClient = createClient(),
) {
    fun fetch(
        websiteUrl: String,
        cachedIconSourceUrl: String?,
        etag: String?,
        lastModified: String?,
    ): WebsiteFaviconFetchResult {
        val pageUrl = resolvePageUrl(websiteUrl) ?: return WebsiteFaviconFetchResult.Failed
        if (!isAllowedScheme(pageUrl)) return WebsiteFaviconFetchResult.Failed
        val allowHttp = pageUrl.startsWith("http://", ignoreCase = true)

        val candidates = buildList {
            cachedIconSourceUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            baseOrigin(pageUrl)?.let { origin ->
                add("$origin/favicon.ico")
                add("$origin/apple-touch-icon.png")
                add("$origin/apple-touch-icon-precomposed.png")
            }
        }.distinct()

        for (candidate in candidates) {
            when (
                val result = fetchIconUrl(
                    candidate,
                    pageUrl,
                    allowHttp,
                    etag,
                    lastModified,
                    validateImage = true,
                )
            ) {
                is WebsiteFaviconFetchResult.Updated -> return result
                WebsiteFaviconFetchResult.NotModified -> return WebsiteFaviconFetchResult.NotModified
                WebsiteFaviconFetchResult.Failed -> Unit
            }
        }

        val htmlIcon = discoverIconFromHtml(pageUrl, allowHttp, etag, lastModified)
        if (htmlIcon != null) return htmlIcon

        return WebsiteFaviconFetchResult.Failed
    }

    private fun discoverIconFromHtml(
        pageUrl: String,
        allowHttp: Boolean,
        etag: String?,
        lastModified: String?,
    ): WebsiteFaviconFetchResult? {
        val request = Request.Builder()
            .url(pageUrl)
            .header("Accept", "text/html,*/*;q=0.8")
            .get()
            .build()
        val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return null
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val finalUrl = resp.request.url.toString()
            if (!isAllowedScheme(finalUrl, pageUrl, allowHttp)) return null
            val body = resp.body ?: return null
            val html = runCatching {
                body.source().use { source ->
                    val buffer = okio.Buffer()
                    var total = 0L
                    while (total < MAX_HTML_BYTES) {
                        val read = source.read(buffer, 8192)
                        if (read == -1L) break
                        total += read
                    }
                    buffer.readUtf8()
                }
            }.getOrNull() ?: return null
            val iconHref = parseIconHref(html) ?: return null
            val iconUrl = resolveAgainst(pageUrl, iconHref) ?: return null
            return fetchIconUrl(iconUrl, pageUrl, allowHttp, etag, lastModified, validateImage = true)
        }
    }

    private fun parseIconHref(html: String): String? {
        var best: String? = null
        var bestScore = Int.MIN_VALUE
        fun consider(rel: String, href: String, sizes: String) {
            if (!rel.contains("icon", ignoreCase = true)) return
            if (href.isBlank()) return
            var score = 0
            if (rel.contains("apple-touch-icon", ignoreCase = true)) score += 3
            if (rel.contains("shortcut", ignoreCase = true)) score += 1
            if (sizes.contains("192") || sizes.contains("180") || sizes.contains("128")) score += 2
            if (score >= bestScore) {
                bestScore = score
                best = href.trim()
            }
        }
        LINK_ICON_PATTERN.matcher(html).let { matcher ->
            while (matcher.find()) {
                consider(
                    rel = matcher.group(1).orEmpty(),
                    href = matcher.group(2).orEmpty(),
                    sizes = matcher.group(3).orEmpty(),
                )
            }
        }
        LINK_ICON_PATTERN_ALT.matcher(html).let { matcher ->
            while (matcher.find()) {
                consider(
                    rel = matcher.group(2).orEmpty(),
                    href = matcher.group(1).orEmpty(),
                    sizes = matcher.group(3).orEmpty(),
                )
            }
        }
        return best
    }

    private fun fetchIconUrl(
        url: String,
        pageUrl: String,
        allowHttp: Boolean,
        etag: String?,
        lastModified: String?,
        validateImage: Boolean,
    ): WebsiteFaviconFetchResult {
        if (!isAllowedScheme(url, pageUrl, allowHttp)) return WebsiteFaviconFetchResult.Failed
        val builder = Request.Builder().url(url).get()
        if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
        if (!lastModified.isNullOrBlank()) builder.header("If-Modified-Since", lastModified)
        val response = runCatching { httpClient.newCall(builder.build()).execute() }.getOrNull()
            ?: return WebsiteFaviconFetchResult.Failed
        response.use { resp ->
            val finalUrl = resp.request.url.toString()
            if (!isAllowedScheme(finalUrl, pageUrl, allowHttp)) return WebsiteFaviconFetchResult.Failed
            if (resp.code == 304) return WebsiteFaviconFetchResult.NotModified
            if (!resp.isSuccessful) return WebsiteFaviconFetchResult.Failed
            val body = resp.body ?: return WebsiteFaviconFetchResult.Failed
            val contentType = body.contentType()?.toString().orEmpty().lowercase()
            if (validateImage && contentType.isNotBlank() &&
                !contentType.startsWith("image/") &&
                !contentType.contains("octet-stream") &&
                !finalUrl.endsWith(".ico", ignoreCase = true)
            ) {
                return WebsiteFaviconFetchResult.Failed
            }
            val bytes = readLimited(body.byteStream()) ?: return WebsiteFaviconFetchResult.Failed
            if (bytes.isEmpty()) return WebsiteFaviconFetchResult.Failed
            if (validateImage && !looksLikeImage(bytes)) return WebsiteFaviconFetchResult.Failed
            return WebsiteFaviconFetchResult.Updated(
                bytes = bytes,
                iconSourceUrl = finalUrl,
                etag = resp.header("ETag").orEmpty(),
                lastModified = resp.header("Last-Modified").orEmpty(),
            )
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray? {
        return runCatching {
            input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_ICON_BYTES) return@runCatching null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) return true
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) return true
        if (bytes.size >= 6) {
            val header = String(bytes, 0, minOf(bytes.size, 6), Charsets.US_ASCII)
            if (header.startsWith("GIF87a") || header.startsWith("GIF89a")) return true
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
            bytes[2] == 0x01.toByte() && bytes[3] == 0x00.toByte()
        ) return true
        if (bytes.size >= 4) {
            val header = String(bytes, 0, minOf(bytes.size, 4), Charsets.US_ASCII)
            if (header.startsWith("<svg", ignoreCase = true)) return true
        }
        return false
    }

    private fun resolvePageUrl(raw: String): String? {
        val key = WebsiteLogoCache.normalizeWebsiteKey(raw)
        if (key.isBlank()) return null
        return if (key.startsWith("http://") || key.startsWith("https://")) key else "https://$key"
    }

    private fun baseOrigin(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val defaultPort = if (httpUrl.scheme == "https") 443 else 80
        return "${httpUrl.scheme}://${httpUrl.host}" +
            if (httpUrl.port != defaultPort) ":${httpUrl.port}" else ""
    }

    private fun resolveAgainst(baseUrl: String, href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            URI(baseUrl).resolve(trimmed).toString()
        }.getOrNull()?.takeIf { isAllowedScheme(it, baseUrl) }
    }

    private fun isAllowedScheme(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun isAllowedScheme(url: String, pageUrl: String, allowHttp: Boolean = pageUrl.startsWith("http://", ignoreCase = true)): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return when (scheme) {
            "https" -> true
            "http" -> allowHttp
            else -> false
        }
    }

    companion object {
        private const val MAX_ICON_BYTES = 512 * 1024
        private const val MAX_HTML_BYTES = 256 * 1024
        private const val MAX_REDIRECTS = 5
        private val LINK_ICON_PATTERN = Pattern.compile(
            """<link[^>]+rel=["']([^"']+)["'][^>]*href=["']([^"']+)["'][^>]*(?:sizes=["']([^"']+)["'])?""",
            Pattern.CASE_INSENSITIVE,
        )
        private val LINK_ICON_PATTERN_ALT = Pattern.compile(
            """<link[^>]+href=["']([^"']+)["'][^>]*rel=["']([^"']+)["'][^>]*(?:sizes=["']([^"']+)["'])?""",
            Pattern.CASE_INSENSITIVE,
        )

        fun createClient(): OkHttpClient = LoginFlowService.createHttpClient().newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                var request = chain.request()
                repeat(MAX_REDIRECTS + 1) { attempt ->
                    val response = chain.proceed(request)
                    if (!response.isRedirect || attempt >= MAX_REDIRECTS) return@addInterceptor response
                    val location = response.header("Location")
                    response.close()
                    val nextUrl = location?.let { resolveRedirect(request.url.toString(), it) }
                    if (nextUrl == null || !isRedirectAllowed(request.url.toString(), nextUrl)) {
                        return@addInterceptor okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(403)
                            .message("Redirect blocked")
                            .body(ByteArray(0).toResponseBody(null))
                            .build()
                    }
                    request = request.newBuilder().url(nextUrl).build()
                }
                chain.proceed(request)
            }
            .build()

        private fun resolveRedirect(base: String, location: String): String? =
            runCatching { URI(base).resolve(location).toString() }.getOrNull()

        private fun isRedirectAllowed(from: String, to: String): Boolean {
            val toScheme = runCatching { URI(to).scheme?.lowercase() }.getOrNull() ?: return false
            if (toScheme != "http" && toScheme != "https") return false
            val fromScheme = runCatching { URI(from).scheme?.lowercase() }.getOrNull() ?: return false
            if (fromScheme == "https" && toScheme == "http") return false
            return true
        }
    }
}
