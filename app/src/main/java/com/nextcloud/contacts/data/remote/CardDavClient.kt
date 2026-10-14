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

class CardDavClient(
    private val credentials: AccountCredentials,
) {
    private val vcardMediaType = "text/vcard; charset=utf-8".toMediaType()
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()

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

    private val addressBookUrl: String by lazy { discoverAddressBookUrl() }

    fun addressBookPath(): String = RemoteUrlNormalizer.storageKey(credentials.server, addressBookUrl)

    fun syncChanges(syncToken: String?): SyncResult {
        val body = buildSyncCollectionXml(syncToken)
        val request = Request.Builder()
            .url(addressBookUrl)
            .method("REPORT", body.toRequestBody(xmlMediaType))
            .header("Depth", "0")
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throwRequestError("Sync", response)
            }
            val xml = response.body?.string().orEmpty()
            return parseSyncCollection(xml, addressBookPath(), syncToken)
        }
    }

    fun listAddressBookContacts(): List<SyncChange> = listAddressBookContactsAt(addressBookUrl)

    fun downloadVCard(storageKey: String): String {
        val request = Request.Builder()
            .url(requestUrl(storageKey))
            .get()
            .header("Accept", "text/vcard, text/x-vcard, */*")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throwRequestError("Download", response)
            }
            return response.body?.string().orEmpty()
        }
    }

    fun uploadVCard(storageKey: String, vcard: String): String? {
        val remoteUrl = requestUrl(storageKey)
        val createRequest = Request.Builder()
            .url(remoteUrl)
            .put(vcard.toRequestBody(vcardMediaType))
            .header("If-None-Match", "*")
            .build()
        httpClient.newCall(createRequest).execute().use { response ->
            when {
                response.isSuccessful || response.code in SUCCESS_CREATE_CODES ->
                    return response.header("ETag")?.trim('"')
                response.code == 412 -> {
                    val etag = fetchEtag(storageKey)
                    return updateVCard(storageKey, vcard, etag)
                }
                else -> throwRequestError("Upload", response)
            }
        }
    }

    fun updateVCard(storageKey: String, vcard: String, etag: String?): String? {
        val response = putVCard(storageKey, vcard, etag)
        if (response.code == 412) {
            response.close()
            return putVCard(storageKey, vcard, etag = null).use { retry ->
                if (!retry.isSuccessful && retry.code !in SUCCESS_UPDATE_CODES) {
                    throwRequestError("Update", retry)
                }
                retry.header("ETag")?.trim('"') ?: fetchEtag(storageKey) ?: etag
            }
        }
        return response.use { resolved ->
            if (!resolved.isSuccessful && resolved.code !in SUCCESS_UPDATE_CODES) {
                throwRequestError("Update", resolved)
            }
            resolved.header("ETag")?.trim('"') ?: etag
        }
    }

    fun deleteVCard(storageKey: String, etag: String?) {
        val response = deleteVCardRequest(storageKey, etag)
        if (response.code == 412) {
            response.close()
            deleteVCardRequest(storageKey, etag = null).use { retry ->
                if (!retry.isSuccessful && retry.code != 404) {
                    throwRequestError("Delete", retry)
                }
            }
            return
        }
        response.use { resolved ->
            if (!resolved.isSuccessful && resolved.code != 404) {
                throwRequestError("Delete", resolved)
            }
        }
    }

    fun setFavorite(storageKey: String, favorite: Boolean) {
        val body = if (favorite) {
            """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propertyupdate xmlns:d="DAV:" xmlns:nc="http://nextcloud.com/ns">
              <d:set>
                <d:prop>
                  <nc:favorite>1</nc:favorite>
                </d:prop>
              </d:set>
            </d:propertyupdate>
            """.trimIndent()
        } else {
            """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propertyupdate xmlns:d="DAV:" xmlns:nc="http://nextcloud.com/ns">
              <d:remove>
                <d:prop>
                  <nc:favorite/>
                </d:prop>
              </d:remove>
            </d:propertyupdate>
            """.trimIndent()
        }
        val request = Request.Builder()
            .url(requestUrl(storageKey))
            .method("PROPPATCH", body.toRequestBody(xmlMediaType))
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 207) {
                val failed = failedPropstatCode(response.body?.string().orEmpty())
                if (failed != null) {
                    error("Favorite failed: HTTP $failed")
                }
                return
            }
            if (!response.isSuccessful) {
                throwRequestError("Favorite", response)
            }
        }
    }

    private fun deleteVCardRequest(storageKey: String, etag: String?): Response {
        val builder = Request.Builder().url(requestUrl(storageKey)).delete()
        if (!etag.isNullOrBlank()) {
            builder.header("If-Match", formatEtag(etag))
        }
        return httpClient.newCall(builder.build()).execute()
    }

    fun contactStorageKey(uid: String): String {
        val book = addressBookPath()
        val fileName = urlEncodePathSegment("$uid.vcf")
        return "$book/$fileName"
    }

    fun canonicalStorageKey(href: String): String =
        RemoteUrlNormalizer.storageKey(credentials.server, resolveHref(href))

    private fun requestUrl(storageKey: String): String =
        RemoteUrlNormalizer.requestUrl(credentials.server, storageKey)

    private fun discoverAddressBookUrl(): String {
        discoverViaPrincipal()?.let { return it }
        discoverViaUserPaths()?.let { return it }
        val base = credentials.server.trimEnd('/')
        return "$base/remote.php/dav/addressbooks/users/${credentials.loginName}/contacts/"
    }

    private fun discoverViaPrincipal(): String? {
        val davRoot = "${credentials.server.trimEnd('/')}/remote.php/dav/"
        val rootXml = runCatching {
            propfindExecute(davRoot, depth = 0, props = listOf("current-user-principal"))
        }.getOrNull() ?: return null

        val principalPath = DavXml.parseInnerHref(rootXml, "current-user-principal") ?: return null
        val principalUrl = resolveHref(principalPath)

        val principalXml = runCatching {
            propfindExecute(principalUrl, depth = 0, props = listOf("addressbook-home-set"))
        }.getOrNull() ?: return null

        val homePath = DavXml.parseInnerHref(principalXml, "addressbook-home-set") ?: return null
        val homeUrl = resolveHref(homePath).trimEnd('/') + "/"

        val homeXml = runCatching {
            propfindExecute(homeUrl, depth = 1, props = listOf("resourcetype", "displayname"))
        }.getOrNull() ?: return null

        val books = parseAddressBookCollections(homeXml, homeUrl)
        return pickBestAddressBook(books)
    }

    private fun discoverViaUserPaths(): String? {
        val base = credentials.server.trimEnd('/')
        val userCandidates = listOf(
            credentials.loginName,
            urlEncodePathSegment(credentials.loginName),
        ).distinct()
        val books = mutableListOf<String>()
        for (user in userCandidates) {
            val userRoot = "$base/remote.php/dav/addressbooks/users/$user/"
            listOf("${userRoot}contacts/", "${userRoot}Contacts/").forEach { candidate ->
                if (probeAddressBook(candidate)) books.add(candidate)
            }
            books.addAll(listChildAddressBooks(userRoot))
        }
        val unique = books.distinctBy { RemoteUrlNormalizer.storageKey(credentials.server, it) }
        return pickBestAddressBook(unique)
    }

    private fun pickBestAddressBook(books: List<String>): String? {
        if (books.isEmpty()) return null
        val ranked = books.map { book ->
            book to runCatching { listAddressBookContactsAt(book).size }.getOrDefault(0)
        }
        ranked.firstOrNull { it.second > 0 }?.let { return it.first }
        return ranked.firstOrNull { it.first.contains("contacts", ignoreCase = true) }?.first
            ?: ranked.first().first
    }

    private fun listAddressBookContactsAt(bookUrl: String): List<SyncChange> {
        val bookPath = RemoteUrlNormalizer.storageKey(credentials.server, bookUrl)
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav" xmlns:nc="http://nextcloud.com/ns">
              <d:prop>
                <d:getetag/>
                <d:resourcetype/>
                <nc:favorite/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
        val request = Request.Builder()
            .url(bookUrl)
            .method("PROPFIND", body.toRequestBody(xmlMediaType))
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throwRequestError("List contacts", response)
            }
            val xml = response.body?.string().orEmpty()
            return parsePropfindContacts(xml, bookPath)
        }
    }

    private fun propfindExecute(url: String, depth: Int, props: List<String>): String {
        val propTags = props.joinToString("") { "<d:$it/>" }
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
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

    private fun probeAddressBook(url: String): Boolean {
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:resourcetype/></d:prop>
            </d:propfind>
        """.trimIndent()
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(xmlMediaType))
            .header("Depth", "0")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 207
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun listChildAddressBooks(userRoot: String): List<String> {
        return try {
            val xml = propfindExecute(userRoot, depth = 1, props = listOf("resourcetype", "displayname"))
            parseAddressBookCollections(xml, userRoot)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseAddressBookCollections(xml: String, userRoot: String): List<String> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val books = mutableListOf<String>()
        val userRootNormalized = userRoot.trimEnd('/') + "/"
        var event = parser.eventType
        var inResponse = false
        var currentHref: String? = null
        var inResourceType = false
        var isCollection = false

        while (event != END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.tagName()) {
                    "response" -> {
                        inResponse = true
                        currentHref = null
                        isCollection = false
                    }
                    "href" -> if (inResponse) currentHref = parser.readTextValue()
                    "resourcetype" -> if (inResponse) inResourceType = true
                    "collection" -> if (inResourceType) isCollection = true
                }
                XmlPullParser.END_TAG -> when (parser.tagName()) {
                    "resourcetype" -> inResourceType = false
                    "response" -> {
                        if (inResponse && isCollection && currentHref != null) {
                            val resolved = resolveHref(currentHref!!).trimEnd('/') + "/"
                            if (!resolved.equals(userRootNormalized, ignoreCase = true)) {
                                books.add(resolved)
                            }
                        }
                        inResponse = false
                    }
                }
            }
            event = parser.next()
        }

        return books.sortedBy { book ->
            when {
                book.contains("/contacts/", ignoreCase = true) -> 0
                else -> 1
            }
        }
    }

    private fun putVCard(storageKey: String, vcard: String, etag: String?): Response {
        val builder = Request.Builder()
            .url(requestUrl(storageKey))
            .put(vcard.toRequestBody(vcardMediaType))
        if (!etag.isNullOrBlank()) {
            builder.header("If-Match", formatEtag(etag))
        }
        return httpClient.newCall(builder.build()).execute()
    }

    private fun fetchEtag(storageKey: String): String? {
        val remoteUrl = requestUrl(storageKey)
        val headRequest = Request.Builder().url(remoteUrl).head().build()
        httpClient.newCall(headRequest).execute().use { response ->
            if (response.isSuccessful) {
                return response.header("ETag")?.trim('"')
            }
        }

        val xml = runCatching {
            propfindExecute(remoteUrl, depth = 0, props = listOf("getetag"))
        }.getOrNull() ?: return null
        return parseEtagFromPropfind(xml)
    }

    private fun parseEtagFromPropfind(xml: String): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != END_DOCUMENT) {
            if (parser.isStart("getetag")) {
                return parser.readTextValue().trim('"')
            }
            event = parser.next()
        }
        return null
    }

    private fun throwRequestError(action: String, response: Response): Nothing {
        val body = response.body?.string().orEmpty()
        val detail = when {
            response.code == 412 -> "data changed on server"
            body.contains('<') -> ""
            else -> body.take(120).trim()
        }
        val suffix = if (detail.isBlank()) "" else ": $detail"
        error("$action failed: HTTP ${response.code}$suffix")
    }

    private fun formatEtag(etag: String): String =
        if (etag.startsWith("\"")) etag else "\"$etag\""

    private fun urlEncodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun buildSyncCollectionXml(syncToken: String?): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8" ?>""")
        append("""<d:sync-collection xmlns:d="DAV:" xmlns:nc="http://nextcloud.com/ns">""")
        append("<d:sync-token>")
        if (syncToken != null) append(syncToken)
        append("</d:sync-token>")
        append("<d:sync-level>1</d:sync-level>")
        append("<d:prop><d:getetag/><nc:favorite/></d:prop>")
        append("</d:sync-collection>")
    }

    private fun parseSyncCollection(xml: String, bookPath: String, previousToken: String?): SyncResult {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        val changes = mutableListOf<SyncChange>()
        var newToken: String? = previousToken
        var event = parser.eventType
        var inResponse = false
        var currentHref: String? = null
        var currentStatus: Int? = null
        var currentEtag: String? = null
        var currentFavorite: Boolean? = null

        while (event != END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.tagName()) {
                    "sync-token" -> newToken = parser.readTextValue()
                    "response" -> {
                        inResponse = true
                        currentHref = null
                        currentStatus = null
                        currentEtag = null
                        currentFavorite = null
                    }
                    "href" -> if (inResponse) currentHref = parser.readTextValue()
                    "status" -> if (inResponse) {
                        val statusText = parser.readTextValue()
                        currentStatus = statusText.substringAfter(" ").toIntOrNull()
                    }
                    "getetag" -> if (inResponse) currentEtag = parser.readTextValue().trim('"')
                    "favorite" -> if (inResponse) {
                        currentFavorite = parseFavoriteValue(parser.readTextValue())
                    }
                }
                XmlPullParser.END_TAG -> if (parser.tagName() == "response" && inResponse) {
                    if (currentHref != null && isContactResource(currentHref, bookPath)) {
                        val href = canonicalStorageKey(currentHref!!)
                        val deleted = currentStatus == 404
                        changes.add(
                            SyncChange(
                                href = href,
                                deleted = deleted,
                                etag = currentEtag,
                                favorite = if (deleted) null else currentFavorite,
                            ),
                        )
                    }
                    inResponse = false
                }
            }
            event = parser.next()
        }

        return SyncResult(changes = changes, syncToken = newToken)
    }

    private fun parsePropfindContacts(xml: String, bookPath: String): List<SyncChange> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        val changes = mutableListOf<SyncChange>()
        var event = parser.eventType
        var inResponse = false
        var currentHref: String? = null
        var currentEtag: String? = null
        var currentFavorite: Boolean? = null
        var inResourceType = false
        var isCollection = false

        while (event != END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.tagName()) {
                    "response" -> {
                        inResponse = true
                        currentHref = null
                        currentEtag = null
                        currentFavorite = null
                        isCollection = false
                    }
                    "href" -> if (inResponse) currentHref = parser.readTextValue()
                    "resourcetype" -> if (inResponse) inResourceType = true
                    "collection" -> if (inResourceType) isCollection = true
                    "getetag" -> if (inResponse) currentEtag = parser.readTextValue().trim('"')
                    "favorite" -> if (inResponse) {
                        currentFavorite = parseFavoriteValue(parser.readTextValue())
                    }
                }
                XmlPullParser.END_TAG -> when (parser.tagName()) {
                    "resourcetype" -> inResourceType = false
                    "response" -> {
                        if (inResponse && currentHref != null &&
                            isContactResource(currentHref, bookPath) && !isCollection
                        ) {
                            changes.add(
                                SyncChange(
                                    href = canonicalStorageKey(currentHref!!),
                                    deleted = false,
                                    etag = currentEtag,
                                    favorite = currentFavorite,
                                ),
                            )
                        }
                        inResponse = false
                    }
                }
            }
            event = parser.next()
        }

        return changes
    }

    private fun parseFavoriteValue(raw: String): Boolean {
        val value = raw.trim().lowercase()
        return value == "1" || value == "true" || value == "yes"
    }

    private fun isContactResource(href: String, bookPath: String): Boolean {
        val resolved = canonicalStorageKey(href).trimEnd('/').lowercase()
        val book = bookPath.trimEnd('/').lowercase()
        if (resolved == book) return false
        if (!resolved.startsWith("$book/")) return false
        val relative = resolved.removePrefix("$book/").trimStart('/')
        return relative.isNotEmpty() && !relative.contains('/')
    }

    private fun resolveHref(href: String): String {
        if (href.startsWith("http")) return href
        val base = credentials.server.trimEnd('/')
        return if (href.startsWith("/")) "$base$href" else "$base/$href"
    }

    data class SyncChange(
        val href: String,
        val deleted: Boolean,
        val etag: String?,
        val favorite: Boolean? = null,
    )

    data class SyncResult(
        val changes: List<SyncChange>,
        val syncToken: String?,
    )

    companion object {
        private val SUCCESS_CREATE_CODES = setOf(201, 204, 200)
        private val SUCCESS_UPDATE_CODES = setOf(200, 204)
    }
}

internal fun failedPropstatCode(xml: String): Int? {
    for (block in PROPPATCH_PROPSTAT.findAll(xml)) {
        val code = PROPPATCH_STATUS.find(block.groupValues[1])?.groupValues?.get(1)?.toIntOrNull()
            ?: continue
        if (code !in 200..299) return code
    }
    return null
}

private val PROPPATCH_PROPSTAT = Regex(
    """<(?:[\w.-]+:)?propstat\b[^>]*>(.*?)</(?:[\w.-]+:)?propstat>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val PROPPATCH_STATUS = Regex(
    """HTTP/\d+(?:\.\d+)?\s+(\d{3})""",
    RegexOption.IGNORE_CASE,
)
