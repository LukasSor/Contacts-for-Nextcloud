package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.auth.LoginFlowService
import com.nextcloud.contacts.data.remote.DavXml.isEnd
import com.nextcloud.contacts.data.remote.DavXml.isStart
import com.nextcloud.contacts.data.remote.DavXml.readTextValue
import com.nextcloud.contacts.data.remote.DavXml.tagName
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CalDavCalendarClient(
    private val credentials: AccountCredentials,
) {
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val calendarMediaType = "text/calendar; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = LoginFlowService.createHttpClient().newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "Authorization",
                    Credentials.basic(credentials.loginName, credentials.appPassword),
                )
                .build()
            chain.proceed(request)
        }
        .build()

    fun calendarHomeUrl(): String {
        discoverCalendarHomeViaPrincipal()?.let { return it }
        val base = credentials.server.trimEnd('/')
        val user = urlEncodePathSegment(credentials.loginName)
        return "$base/remote.php/dav/calendars/$user/"
    }

    fun findCalendarCollection(homeUrl: String, slug: String): CalendarCollection? {
        val normalizedHome = homeUrl.trimEnd('/') + "/"
        val targetSuffix = "/$slug/"
        val xml = runCatching {
            propfindExecute(normalizedHome, depth = 1, props = listOf("resourcetype", "displayname"))
        }.getOrNull() ?: return null
        return parseCalendarCollections(xml, normalizedHome)
            .firstOrNull { collection ->
                collection.href.endsWith(targetSuffix, ignoreCase = true) ||
                    RemoteUrlNormalizer.storageKey(credentials.server, collection.href)
                        .endsWith("/$slug", ignoreCase = true)
            }
    }

    fun createCalendarCollection(homeUrl: String, slug: String, displayName: String): String {
        val normalizedHome = homeUrl.trimEnd('/') + "/"
        val collectionUrl = normalizedHome + urlEncodePathSegment(slug) + "/"
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <C:mkcalendar xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:set>
                <D:prop>
                  <D:displayname>${escapeXml(displayName)}</D:displayname>
                  <C:supported-calendar-component-set>
                    <C:comp name="VEVENT"/>
                  </C:supported-calendar-component-set>
                </D:prop>
              </D:set>
            </C:mkcalendar>
        """.trimIndent()
        val request = Request.Builder()
            .url(collectionUrl)
            .method("MKCALENDAR", body.toRequestBody(xmlMediaType))
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in SUCCESS_CREATE_CODES) {
                throwRequestError("MKCALENDAR", response)
            }
        }
        return collectionUrl
    }

    fun listEventResources(collectionUrl: String): List<CalendarResource> {
        val normalized = collectionUrl.trimEnd('/') + "/"
        val collectionPath = RemoteUrlNormalizer.storageKey(credentials.server, normalized)
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <d:getetag/>
                <d:resourcetype/>
                <cal:calendar-data/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
        val request = Request.Builder()
            .url(normalized)
            .method("PROPFIND", body.toRequestBody(xmlMediaType))
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throwRequestError("List events", response)
            }
            val xml = response.body?.string().orEmpty()
            return parseCalendarResources(xml, collectionPath)
        }
    }

    fun putEvent(eventUrl: String, ical: String, etag: String?) {
        val builder = Request.Builder()
            .url(eventUrl)
            .put(ical.toRequestBody(calendarMediaType))
        if (!etag.isNullOrBlank()) {
            builder.header("If-Match", etag)
        } else {
            builder.header("If-None-Match", "*")
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            if (response.isSuccessful || response.code in SUCCESS_UPDATE_CODES) return
            if (response.code == 412) {
                response.close()
                putEvent(eventUrl, ical, etag = null)
                return
            }
            throwRequestError("Put event", response)
        }
    }

    fun deleteResource(resourceUrl: String, etag: String?) {
        val builder = Request.Builder().url(resourceUrl).delete()
        if (!etag.isNullOrBlank()) {
            builder.header("If-Match", etag)
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            if (response.isSuccessful || response.code == 404) return
            if (response.code == 412) {
                response.close()
                deleteResource(resourceUrl, etag = null)
                return
            }
            throwRequestError("Delete", response)
        }
    }

    fun deleteCollection(collectionUrl: String) {
        val normalized = collectionUrl.trimEnd('/') + "/"
        httpClient.newCall(Request.Builder().url(normalized).delete().build()).execute().use { response ->
            if (response.isSuccessful || response.code == 404) return
            throwRequestError("Delete collection", response)
        }
    }

    fun eventUrl(collectionUrl: String, uid: String): String {
        val normalized = collectionUrl.trimEnd('/') + "/"
        return normalized + urlEncodePathSegment("$uid.ics")
    }

    private fun discoverCalendarHomeViaPrincipal(): String? {
        val davRoot = "${credentials.server.trimEnd('/')}/remote.php/dav/"
        val rootXml = runCatching {
            propfindExecute(davRoot, depth = 0, props = listOf("current-user-principal"))
        }.getOrNull() ?: return null

        val principalPath = DavXml.parseInnerHref(rootXml, "current-user-principal") ?: return null
        val principalUrl = resolveHref(principalPath)

        val principalXml = runCatching {
            propfindExecute(principalUrl, depth = 0, props = listOf("calendar-home-set"))
        }.getOrNull() ?: return null

        val homePath = DavXml.parseInnerHref(principalXml, "calendar-home-set") ?: return null
        return resolveHref(homePath).trimEnd('/') + "/"
    }

    private fun propfindExecute(url: String, depth: Int, props: List<String>): String {
        val propTags = props.joinToString("") { tag ->
            when (tag) {
                "calendar-home-set" -> "<cal:calendar-home-set/>"
                "calendar-data" -> "<cal:calendar-data/>"
                else -> "<d:$tag/>"
            }
        }
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop>$propTags</d:prop>
            </d:propfind>
        """.trimIndent()
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(xmlMediaType))
            .header("Depth", depth.toString())
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throwRequestError("Propfind", response)
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseCalendarCollections(xml: String, homeUrl: String): List<CalendarCollection> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val homePath = RemoteUrlNormalizer.storageKey(credentials.server, homeUrl)
        val results = mutableListOf<CalendarCollection>()
        var event = parser.eventType
        var currentHref: String? = null
        var displayName: String? = null
        var isCalendar = false
        while (event != END_DOCUMENT) {
            when {
                parser.isStart("response") -> {
                    currentHref = null
                    displayName = null
                    isCalendar = false
                }
                parser.isStart("href") && currentHref == null -> currentHref = parser.readTextValue()
                parser.isStart("displayname") -> displayName = parser.readTextValue()
                parser.isStart("calendar") -> isCalendar = true
                parser.isEnd("response") -> {
                    val href = currentHref ?: ""
                    val resolved = resolveHref(href)
                    val path = RemoteUrlNormalizer.storageKey(credentials.server, resolved)
                    if (isCalendar && path != homePath && path.startsWith(homePath)) {
                        results.add(
                            CalendarCollection(
                                href = resolved.trimEnd('/') + "/",
                                displayName = displayName,
                            ),
                        )
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    private fun parseCalendarResources(xml: String, collectionPath: String): List<CalendarResource> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val results = mutableListOf<CalendarResource>()
        var event = parser.eventType
        var currentHref: String? = null
        var etag: String? = null
        var calendarData: String? = null
        while (event != END_DOCUMENT) {
            when {
                parser.isStart("response") -> {
                    currentHref = null
                    etag = null
                    calendarData = null
                }
                parser.isStart("href") && currentHref == null -> currentHref = parser.readTextValue()
                parser.isStart("getetag") -> etag = parser.readTextValue().trim('"')
                parser.isStart("calendar-data") -> calendarData = parser.readTextValue()
                parser.isEnd("response") -> {
                    val href = currentHref
                    if (href != null) {
                        val resolved = resolveHref(href)
                        val path = RemoteUrlNormalizer.storageKey(credentials.server, resolved)
                        if (path != collectionPath && path.startsWith(collectionPath) &&
                            path.endsWith(".ics", ignoreCase = true)
                        ) {
                            results.add(
                                CalendarResource(
                                    href = resolved,
                                    etag = etag,
                                    calendarData = calendarData,
                                ),
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    private fun resolveHref(href: String): String {
        val trimmed = href.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val base = credentials.server.trimEnd('/')
        return if (trimmed.startsWith("/")) {
            base + trimmed
        } else {
            "$base/$trimmed"
        }
    }

    private fun urlEncodePathSegment(segment: String): String =
        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun throwRequestError(action: String, response: Response): Nothing {
        val body = response.body?.string().orEmpty().take(200)
        error("$action failed (${response.code}): $body")
    }

    data class CalendarCollection(
        val href: String,
        val displayName: String?,
    )

    data class CalendarResource(
        val href: String,
        val etag: String?,
        val calendarData: String?,
    )

    companion object {
        private val SUCCESS_CREATE_CODES = setOf(201, 204)
        private val SUCCESS_UPDATE_CODES = setOf(200, 201, 204)
    }
}
