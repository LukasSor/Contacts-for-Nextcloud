package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.auth.LoginFlowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.nio.charset.StandardCharsets

data class CloudUser(
    val id: String,
    val displayName: String,
    val cloudId: String,
    val avatarUrl: String,
)

sealed class LocalUserLookupResult {
    data class Found(val user: CloudUser) : LocalUserLookupResult()
    data object NotFound : LocalUserLookupResult()
    data object Failed : LocalUserLookupResult()
}

/**
 * Searches Nextcloud users via the sharees OCS API (same as the web share dialog).
 */
class NextcloudUserDirectory(
    private val credentials: AccountCredentials,
) {
    private val httpClient = LoginFlowService.createHttpClient().newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "Authorization",
                        Credentials.basic(credentials.loginName, credentials.appPassword),
                    )
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()

    private val serverHost: String = runCatching {
        URI(credentials.server.trim()).host?.takeIf { it.isNotBlank() }
    }.getOrNull().orEmpty().ifBlank {
        credentials.server.trim().trimEnd('/').removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
    }

    fun currentUser(selfDisplayName: String? = null): CloudUser {
        val login = credentials.loginName.trim()
        val encodedId = java.net.URLEncoder.encode(login, StandardCharsets.UTF_8)
            .replace("+", "%20")
        val base = credentials.server.trimEnd('/')
        return CloudUser(
            id = login,
            displayName = selfDisplayName ?: "You ($login)",
            cloudId = "$login@$serverHost",
            avatarUrl = "$base/index.php/avatar/$encodedId/64",
        )
    }

    /**
     * Confirms a local server account exists via the sharees OCS API (read-only).
     * Does not call Talk / Spreed. Returns null on network error or no exact match.
     */
    suspend fun lookupLocalUser(userId: String, expectedCloudId: String? = null): CloudUser? =
        when (val result = lookupLocalUserResult(userId, expectedCloudId)) {
            is LocalUserLookupResult.Found -> result.user
            else -> null
        }

    suspend fun lookupLocalUserResult(
        userId: String,
        expectedCloudId: String? = null,
    ): LocalUserLookupResult = withContext(Dispatchers.IO) {
        val id = userId.trim()
        if (id.isBlank()) return@withContext LocalUserLookupResult.NotFound
        runCatching {
            val users = searchUsers(id)
            val expected = expectedCloudId?.trim()?.takeIf { it.isNotBlank() }
            val match = if (expected != null) {
                users.firstOrNull { user ->
                    user.id.equals(id, ignoreCase = true) &&
                        user.cloudId.equals(expected, ignoreCase = true)
                }
            } else {
                users.firstOrNull { user -> user.id.equals(id, ignoreCase = true) }
            }
            if (match != null) {
                LocalUserLookupResult.Found(match)
            } else {
                LocalUserLookupResult.NotFound
            }
        }.getOrElse {
            LocalUserLookupResult.Failed
        }
    }

    suspend fun searchUsers(query: String, selfDisplayName: String? = null): List<CloudUser> = withContext(Dispatchers.IO) {
        val base = credentials.server.trimEnd('/')
        val url = "$base/ocs/v2.php/apps/files_sharing/api/v1/sharees"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("search", query.trim())
            ?.addQueryParameter("itemType", "file")
            ?.addQueryParameter("shareType[]", "0")
            ?.addQueryParameter("format", "json")
            ?.build()
            ?: error("Invalid server URL")

        val request = Request.Builder().url(url).get().build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            error("User search failed: HTTP ${response.code}")
        }

        val self = currentUser(selfDisplayName)
        val q = query.trim()
        val includeSelf = q.isEmpty() ||
            self.id.contains(q, ignoreCase = true) ||
            self.cloudId.contains(q, ignoreCase = true) ||
            self.displayName.contains(q, ignoreCase = true)

        // Sharees deliberately omits the signed-in user; prepend them when relevant.
        buildList {
            if (includeSelf) add(self)
            addAll(
                parseSharees(body).filterNot {
                    it.id.equals(self.id, ignoreCase = true) ||
                        it.cloudId.equals(self.cloudId, ignoreCase = true)
                },
            )
        }
    }

    private fun parseSharees(body: String): List<CloudUser> {
        val root = JSONObject(body)
        val data = root.optJSONObject("ocs")?.optJSONObject("data") ?: return emptyList()
        val exact = data.optJSONObject("exact")
        val users = buildList {
            exact?.optJSONArray("users")?.let { addAll(parseUserArray(it)) }
            data.optJSONArray("users")?.let { addAll(parseUserArray(it)) }
        }
        return users
            .distinctBy { it.cloudId.lowercase() }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun parseUserArray(array: org.json.JSONArray): List<CloudUser> {
        val base = credentials.server.trimEnd('/')
        val out = ArrayList<CloudUser>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val value = item.optJSONObject("value") ?: continue
            val shareWith = value.optString("shareWith").trim()
            if (shareWith.isBlank()) continue
            val label = item.optString("label").trim().ifBlank { shareWith }
            // shareWithDisplayNameUnique is often an email for display disambiguation — not a cloud ID.
            // Local users: userId@this-server. Federated-style shareWith already contains '@'.
            val cloudId = if ('@' in shareWith) {
                shareWith
            } else {
                "$shareWith@$serverHost"
            }
            val encodedId = java.net.URLEncoder.encode(shareWith, StandardCharsets.UTF_8)
                .replace("+", "%20")
            out += CloudUser(
                id = shareWith,
                displayName = label,
                cloudId = cloudId,
                avatarUrl = "$base/index.php/avatar/$encodedId/64",
            )
        }
        return out
    }

    fun avatarAuthorizationHeader(): String =
        Credentials.basic(credentials.loginName, credentials.appPassword)

    companion object {
        fun exampleCloudId(credentials: AccountCredentials?): String {
            val host = credentials?.server?.let { server ->
                runCatching { URI(server.trim()).host }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: server.trim().trimEnd('/')
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore('/')
            }.orEmpty().ifBlank { "example.com" }
            return "user@$host"
        }
    }
}
