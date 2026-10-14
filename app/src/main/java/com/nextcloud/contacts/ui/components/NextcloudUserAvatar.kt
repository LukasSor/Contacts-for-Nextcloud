package com.nextcloud.contacts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.data.remote.NextcloudUserDirectory
import okhttp3.Credentials
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object NextcloudAvatarSupport {
    fun userIdFromCloudId(cloudId: String): String =
        cloudId.trim().substringBefore('@').trim()

    fun hostFromCloudId(cloudId: String): String? {
        val host = cloudId.trim().substringAfter('@', missingDelimiterValue = "").trim()
        return host.takeIf { it.isNotBlank() && '.' in it }
    }

    /** Nextcloud profile page for a Federated Cloud ID (`user@host` → `https://host/u/user`). */
    fun profileUrl(cloudId: String, credentials: AccountCredentials? = null): String? {
        val userId = userIdFromCloudId(cloudId)
        if (userId.isBlank()) return null
        val serverHost = hostFromServer(credentials?.server)
        val cloudHost = hostFromCloudId(cloudId)
        // Prefer the signed-in Nextcloud host when the cloud ID host is missing or matches it.
        // If the ID points at another host (true federation), keep that host.
        val host = when {
            serverHost != null && (cloudHost == null || cloudHost.equals(serverHost, ignoreCase = true)) ->
                serverHost
            cloudHost != null -> cloudHost
            else -> serverHost
        } ?: return null
        val encoded = URLEncoder.encode(userId, StandardCharsets.UTF_8).replace("+", "%20")
        return "https://$host/u/$encoded"
    }

    /** Profile URL on the signed-in server for a local Nextcloud user id. */
    fun profileUrlForLocalUser(userId: String, credentials: AccountCredentials?): String? {
        val id = userId.trim()
        if (id.isBlank() || '@' in id) {
            return profileUrl(id, credentials)
        }
        val host = hostFromServer(credentials?.server) ?: return null
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20")
        return "https://$host/u/$encoded"
    }

    fun hostFromServer(server: String?): String? {
        if (server.isNullOrBlank()) return null
        return runCatching { java.net.URI(server.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: server.trim().trimEnd('/')
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
    }

    /** Inverse of [profileUrl] when the value looks like a Nextcloud profile link. */
    fun cloudIdFromProfileUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.contains('@') && !trimmed.contains("://")) {
            return trimmed.takeIf { userIdFromCloudId(it).isNotBlank() && hostFromCloudId(it) != null }
        }
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val path = uri.path.orEmpty().trim('/')
        // /u/username or index.php/u/username
        val userId = when {
            path.contains("/u/") -> path.substringAfterLast("/u/").substringBefore('/').trim()
            path.startsWith("u/") -> path.removePrefix("u/").substringBefore('/').trim()
            else -> return null
        }
        if (userId.isBlank()) return null
        return "$userId@$host"
    }

    fun looksLikeCloudId(raw: String): Boolean {
        val value = raw.trim()
        return userIdFromCloudId(value).isNotBlank() && hostFromCloudId(value) != null
    }

    fun exampleProfileUrl(credentials: AccountCredentials?): String {
        val cloud = NextcloudUserDirectory.exampleCloudId(credentials)
        return profileUrl(cloud) ?: "https://example.com/u/user"
    }

    fun avatarUrl(credentials: AccountCredentials?, cloudId: String): String? {
        val creds = credentials ?: return null
        val userId = userIdFromCloudId(cloudId)
        if (userId.isBlank()) return null
        val encoded = URLEncoder.encode(userId, StandardCharsets.UTF_8).replace("+", "%20")
        return "${creds.server.trimEnd('/')}/index.php/avatar/$encoded/64"
    }

    fun authHeader(credentials: AccountCredentials?): String? {
        val creds = credentials ?: return null
        return Credentials.basic(creds.loginName, creds.appPassword)
    }

    fun exampleLabel(credentials: AccountCredentials?): String =
        NextcloudUserDirectory.exampleCloudId(credentials)
}

@Composable
fun NextcloudUserAvatar(
    cloudId: String,
    displayName: String = cloudId,
    avatarUrl: String? = null,
    authHeader: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credentials = rememberCredentials()
    val resolvedAuth = authHeader ?: NextcloudAvatarSupport.authHeader(credentials)
    val resolvedUrl = avatarUrl ?: NextcloudAvatarSupport.avatarUrl(credentials, cloudId)
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: NextcloudAvatarSupport.userIdFromCloudId(cloudId).firstOrNull()?.uppercaseChar()?.toString()
        ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (!resolvedAuth.isNullOrBlank() && !resolvedUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .addHeader("Authorization", resolvedAuth)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun rememberCredentials(): AccountCredentials? {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        (context.applicationContext as ContactsApp).container.credentialStore.getCredentials()
    }
}
