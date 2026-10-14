package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.data.local.CloudIdEntity
import com.nextcloud.contacts.data.local.ImEntity
import com.nextcloud.contacts.data.local.NextcloudProfileLookupCache
import com.nextcloud.contacts.data.local.SocialProfileEntity
import com.nextcloud.contacts.data.remote.CloudUser
import com.nextcloud.contacts.data.remote.LocalUserLookupResult
import com.nextcloud.contacts.data.remote.NextcloudTalkService
import com.nextcloud.contacts.data.remote.NextcloudUserDirectory
import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MessengerImMapping
import com.nextcloud.contacts.platform.TalkContactsIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NextcloudProfilePresence(
    val cloudId: String?,
    val socialProfileUrl: String?,
    val imCloudId: String?,
    val federatedCloudId: String?,
)

object NextcloudProfileCardSupport {
    fun isNextcloudSocialProfile(type: String, value: String): Boolean {
        if (value.isBlank()) return false
        return type.equals("nextcloud", ignoreCase = true) ||
            NextcloudAvatarSupport.cloudIdFromProfileUrl(value) != null
    }

    fun isNextcloudImAccount(type: String, value: String): Boolean =
        value.isNotBlank() && MessengerImMapping.isNextcloudImType(type)

    fun collectPresences(
        socialProfiles: List<SocialProfileEntity>,
        imAccounts: List<ImEntity>,
        cloudIds: List<CloudIdEntity> = emptyList(),
    ): List<NextcloudProfilePresence> {
        val socials = socialProfiles.filter { isNextcloudSocialProfile(it.type, it.value) }
        val ims = imAccounts.filter { isNextcloudImAccount(it.type, it.value) }
        val clouds = cloudIds.filter { it.value.isNotBlank() }
        if (socials.isEmpty() && ims.isEmpty() && clouds.isEmpty()) return emptyList()

        data class MutablePresence(
            var cloudId: String? = null,
            var socialProfileUrl: String? = null,
            var imCloudId: String? = null,
            var federatedCloudId: String? = null,
        )

        val keyedOrder = ArrayList<String>()
        val keyed = LinkedHashMap<String, MutablePresence>()
        val orphans = ArrayList<MutablePresence>()

        fun touchKey(key: String): MutablePresence {
            if (key !in keyed) keyedOrder += key
            return keyed.getOrPut(key) { MutablePresence(cloudId = key) }
        }

        fun addOrphan(block: MutablePresence.() -> Unit) {
            orphans += MutablePresence().apply(block)
        }

        for (social in socials) {
            val canonical = canonicalCloudKey(social.value)
            if (canonical != null) {
                touchKey(canonical).apply {
                    socialProfileUrl = social.value.trim()
                    cloudId = cloudId ?: canonical
                }
            } else {
                addOrphan {
                    socialProfileUrl = social.value.trim()
                    cloudId = cloudIdForSocial(social.value)
                }
            }
        }

        for (im in ims) {
            val canonical = canonicalCloudKey(im.value)
            if (canonical != null) {
                touchKey(canonical).apply {
                    imCloudId = im.value.trim()
                    cloudId = cloudId ?: canonical
                }
            } else {
                addOrphan {
                    imCloudId = im.value.trim()
                    cloudId = cloudIdForIm(im.value)
                }
            }
        }

        for (cloud in clouds) {
            val value = cloud.value.trim()
            val canonical = canonicalCloudKey(value)
            if (canonical != null) {
                touchKey(canonical).apply {
                    federatedCloudId = value
                    cloudId = cloudId ?: canonical
                }
            } else {
                addOrphan {
                    federatedCloudId = value
                    cloudId = value.takeIf { it.isNotBlank() }
                }
            }
        }

        fun toPresence(m: MutablePresence) = NextcloudProfilePresence(
            cloudId = m.cloudId,
            socialProfileUrl = m.socialProfileUrl,
            imCloudId = m.imCloudId,
            federatedCloudId = m.federatedCloudId,
        )

        val initial = keyedOrder.map { toPresence(keyed.getValue(it)) } +
            orphans.map(::toPresence)
        return consolidatePresences(initial)
    }

    private fun mergePresence(
        a: NextcloudProfilePresence,
        b: NextcloudProfilePresence,
    ): NextcloudProfilePresence = NextcloudProfilePresence(
        cloudId = a.cloudId ?: b.cloudId,
        socialProfileUrl = a.socialProfileUrl ?: b.socialProfileUrl,
        imCloudId = a.imCloudId ?: b.imCloudId,
        federatedCloudId = a.federatedCloudId ?: b.federatedCloudId,
    )

    private fun presenceMatchKeys(presence: NextcloudProfilePresence): Set<String> = buildSet {
        presence.cloudId?.lowercase()?.let { add(it) }
        resolvedCloudIdFor(presence)?.lowercase()?.let { add(it) }
        listOfNotNull(
            presence.socialProfileUrl,
            presence.imCloudId,
            presence.federatedCloudId,
        ).forEach { raw ->
            canonicalCloudKey(raw)?.let { add(it) }
            raw.trim().lowercase().takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    /** One page per Nextcloud account. An IM-only row and a federated row for the same id collapse. */
    private fun consolidatePresences(
        initial: List<NextcloudProfilePresence>,
    ): List<NextcloudProfilePresence> {
        if (initial.size <= 1) return initial
        val merged = ArrayList<NextcloudProfilePresence>()
        val absorbed = BooleanArray(initial.size)
        for (i in initial.indices) {
            if (absorbed[i]) continue
            var acc = initial[i]
            val keys = presenceMatchKeys(acc).toMutableSet()
            var changed = true
            while (changed) {
                changed = false
                for (j in initial.indices) {
                    if (i == j || absorbed[j]) continue
                    val otherKeys = presenceMatchKeys(initial[j])
                    if (keys.intersect(otherKeys).isNotEmpty()) {
                        acc = mergePresence(acc, initial[j])
                        keys.addAll(presenceMatchKeys(acc))
                        absorbed[j] = true
                        changed = true
                    }
                }
            }
            merged += acc
        }
        return merged
    }

    fun resolvedCloudIdFor(presence: NextcloudProfilePresence): String? =
        presence.cloudId
            ?: presence.federatedCloudId?.let { canonicalCloudKey(it) ?: it.trim().takeIf { id -> id.isNotBlank() } }
            ?: presence.imCloudId?.let { cloudIdForIm(it) }
            ?: presence.socialProfileUrl?.let { cloudIdForSocial(it) }

    /** Cloud ids that are not already shown on a profile card. */
    fun cloudIdsOutsideProfileCard(
        cloudIds: List<CloudIdEntity>,
        presences: List<NextcloudProfilePresence>,
    ): List<CloudIdEntity> {
        if (presences.isEmpty()) return cloudIds.filter { it.value.isNotBlank() }
        val represented = buildSet {
            presences.forEach { presence ->
                presence.federatedCloudId?.trim()?.lowercase()?.let { add(it) }
                presence.cloudId?.lowercase()?.let { add(it) }
                listOfNotNull(
                    presence.socialProfileUrl,
                    presence.imCloudId,
                    presence.federatedCloudId,
                ).forEach { raw ->
                    canonicalCloudKey(raw)?.let { add(it) }
                }
            }
        }
        return cloudIds.filter { entity ->
            val value = entity.value.trim()
            if (value.isBlank()) return@filter false
            val canonical = canonicalCloudKey(value)?.lowercase()
            (canonical == null || canonical !in represented) && value.lowercase() !in represented
        }
    }

    private fun canonicalCloudKey(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        cloudIdForSocial(trimmed)?.lowercase()?.let { return it }
        cloudIdForIm(trimmed)?.lowercase()?.let { return it }
        return trimmed.takeIf { NextcloudAvatarSupport.looksLikeCloudId(it) }?.lowercase()
    }

    private fun cloudIdForSocial(value: String): String? =
        NextcloudAvatarSupport.cloudIdFromProfileUrl(value)
            ?: value.trim().takeIf { NextcloudAvatarSupport.looksLikeCloudId(it) }

    private fun cloudIdForIm(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        NextcloudAvatarSupport.cloudIdFromProfileUrl(trimmed)?.let { return it }
        return trimmed.takeIf { NextcloudAvatarSupport.looksLikeCloudId(it) }
    }

    fun linkRowCount(presence: NextcloudProfilePresence): Int =
        listOfNotNull(
            presence.socialProfileUrl,
            presence.imCloudId,
            presence.federatedCloudId,
        ).size

    fun profileOpenUrl(presence: NextcloudProfilePresence): String? {
        val cloudId = presence.cloudId
            ?: presence.federatedCloudId?.let { canonicalCloudKey(it) ?: it.trim().takeIf { id -> id.isNotBlank() } }
            ?: presence.imCloudId?.let { cloudIdForIm(it) }
            ?: presence.socialProfileUrl?.let { cloudIdForSocial(it) }
        cloudId?.let { NextcloudAvatarSupport.profileUrl(it) }?.let { return it }
        presence.socialProfileUrl?.trim()?.takeIf { it.contains("://") }?.let { return it }
        return null
    }

    /** User id on [credentials]' server. Null when the account is on another host. */
    fun localTalkInviteUserId(
        presence: NextcloudProfilePresence,
        credentials: AccountCredentials,
    ): String? {
        val serverHost = NextcloudAvatarSupport.hostFromServer(credentials.server) ?: return null
        val login = credentials.loginName.trim()
        val resolvedCloudId = resolvedCloudIdFor(presence)
        if (resolvedCloudId != null) {
            val cloudHost = NextcloudAvatarSupport.hostFromCloudId(resolvedCloudId)
            if (cloudHost != null && !cloudHost.equals(serverHost, ignoreCase = true)) {
                return null
            }
            val userId = NextcloudAvatarSupport.userIdFromCloudId(resolvedCloudId)
            if (userId.isBlank() || '@' in userId) return null
            if (userId.equals(login, ignoreCase = true)) return null
            return userId
        }
        listOfNotNull(
            presence.socialProfileUrl,
            presence.imCloudId,
            presence.federatedCloudId,
        ).forEach { raw ->
            userIdFromSameServerProfileUrl(raw, serverHost)?.let { userId ->
                if (!userId.equals(login, ignoreCase = true)) return userId
            }
        }
        return null
    }

    data class LocalTalkInvite(
        val userId: String,
        val resolvedCloudId: String?,
    )

    fun localTalkInvite(
        presence: NextcloudProfilePresence,
        credentials: AccountCredentials,
    ): LocalTalkInvite? {
        val userId = localTalkInviteUserId(presence, credentials) ?: return null
        return LocalTalkInvite(
            userId = userId,
            resolvedCloudId = resolvedCloudIdFor(presence),
        )
    }

    fun firstLocalTalkInvite(
        presences: List<NextcloudProfilePresence>,
        credentials: AccountCredentials?,
    ): LocalTalkInvite? {
        if (credentials == null || presences.isEmpty()) return null
        for (presence in presences) {
            localTalkInvite(presence, credentials)?.let { return it }
        }
        return null
    }

    fun firstLocalTalkInviteUserId(
        presences: List<NextcloudProfilePresence>,
        credentials: AccountCredentials?,
    ): String? = firstLocalTalkInvite(presences, credentials)?.userId

    /** Sharees lookup for [invite.userId]. No Talk room call. */
    suspend fun isTalkInviteVerified(
        credentials: AccountCredentials,
        invite: LocalTalkInvite,
    ): Boolean = withContext(Dispatchers.IO) {
        NextcloudUserDirectory(credentials).lookupLocalUser(
            userId = invite.userId,
            expectedCloudId = invite.resolvedCloudId,
        ) != null
    }

    fun talkVerificationToStore(invite: LocalTalkInvite): Pair<String, String?> {
        val cloudId = invite.resolvedCloudId?.let { raw ->
            canonicalCloudKey(raw) ?: raw.trim().takeIf { id -> id.isNotBlank() }
        }
        return invite.userId to cloudId
    }

    /**
     * Stored Talk user id must match [invite]. Cloud id is compared only when both sides have one.
     */
    fun cachedTalkVerificationMatches(
        invite: LocalTalkInvite,
        verifiedUserId: String?,
        verifiedCloudId: String?,
    ): Boolean {
        val cachedUser = verifiedUserId?.trim().orEmpty()
        if (cachedUser.isBlank()) return false
        if (!cachedUser.equals(invite.userId, ignoreCase = true)) return false
        val cachedCloud = verifiedCloudId?.trim().orEmpty()
        if (cachedCloud.isBlank()) return true
        val currentCloud = invite.resolvedCloudId?.let { raw ->
            canonicalCloudKey(raw) ?: raw.trim().takeIf { id -> id.isNotBlank() }
        } ?: return true
        return cachedCloud.equals(currentCloud, ignoreCase = true)
    }

    private fun userIdFromSameServerProfileUrl(raw: String, serverHost: String): String? {
        val cloudId = NextcloudAvatarSupport.cloudIdFromProfileUrl(raw.trim()) ?: return null
        val host = NextcloudAvatarSupport.hostFromCloudId(cloudId) ?: return null
        if (!host.equals(serverHost, ignoreCase = true)) return null
        return NextcloudAvatarSupport.userIdFromCloudId(cloudId).takeIf { it.isNotBlank() && '@' !in it }
    }

    data class TalkSheetTarget(
        val id: String,
        val label: String,
        val subtitle: String? = null,
        val accountInvite: LocalTalkInvite? = null,
        val connectedAction: ContactsProviderIntegrations.Action? = null,
    )

    fun talkSheetLabelForInvite(
        presence: NextcloudProfilePresence,
        invite: LocalTalkInvite,
        profileLookupCache: String,
    ): Pair<String, String?> {
        val cloudId = invite.resolvedCloudId ?: resolvedCloudIdFor(presence)
        val displayName = cloudId
            ?.let { NextcloudProfileLookupCache.get(profileLookupCache, it)?.displayName }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (displayName != null) {
            val subtitle = cloudId?.takeIf { !it.equals(displayName, ignoreCase = true) }
                ?: invite.userId.takeIf { !it.equals(displayName, ignoreCase = true) }
            return displayName to subtitle
        }
        return (cloudId?.takeIf { it.isNotBlank() } ?: invite.userId) to null
    }

    /** Verified accounts on this server, plus a phone-book Talk row when one exists. No Talk room calls. */
    fun collectTalkSheetTargets(
        presences: List<NextcloudProfilePresence>,
        credentials: AccountCredentials?,
        verifiedUserId: String?,
        verifiedCloudId: String?,
        connectedAction: ContactsProviderIntegrations.Action?,
        profileLookupCache: String,
    ): List<TalkSheetTarget> {
        if (credentials == null) {
            return listOfNotNull(
                connectedAction?.let { action ->
                    TalkSheetTarget(
                        id = "connected:${action.dataId}",
                        label = action.label,
                        connectedAction = action,
                    )
                },
            )
        }
        val seen = mutableSetOf<String>()
        val accounts = presences.mapNotNull { presence ->
            val invite = localTalkInvite(presence, credentials) ?: return@mapNotNull null
            if (!cachedTalkVerificationMatches(invite, verifiedUserId, verifiedCloudId)) {
                return@mapNotNull null
            }
            val dedupeKey = invite.userId.lowercase() + "\u0000" + (invite.resolvedCloudId?.lowercase() ?: "")
            if (!seen.add(dedupeKey)) return@mapNotNull null
            val (label, subtitle) = talkSheetLabelForInvite(presence, invite, profileLookupCache)
            TalkSheetTarget(
                id = "account:$dedupeKey",
                label = label,
                subtitle = subtitle,
                accountInvite = invite,
            )
        }
        val connected = connectedAction?.takeUnless { action ->
            isTalkConnectedDuplicateOfAccounts(action, accounts)
        }?.let { action ->
            TalkSheetTarget(
                id = "connected:${action.dataId}",
                label = action.label,
                connectedAction = action,
            )
        }
        return accounts + listOfNotNull(connected)
    }

    private fun isTalkConnectedDuplicateOfAccounts(
        action: ContactsProviderIntegrations.Action,
        accounts: List<TalkSheetTarget>,
    ): Boolean {
        if (accounts.size != 1) return false
        val invite = accounts.single().accountInvite ?: return false
        val detail = action.detail?.trim().orEmpty()
        if (detail.isBlank()) return false
        if (detail.equals(invite.userId, ignoreCase = true)) return true
        return invite.resolvedCloudId?.let { detail.equals(it, ignoreCase = true) } == true
    }
}

@Composable
fun NextcloudProfileCardSection(
    socialProfiles: List<SocialProfileEntity>,
    imAccounts: List<ImEntity>,
    cloudIds: List<CloudIdEntity> = emptyList(),
    contactId: Long,
    profileLookupCache: String = "",
    onProfileLookupCached: (CloudUser) -> Unit = {},
    onProfileLookupCleared: (String) -> Unit = {},
    talkInviteVerifiedUserId: String? = null,
    talkInviteVerifiedCloudId: String? = null,
    onTalkInviteVerified: (userId: String, cloudId: String?) -> Unit = { _, _ -> },
    onTalkInviteVerificationCleared: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val presences = remember(socialProfiles, imAccounts, cloudIds) {
        NextcloudProfileCardSupport.collectPresences(socialProfiles, imAccounts, cloudIds)
    }
    if (presences.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Nextcloud",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GroupedFormDefaults.CornerOuter),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            if (presences.size == 1) {
                NextcloudProfilePresenceBlock(
                    presence = presences.single(),
                    contactId = contactId,
                    profileLookupCache = profileLookupCache,
                    onProfileLookupCached = onProfileLookupCached,
                    onProfileLookupCleared = onProfileLookupCleared,
                    talkInviteVerifiedUserId = talkInviteVerifiedUserId,
                    talkInviteVerifiedCloudId = talkInviteVerifiedCloudId,
                    onTalkInviteVerified = onTalkInviteVerified,
                    onTalkInviteVerificationCleared = onTalkInviteVerificationCleared,
                )
            } else {
                val maxLinkRows = remember(presences) {
                    presences.maxOf(NextcloudProfileCardSupport::linkRowCount)
                }
                val pagerMinHeight = remember(maxLinkRows) {
                    profilePresenceMinHeight(maxLinkRows)
                }
                val pagerState = rememberPagerState(pageCount = { presences.size })
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = pagerMinHeight),
                    ) { page ->
                        NextcloudProfilePresenceBlock(
                            presence = presences[page],
                            minLinkRows = maxLinkRows,
                            contactId = contactId,
                            profileLookupCache = profileLookupCache,
                            onProfileLookupCached = onProfileLookupCached,
                            onProfileLookupCleared = onProfileLookupCleared,
                            talkInviteVerifiedUserId = talkInviteVerifiedUserId,
                            talkInviteVerifiedCloudId = talkInviteVerifiedCloudId,
                            onTalkInviteVerified = onTalkInviteVerified,
                            onTalkInviteVerificationCleared = onTalkInviteVerificationCleared,
                        )
                    }
                    NextcloudProfilePageIndicators(
                        pageCount = presences.size,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}

private fun profilePresenceMinHeight(linkRowCount: Int): Dp {
    val headerBlock = 152.dp
    if (linkRowCount == 0) return headerBlock
    return headerBlock + 9.dp + (linkRowCount * 56).dp
}

@Composable
private fun NextcloudProfilePageIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .size(width = if (selected) 8.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun NextcloudProfilePresenceBlock(
    presence: NextcloudProfilePresence,
    minLinkRows: Int? = null,
    contactId: Long,
    profileLookupCache: String,
    onProfileLookupCached: (CloudUser) -> Unit,
    onProfileLookupCleared: (String) -> Unit,
    talkInviteVerifiedUserId: String?,
    talkInviteVerifiedCloudId: String?,
    onTalkInviteVerified: (userId: String, cloudId: String?) -> Unit,
    onTalkInviteVerificationCleared: () -> Unit,
) {
    val context = LocalContext.current
    val credentials = remember {
        (context.applicationContext as ContactsApp).container.credentialStore.getCredentials()
    }
    val directory = remember(credentials) {
        credentials?.let { NextcloudUserDirectory(it) }
    }
    val resolvedCloudId = remember(presence) {
        NextcloudProfileCardSupport.resolvedCloudIdFor(presence)
    }
    val willLookup = remember(resolvedCloudId, directory) {
        val cloudId = resolvedCloudId ?: return@remember false
        if (directory == null) return@remember false
        NextcloudAvatarSupport.userIdFromCloudId(cloudId).isNotBlank()
    }
    val cachedProfile = remember(resolvedCloudId, profileLookupCache) {
        NextcloudProfileLookupCache.get(profileLookupCache, resolvedCloudId)
    }
    var lookedUpUser by remember(resolvedCloudId, profileLookupCache) {
        mutableStateOf(cachedProfile?.toCloudUser())
    }
    var lookupComplete by remember(resolvedCloudId, profileLookupCache) {
        mutableStateOf(!willLookup || cachedProfile != null)
    }

    LaunchedEffect(resolvedCloudId, directory, willLookup, contactId) {
        val cloudId = resolvedCloudId
        if (!willLookup || cloudId.isNullOrBlank()) {
            lookupComplete = true
            return@LaunchedEffect
        }
        val dir = directory ?: run {
            lookupComplete = true
            return@LaunchedEffect
        }
        val userId = NextcloudAvatarSupport.userIdFromCloudId(cloudId)
        if (userId.isBlank()) {
            lookupComplete = true
            return@LaunchedEffect
        }
        val seedProfile = NextcloudProfileLookupCache.get(profileLookupCache, cloudId)
        if (seedProfile == null) {
            lookupComplete = false
        }
        when (
            val result = withContext(Dispatchers.IO) {
                dir.lookupLocalUserResult(userId, cloudId)
            }
        ) {
            is LocalUserLookupResult.Found -> {
                lookedUpUser = result.user
                lookupComplete = true
                onProfileLookupCached(result.user)
            }
            LocalUserLookupResult.NotFound -> {
                lookedUpUser = null
                lookupComplete = true
                onProfileLookupCleared(cloudId)
            }
            LocalUserLookupResult.Failed -> {
                lookupComplete = true
            }
        }
    }

    val profileUrl = remember(presence) { NextcloudProfileCardSupport.profileOpenUrl(presence) }
    val fallbackTitle = remember(presence) {
        resolvedCloudId
            ?: presence.federatedCloudId?.trim()?.takeIf { it.isNotBlank() }
            ?: presence.socialProfileUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: presence.imCloudId?.trim()
            ?: ""
    }
    val lookupInProgress = willLookup && !lookupComplete && lookedUpUser == null
    val displayName = lookedUpUser?.displayName?.takeIf { it.isNotBlank() }
        ?: cachedProfile?.displayName?.takeIf { it.isNotBlank() }
    val titleText = when {
        lookupInProgress -> null
        displayName != null -> displayName
        fallbackTitle.isNotBlank() -> fallbackTitle
        else -> null
    }
    val reserveTitleLine = lookupInProgress || titleText != null
    val hasResolvedProfile = lookedUpUser != null || cachedProfile != null
    val subtitleText = when {
        lookupInProgress -> null
        hasResolvedProfile -> resolvedCloudId?.takeIf { it.isNotBlank() }
        else -> null
    }
    val reserveSubtitleLine = (lookupInProgress || hasResolvedProfile) && !resolvedCloudId.isNullOrBlank()
    val avatarCloudId = resolvedCloudId ?: fallbackTitle
    val avatarDisplayName = displayName ?: fallbackTitle.ifBlank { avatarCloudId }
    val avatarUrl = lookedUpUser?.avatarUrl ?: cachedProfile?.avatarUrl?.takeIf { it.isNotBlank() }
    val avatarAuth = directory?.avatarAuthorizationHeader()

    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val linkRows = buildList {
        presence.socialProfileUrl?.let { socialUrl ->
            add(
                NextcloudProfileDetailRowModel(
                    icon = Icons.Default.Public,
                    label = stringResource(R.string.field_social_profile),
                    value = socialUrl,
                    openUrl = socialOpenUrl(socialUrl, resolvedCloudId) ?: profileUrl,
                ),
            )
        }
        presence.imCloudId?.let { imValue ->
            add(
                NextcloudProfileDetailRowModel(
                    icon = Icons.Default.Link,
                    label = stringResource(R.string.field_instant_message),
                    value = imValue,
                    openUrl = profileUrl,
                ),
            )
        }
        presence.federatedCloudId?.let { cloudValue ->
            add(
                NextcloudProfileDetailRowModel(
                    icon = Icons.Default.Cloud,
                    label = stringResource(R.string.field_federated_cloud_id),
                    value = cloudValue,
                    openUrl = profileUrl,
                ),
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NextcloudProfileHeader(
            titleText = titleText,
            reserveTitleLine = reserveTitleLine,
            subtitleText = subtitleText,
            reserveSubtitleLine = reserveSubtitleLine,
            avatarCloudId = avatarCloudId,
            avatarDisplayName = avatarDisplayName,
            avatarUrl = avatarUrl,
            avatarAuth = avatarAuth,
            profileUrl = profileUrl,
        )
        if (linkRows.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .padding(horizontal = GroupedFormDefaults.RowPadding),
                thickness = 1.dp,
                color = dividerColor,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                linkRows.forEachIndexed { index, row ->
                    NextcloudProfileDetailRow(
                        icon = row.icon,
                        label = row.label,
                        value = row.value,
                        openUrl = row.openUrl,
                    )
                    if (index < linkRows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
                            thickness = 1.dp,
                            color = dividerColor,
                        )
                    }
                }
            }
            val padRows = minLinkRows?.minus(linkRows.size) ?: 0
            if (padRows > 0) {
                Spacer(modifier = Modifier.heightIn(min = (padRows * 56).dp))
            }
        }
        NextcloudProfileOpenTalkButton(
            presence = presence,
            credentials = credentials,
            talkInviteVerifiedUserId = talkInviteVerifiedUserId,
            talkInviteVerifiedCloudId = talkInviteVerifiedCloudId,
            onTalkInviteVerified = onTalkInviteVerified,
            onTalkInviteVerificationCleared = onTalkInviteVerificationCleared,
        )
    }
}

private val openTalkButtonVerticalPadding = 12.dp

@Composable
private fun NextcloudProfileOpenTalkButton(
    presence: NextcloudProfilePresence,
    credentials: AccountCredentials?,
    talkInviteVerifiedUserId: String?,
    talkInviteVerifiedCloudId: String?,
    onTalkInviteVerified: (userId: String, cloudId: String?) -> Unit,
    onTalkInviteVerificationCleared: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val talkInstalled = remember { TalkContactsIntents.isTalkInstalled(context) }
    if (!talkInstalled || credentials == null) return

    val talkInvite = remember(presence, credentials) {
        NextcloudProfileCardSupport.localTalkInvite(presence, credentials)
    } ?: return

    val talkServerHost = remember(credentials) {
        NextcloudAvatarSupport.hostFromServer(credentials.server)
    }

    var showTalkAccount by remember(talkInvite, talkInviteVerifiedUserId, talkInviteVerifiedCloudId) {
        mutableStateOf(
            NextcloudProfileCardSupport.cachedTalkVerificationMatches(
                invite = talkInvite,
                verifiedUserId = talkInviteVerifiedUserId,
                verifiedCloudId = talkInviteVerifiedCloudId,
            ),
        )
    }
    var talkVerifyComplete by remember(talkInvite, talkInviteVerifiedUserId, talkInviteVerifiedCloudId) {
        val cacheMatches = NextcloudProfileCardSupport.cachedTalkVerificationMatches(
            invite = talkInvite,
            verifiedUserId = talkInviteVerifiedUserId,
            verifiedCloudId = talkInviteVerifiedCloudId,
        )
        mutableStateOf(cacheMatches)
    }
    var talkOpenInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(talkInvite, credentials, talkInviteVerifiedUserId, talkInviteVerifiedCloudId) {
        val cacheMatches = NextcloudProfileCardSupport.cachedTalkVerificationMatches(
            invite = talkInvite,
            verifiedUserId = talkInviteVerifiedUserId,
            verifiedCloudId = talkInviteVerifiedCloudId,
        )
        showTalkAccount = cacheMatches
        if (cacheMatches) {
            talkVerifyComplete = true
            return@LaunchedEffect
        }
        talkVerifyComplete = false
        when (
            val result = withContext(Dispatchers.IO) {
                NextcloudUserDirectory(credentials).lookupLocalUserResult(
                    userId = talkInvite.userId,
                    expectedCloudId = talkInvite.resolvedCloudId,
                )
            }
        ) {
            is LocalUserLookupResult.Found -> {
                showTalkAccount = true
                val (userId, cloudId) = NextcloudProfileCardSupport.talkVerificationToStore(talkInvite)
                onTalkInviteVerified(userId, cloudId)
            }
            LocalUserLookupResult.NotFound -> {
                showTalkAccount = false
                if (NextcloudProfileCardSupport.cachedTalkVerificationMatches(
                        invite = talkInvite,
                        verifiedUserId = talkInviteVerifiedUserId,
                        verifiedCloudId = talkInviteVerifiedCloudId,
                    )
                ) {
                    onTalkInviteVerificationCleared()
                }
            }
            LocalUserLookupResult.Failed -> {
                showTalkAccount = cacheMatches
            }
        }
        talkVerifyComplete = true
    }

    val reserveTalkSlot = !showTalkAccount && !talkVerifyComplete
    if (!showTalkAccount && talkVerifyComplete) return

    val buttonModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = GroupedFormDefaults.RowPadding)
        .padding(top = openTalkButtonVerticalPadding, bottom = openTalkButtonVerticalPadding)

    if (reserveTalkSlot) {
        Spacer(
            modifier = buttonModifier.heightIn(min = 40.dp),
        )
        return
    }

    FilledTonalButton(
        onClick = {
            if (talkOpenInProgress) return@FilledTonalButton
            val userId = talkInvite.userId
            val host = talkServerHost
            if (userId.isBlank() || host.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
                return@FilledTonalButton
            }
            talkOpenInProgress = true
            scope.launch {
                val tokenResult = withContext(Dispatchers.IO) {
                    NextcloudTalkService(credentials).getOrCreateOneToOneRoom(userId)
                }
                talkOpenInProgress = false
                tokenResult.fold(
                    onSuccess = { token ->
                        if (!TalkContactsIntents.openChat(context, host, token)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_talk_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onFailure = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_talk_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        },
        enabled = !talkOpenInProgress,
        modifier = buttonModifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nextcloud_talk),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(R.string.action_open_talk))
    }
}

private data class NextcloudProfileDetailRowModel(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val openUrl: String?,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NextcloudProfileHeader(
    titleText: String?,
    reserveTitleLine: Boolean,
    subtitleText: String?,
    reserveSubtitleLine: Boolean,
    avatarCloudId: String,
    avatarDisplayName: String,
    avatarUrl: String?,
    avatarAuth: String?,
    profileUrl: String?,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val canOpen = !profileUrl.isNullOrBlank()
    val titleStyle = MaterialTheme.typography.titleLarge
    val subtitleStyle = MaterialTheme.typography.bodyMedium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GroupedFormDefaults.RowPadding)
            .combinedClickable(
                onClick = {
                    if (!canOpen) return@combinedClickable
                    openProfileUrl(context, profileUrl)
                },
                onLongClick = {
                    val copy = subtitleText?.takeIf { it.isNotBlank() }
                        ?: titleText?.takeIf { it.isNotBlank() }
                    if (!copy.isNullOrBlank()) {
                        clipboardManager.setText(AnnotatedString(copy))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NextcloudUserAvatar(
            cloudId = avatarCloudId,
            displayName = avatarDisplayName,
            avatarUrl = avatarUrl,
            authHeader = avatarAuth,
            size = 64.dp,
        )
        Spacer(modifier = Modifier.size(12.dp))
        if (reserveTitleLine) {
            if (titleText != null) {
                Text(
                    text = titleText,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "\u00a0",
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.alpha(0f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (reserveSubtitleLine || !subtitleText.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(4.dp))
            if (!subtitleText.isNullOrBlank()) {
                Text(
                    text = subtitleText,
                    style = subtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "\u00a0",
                    style = subtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alpha(0f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NextcloudProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    openUrl: String?,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val canOpen = !openUrl.isNullOrBlank()
    val displayValue = remember(value) { value.trim() }
    val valueOverflow = if (displayValue.contains("://")) {
        TextOverflow.MiddleEllipsis
    } else {
        TextOverflow.Ellipsis
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!canOpen) return@combinedClickable
                    openProfileUrl(context, openUrl)
                },
                onLongClick = {
                    if (displayValue.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(displayValue))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(
                start = GroupedFormDefaults.RowPadding,
                end = 4.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (displayValue.contains("://")) 1 else 2,
                overflow = valueOverflow,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canOpen) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.action_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
            )
        }
    }
}

private fun socialOpenUrl(socialValue: String, cloudId: String?): String? {
    val trimmed = socialValue.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    cloudId?.let { NextcloudAvatarSupport.profileUrl(it) }?.let { return it }
    return NextcloudAvatarSupport.profileUrl(trimmed)
}

private fun openProfileUrl(context: android.content.Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toast_profile_failed), Toast.LENGTH_SHORT).show()
    }
}
