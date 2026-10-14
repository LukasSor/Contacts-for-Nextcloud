package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.ui.components.AppSnackbarHost
import com.nextcloud.contacts.ui.components.ObserveUiNotices
import com.nextcloud.contacts.data.local.AddressInput
import com.nextcloud.contacts.data.local.ContactCategories
import kotlinx.coroutines.delay
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.toInput
import com.nextcloud.contacts.data.local.LanguageOptions
import com.nextcloud.contacts.data.prefs.AvatarFrameStyle
import com.nextcloud.contacts.data.local.ProviderIntegrationsCache
import com.nextcloud.contacts.data.remote.LocalUserLookupResult
import com.nextcloud.contacts.data.remote.NextcloudUserDirectory
import com.nextcloud.contacts.data.remote.VCardMapper
import com.nextcloud.contacts.data.remote.NextcloudTalkService
import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MapsContactsIntents
import com.nextcloud.contacts.platform.MessagingContactsIntents
import com.nextcloud.contacts.platform.MessagingContactsIntents.LaunchResult
import com.nextcloud.contacts.platform.TalkContactsIntents
import com.nextcloud.contacts.platform.ServiceBrandIcons
import com.nextcloud.contacts.platform.VideoCallTargets
import com.nextcloud.contacts.ui.util.rememberPhoneCallAction
import com.nextcloud.contacts.ui.components.ContactAvatar
import com.nextcloud.contacts.ui.components.ConnectedAppsSection
import com.nextcloud.contacts.ui.components.ContactPhotoViewer
import com.nextcloud.contacts.ui.components.FramedContactPhoto
import com.nextcloud.contacts.ui.components.GroupedFormSection
import com.nextcloud.contacts.ui.components.InstantMessagingSection
import com.nextcloud.contacts.ui.components.MessengerLinkSuggestions
import com.nextcloud.contacts.ui.components.ContactDetailFieldRow
import com.nextcloud.contacts.ui.components.NextcloudProfileCardSection
import com.nextcloud.contacts.ui.components.WebsiteDetailRow
import com.nextcloud.contacts.ui.components.NextcloudProfileCardSupport
import com.nextcloud.contacts.ui.components.ContactNotesEditSheet
import com.nextcloud.contacts.ui.components.ContactNotesMarkdownPreview
import com.nextcloud.contacts.ui.components.NextcloudAvatarSupport
import com.nextcloud.contacts.ui.components.NextcloudUserAvatar
import com.nextcloud.contacts.ui.components.ActionChooserItem
import com.nextcloud.contacts.ui.components.ActionTargetChooserSheet
import com.nextcloud.contacts.ui.components.VideoCallChooserSheet
import com.nextcloud.contacts.ui.theme.NextcloudFavorite
import com.nextcloud.contacts.ui.util.localizedFieldLabel
import com.nextcloud.contacts.ui.util.localizedLanguageName
import com.nextcloud.contacts.ui.viewmodel.ContactDetailViewModel
import com.nextcloud.contacts.util.ContactDateCountdownField
import com.nextcloud.contacts.util.canOpenContactDateCountdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenContact: (Long) -> Unit = {},
    onOpenTimeZoneClock: (String) -> Unit = {},
    onOpenDateCountdown: (ContactDateCountdownField, String, String) -> Unit = { _, _, _ -> },
    onGroupFilterClick: (String) -> Unit = {},
    viewModel: ContactDetailViewModel = viewModel(),
) {
    val contact by viewModel.contact.collectAsState()
    val lineManagerContactId by viewModel.lineManagerContactId.collectAsState()
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val avatarFrameStyle by container.userPreferences.avatarFrameStyle.collectAsState(
        initial = AvatarFrameStyle.ORGANIC,
    )
    val enableWebsiteLogos by container.userPreferences.enableWebsiteLogos.collectAsState(
        initial = container.userPreferences.snapshot().enableWebsiteLogos,
    )
    val disabledAccountKeys by container.userPreferences.disabledAccountKeys.collectAsState(
        initial = container.userPreferences.snapshot().disabledAccountKeys,
    )
    var showPhotoViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    ObserveUiNotices { snackbarHostState.showSnackbar(it) }
    val startPhoneCall = rememberPhoneCallAction { result, withVideo ->
        when (result) {
            LaunchResult.Success -> Unit
            LaunchResult.NeedsCallPermission -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_phone_permission_calls),
                    Toast.LENGTH_LONG,
                ).show()
            }
            LaunchResult.Failed -> {
                val message = if (withVideo) {
                    context.getString(R.string.toast_video_call_failed)
                } else {
                    context.getString(R.string.toast_call_failed)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(contact?.contact?.displayName ?: stringResource(R.string.fallback_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val isFavorite = contact?.contact?.isFavorite == true
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) {
                                stringResource(R.string.cd_remove_favorites)
                            } else {
                                stringResource(R.string.cd_add_favorites)
                            },
                            tint = if (isFavorite) {
                                NextcloudFavorite
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                },
            )
        },
    ) { padding ->
        val details = contact ?: return@Scaffold
        val primaryPhone = details.phones.firstOrNull { it.value.isNotBlank() }?.value
        val phoneNumbers = details.phones.map { it.value }

        var videoTargets by remember { mutableStateOf<List<VideoCallTargets.Target>>(emptyList()) }
        var videoTargetsReady by remember { mutableStateOf(false) }
        var showVideoChooser by remember { mutableStateOf(false) }
        var showPhoneCallChooser by remember { mutableStateOf(false) }
        var phoneCallChooserItems by remember { mutableStateOf<List<ActionChooserItem>>(emptyList()) }
        var showMessageChooser by remember { mutableStateOf(false) }
        var messageChooserItems by remember { mutableStateOf<List<ActionChooserItem>>(emptyList()) }
        var showEmailChooser by remember { mutableStateOf(false) }
        var emailChooserItems by remember { mutableStateOf<List<ActionChooserItem>>(emptyList()) }
        var showTalkChooser by remember { mutableStateOf(false) }
        var talkChooserTargets by remember {
            mutableStateOf<List<NextcloudProfileCardSupport.TalkSheetTarget>>(emptyList())
        }
        var talkInProgress by remember { mutableStateOf(false) }
        var showNoteEditor by remember { mutableStateOf(false) }

        val credentials = remember { container.credentialStore.getCredentials() }
        val providerIntegrationsCache = details.contact.providerIntegrationsCache
        val onProviderIntegrationsCached = remember(phoneNumbers) {
            { groups: List<ContactsProviderIntegrations.AppGroup> ->
                viewModel.setProviderIntegrationsCache(phoneNumbers, groups)
            }
        }
        val seededProviderGroups = remember(providerIntegrationsCache, phoneNumbers) {
            ProviderIntegrationsCache.seedAppGroups(providerIntegrationsCache, phoneNumbers)
        }
        var providerGroups by remember(providerIntegrationsCache, phoneNumbers) {
            mutableStateOf(seededProviderGroups)
        }
        val talkConnectedAppAction = remember(providerGroups, disabledAccountKeys) {
            TalkContactsIntents.findTalkChatAction(providerGroups, disabledAccountKeys)
        }
        val nextcloudPresences = remember(
            details.socialProfiles,
            details.imAccounts,
            details.cloudIds,
        ) {
            NextcloudProfileCardSupport.collectPresences(
                details.socialProfiles,
                details.imAccounts,
                details.cloudIds,
            )
        }
        val talkInvite = remember(nextcloudPresences, credentials) {
            NextcloudProfileCardSupport.firstLocalTalkInvite(nextcloudPresences, credentials)
        }
        val talkServerHost = remember(credentials) {
            NextcloudAvatarSupport.hostFromServer(credentials?.server)
        }

        val cachedTalkUserId = details.contact.talkInviteVerifiedUserId
        val cachedTalkCloudId = details.contact.talkInviteVerifiedCloudId

        var showTalkAccount by remember(talkInvite, credentials, cachedTalkUserId, cachedTalkCloudId) {
            mutableStateOf(
                talkInvite != null &&
                    credentials != null &&
                    TalkContactsIntents.isTalkInstalled(context) &&
                    NextcloudProfileCardSupport.cachedTalkVerificationMatches(
                        invite = talkInvite!!,
                        verifiedUserId = cachedTalkUserId,
                        verifiedCloudId = cachedTalkCloudId,
                    ),
            )
        }

        val showTalk = TalkContactsIntents.isTalkInstalled(context) &&
            (showTalkAccount || talkConnectedAppAction != null)

        LaunchedEffect(talkInvite, credentials, cachedTalkUserId, cachedTalkCloudId) {
            if (!TalkContactsIntents.isTalkInstalled(context)) {
                showTalkAccount = false
                return@LaunchedEffect
            }
            val invite = talkInvite ?: run {
                showTalkAccount = false
                return@LaunchedEffect
            }
            val creds = credentials ?: run {
                showTalkAccount = false
                return@LaunchedEffect
            }

            val cacheMatches = NextcloudProfileCardSupport.cachedTalkVerificationMatches(
                invite = invite,
                verifiedUserId = cachedTalkUserId,
                verifiedCloudId = cachedTalkCloudId,
            )
            showTalkAccount = cacheMatches

            when (
                val result = withContext(Dispatchers.IO) {
                    NextcloudUserDirectory(creds).lookupLocalUserResult(
                        userId = invite.userId,
                        expectedCloudId = invite.resolvedCloudId,
                    )
                }
            ) {
                is LocalUserLookupResult.Found -> {
                    showTalkAccount = true
                    val (userId, cloudId) = NextcloudProfileCardSupport.talkVerificationToStore(invite)
                    viewModel.setTalkInviteVerification(userId, cloudId)
                }
                LocalUserLookupResult.NotFound -> {
                    showTalkAccount = false
                    if (!cachedTalkUserId.isNullOrBlank()) {
                        viewModel.clearTalkInviteVerification()
                    }
                }
                LocalUserLookupResult.Failed -> {
                    showTalkAccount = cacheMatches
                }
            }
        }

        LaunchedEffect(phoneNumbers, providerIntegrationsCache) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            // Opening a contact does not ask for READ_CONTACTS.
            if (!com.nextcloud.contacts.data.prefs.ConnectedAppsAccess.discoveryEnabled(hasPermission)) {
                providerGroups = emptyList()
                return@LaunchedEffect
            }
            val seeded = ProviderIntegrationsCache.seedAppGroups(providerIntegrationsCache, phoneNumbers)
            if (seeded.isNotEmpty()) {
                providerGroups = seeded
            }
            val groups = withContext(Dispatchers.IO) {
                ContactsProviderIntegrations.discoverGroupedCoalesced(context, phoneNumbers)
            }
            providerGroups = groups
            onProviderIntegrationsCached(groups)
        }

        LaunchedEffect(phoneNumbers, disabledAccountKeys, seededProviderGroups) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val includeConnectedApps =
                com.nextcloud.contacts.data.prefs.ConnectedAppsAccess.discoveryEnabled(hasPermission)
            if (includeConnectedApps && seededProviderGroups.isNotEmpty()) {
                ContactsProviderIntegrations.cacheAppGroups(phoneNumbers, seededProviderGroups)
            }
            videoTargets = withContext(Dispatchers.IO) {
                VideoCallTargets.collect(
                    context,
                    phoneNumbers,
                    disabledAccountKeys,
                    includeConnectedApps = includeConnectedApps,
                )
            }
            videoTargetsReady = true
        }

        fun handleLaunchResult(
            launch: () -> LaunchResult,
            failureMessage: String,
        ) {
            when (launch()) {
                LaunchResult.Success -> Unit
                LaunchResult.NeedsCallPermission -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_phone_permission_calls),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                LaunchResult.Failed -> {
                    Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        fun placeCall(number: String?, withVideo: Boolean = false) {
            val trimmed = number?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.toast_no_phone), Toast.LENGTH_SHORT).show()
                return
            }
            startPhoneCall(trimmed, withVideo)
        }

        fun callTargetsWithTalk(targets: List<VideoCallTargets.Target>): List<VideoCallTargets.Target> {
            if (!showTalk) return targets
            val talk = VideoCallTargets.talkTarget(context)
            if (targets.any { it.source is VideoCallTargets.Source.Talk }) return targets
            return targets + talk
        }

        fun openTalkAccountInvite(invite: NextcloudProfileCardSupport.LocalTalkInvite) {
            if (talkInProgress) {
                Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val host = talkServerHost
            val creds = credentials
            val userId = invite.userId
            if (userId.isBlank() || host.isNullOrBlank() || creds == null) {
                Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
                return
            }
            talkInProgress = true
            scope.launch {
                val tokenResult = withContext(Dispatchers.IO) {
                    NextcloudTalkService(creds).getOrCreateOneToOneRoom(userId)
                }
                talkInProgress = false
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
        }

        fun openTalkSheetTarget(target: NextcloudProfileCardSupport.TalkSheetTarget) {
            target.accountInvite?.let {
                openTalkAccountInvite(it)
                return
            }
            val connectedAction = target.connectedAction
            if (connectedAction == null) {
                Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
                return
            }
            when (MessagingContactsIntents.openProviderAction(context, connectedAction)) {
                LaunchResult.Success -> Unit
                LaunchResult.NeedsCallPermission,
                LaunchResult.Failed,
                -> Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
            }
        }

        fun onOpenInTalk() {
            if (!showTalk || talkInProgress) {
                Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val targets = NextcloudProfileCardSupport.collectTalkSheetTargets(
                presences = nextcloudPresences,
                credentials = credentials,
                verifiedUserId = cachedTalkUserId,
                verifiedCloudId = cachedTalkCloudId,
                connectedAction = talkConnectedAppAction,
                profileLookupCache = details.contact.nextcloudProfileLookupCache,
            )
            when {
                targets.isEmpty() -> {
                    Toast.makeText(context, context.getString(R.string.toast_talk_failed), Toast.LENGTH_SHORT).show()
                }
                targets.size == 1 -> openTalkSheetTarget(targets.single())
                else -> {
                    talkChooserTargets = targets
                    showTalkChooser = true
                }
            }
        }

        fun launchCallTarget(
            target: VideoCallTargets.Target,
            failureMessage: String,
        ) {
            if (target.source is VideoCallTargets.Source.Talk) {
                onOpenInTalk()
                return
            }
            if (target.source is VideoCallTargets.Source.Phone) {
                placeCall(target.source.number, withVideo = target.source.withVideo)
                return
            }
            handleLaunchResult(
                launch = { VideoCallTargets.launch(context, target) },
                failureMessage = failureMessage,
            )
        }

        fun launchVideoTarget(target: VideoCallTargets.Target) {
            launchCallTarget(target, failureMessage = context.getString(R.string.toast_video_call_failed))
        }

        fun onCallAction() {
            val phones = details.phones.filter { it.value.isNotBlank() }
            when (phones.size) {
                0 -> Toast.makeText(context, context.getString(R.string.toast_no_phone), Toast.LENGTH_SHORT).show()
                1 -> placeCall(phones.single().value, withVideo = false)
                else -> {
                    phoneCallChooserItems = contactPhoneChooserItems(context, phones)
                    showPhoneCallChooser = true
                }
            }
        }

        fun onMessageAction() {
            val phones = details.phones.filter { it.value.isNotBlank() }
            when (phones.size) {
                0 -> Toast.makeText(context, context.getString(R.string.toast_no_phone), Toast.LENGTH_SHORT).show()
                1 -> launchSms(context, phones.single().value)
                else -> {
                    messageChooserItems = contactPhoneChooserItems(context, phones)
                    showMessageChooser = true
                }
            }
        }

        fun onEmailAction() {
            val emails = details.emails.filter { it.value.isNotBlank() }
            when (emails.size) {
                0 -> Toast.makeText(context, context.getString(R.string.toast_no_email), Toast.LENGTH_SHORT).show()
                1 -> launchEmail(context, emails.single().value)
                else -> {
                    emailChooserItems = contactEmailChooserItems(context, emails)
                    showEmailChooser = true
                }
            }
        }

        fun onVideoCall() {
            fun present(targets: List<VideoCallTargets.Target>) {
                val choices = callTargetsWithTalk(targets)
                if (choices.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_no_video),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                if (choices.size == 1) {
                    launchVideoTarget(choices.single())
                    return
                }
                videoTargets = choices
                showVideoChooser = true
            }

            if (videoTargetsReady) {
                present(videoTargets)
                return
            }
            scope.launch {
                val targets = withContext(Dispatchers.IO) {
                    VideoCallTargets.collect(
                        context,
                        phoneNumbers,
                        disabledAccountKeys,
                        includeConnectedApps = com.nextcloud.contacts.data.prefs.ConnectedAppsAccess.discoveryEnabled(
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_CONTACTS,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                        ),
                    )
                }
                videoTargets = targets
                videoTargetsReady = true
                present(targets)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            FramedContactPhoto(
                contactId = details.contact.id,
                displayName = details.contact.displayName,
                frameStyle = avatarFrameStyle,
                size = 140.dp,
                modifier = Modifier.clickable { showPhotoViewer = true },
            )
            if (showPhotoViewer) {
                ContactPhotoViewer(
                    contactId = details.contact.id,
                    displayName = details.contact.displayName,
                    frameStyle = avatarFrameStyle,
                    onDismiss = { showPhotoViewer = false },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = details.contact.displayName,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (details.contact.organization.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.contact.organization,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (details.contact.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = details.contact.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            val primaryEmail = details.emails.firstOrNull { it.value.isNotBlank() }?.value
            ContactActionButtons(
                phoneNumber = primaryPhone,
                emailAddress = primaryEmail,
                showTalk = showTalk,
                talkEnabled = !talkInProgress,
                onCall = { onCallAction() },
                onMessage = { onMessageAction() },
                onVideo = { onVideoCall() },
                onTalk = { onOpenInTalk() },
                onEmail = { onEmailAction() },
            )

            val c = details.contact
            val detailGroups = remember(c.categories) { ContactCategories.parse(c.categories) }
            val detailFieldTop = Modifier.padding(top = 16.dp)

            if (detailGroups.isNotEmpty()) {
                Column(
                    modifier = detailFieldTop.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = stringResource(R.string.field_groups),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    GroupsDetailRow(
                        groups = detailGroups,
                        onGroupClick = onGroupFilterClick,
                    )
                }
            }

            ConnectedAppsSection(
                phones = phoneNumbers,
                existingIm = details.imAccounts,
                providerIntegrationsCache = providerIntegrationsCache,
                onProviderIntegrationsCached = onProviderIntegrationsCached,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (c.nickname.isNotBlank()) {
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = listOf {
                        ContactDetailFieldRow(
                            icon = Icons.Default.Badge,
                            value = c.nickname,
                            label = stringResource(R.string.field_nickname),
                            onClick = null,
                        )
                    },
                )
            }

            val phones = details.phones.filter { it.value.isNotBlank() }
            if (phones.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_phone),
                    modifier = detailFieldTop,
                    rows = phones.map { phone ->
                        {
                            ContactDetailFieldRow(
                                icon = Icons.Default.Phone,
                                value = phone.value,
                                label = ContactFieldOptions.typedFieldLabel(
                                    context,
                                    ContactFieldOptions.phones,
                                    phone.type,
                                    phone.vcardTypeParam,
                                ),
                                onClick = { placeCall(phone.value, withVideo = false) },
                                trailingContent = {
                                    IconButton(
                                        onClick = { launchSms(context, phone.value) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sms,
                                            contentDescription = stringResource(R.string.action_message),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }

            val emails = details.emails.filter { it.value.isNotBlank() }
            if (emails.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_email),
                    modifier = detailFieldTop,
                    rows = emails.map { email ->
                        {
                            ContactDetailFieldRow(
                                icon = Icons.Default.Email,
                                value = email.value,
                                label = ContactFieldOptions.typedFieldLabel(
                                    context,
                                    ContactFieldOptions.emails,
                                    email.type,
                                    email.vcardTypeParam,
                                ),
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.value}")),
                                    )
                                },
                            )
                        }
                    },
                )
            }

            if (c.role.isNotBlank()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_organization),
                    modifier = detailFieldTop,
                    rows = listOf {
                        ContactDetailFieldRow(
                            icon = Icons.Default.AssignmentInd,
                            value = c.role,
                            label = stringResource(R.string.field_role),
                            onClick = null,
                        )
                    },
                )
            }

            if (c.managersName.isNotBlank() || c.managersUid.isNotBlank()) {
                val managerId = lineManagerContactId
                val managerName = c.managersName.ifBlank { stringResource(R.string.label_linked_contact) }
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = listOf {
                        LineManagerDetailRow(
                            displayName = managerName,
                            contactId = managerId,
                            onOpenContact = managerId?.let { id -> { onOpenContact(id) } },
                        )
                    },
                )
            }

            val addressSectionRows = buildList<@Composable () -> Unit> {
                details.addresses.forEach { address ->
                    val addressInput = AddressInput(
                        type = address.type,
                        poBox = address.poBox,
                        extendedAddress = address.extendedAddress,
                        street = address.street,
                        city = address.city,
                        region = address.region,
                        postalCode = address.postalCode,
                        country = address.country,
                        geo = address.geo,
                    )
                    if (!addressInput.isBlank()) {
                        add {
                            AddressDetailFieldRow(
                                addressInput = addressInput,
                                label = ContactFieldOptions.typedFieldLabel(
                                    context,
                                    ContactFieldOptions.addresses,
                                    address.type,
                                    address.vcardTypeParam,
                                ),
                            )
                        }
                    }
                }
                if (details.addresses.isEmpty() && c.geo.isNotBlank()) {
                    add {
                        MapsLocationDetailFieldRow(
                            icon = Icons.Default.MyLocation,
                            value = c.geo,
                            label = null,
                        )
                    }
                }
            }
            if (addressSectionRows.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_address),
                    modifier = detailFieldTop,
                    rows = addressSectionRows,
                )
            }

            val contactDisplayName = c.displayName
            val dateRows = buildList<@Composable () -> Unit> {
                if (c.birthday.isNotBlank()) {
                    add {
                        ContactDetailFieldRow(
                            icon = Icons.Default.Cake,
                            value = c.birthday,
                            label = stringResource(R.string.field_birthday),
                            onClick = if (canOpenContactDateCountdown(c.birthday)) {
                                {
                                    onOpenDateCountdown(
                                        ContactDateCountdownField.BIRTHDAY,
                                        c.birthday,
                                        contactDisplayName,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                if (c.birthplace.isNotBlank()) {
                    add {
                        MapsLocationDetailFieldRow(
                            value = c.birthplace,
                            label = stringResource(R.string.field_place_of_birth),
                        )
                    }
                }
                if (c.anniversary.isNotBlank()) {
                    add {
                        ContactDetailFieldRow(
                            icon = Icons.Default.Event,
                            value = c.anniversary,
                            label = stringResource(R.string.field_anniversary),
                            onClick = if (canOpenContactDateCountdown(c.anniversary)) {
                                {
                                    onOpenDateCountdown(
                                        ContactDateCountdownField.ANNIVERSARY,
                                        c.anniversary,
                                        contactDisplayName,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            if (dateRows.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = dateRows,
                )
            }

            val imAccounts = details.imAccounts.filter { im ->
                im.value.isNotBlank() &&
                    !NextcloudProfileCardSupport.isNextcloudImAccount(im.type, im.value)
            }
            if (imAccounts.isNotEmpty()) {
                InstantMessagingSection(
                    accounts = imAccounts,
                    phones = phoneNumbers,
                    providerIntegrationsCache = providerIntegrationsCache,
                    onProviderIntegrationsCached = onProviderIntegrationsCached,
                    modifier = detailFieldTop,
                )
            }

            val social = details.socialProfiles.filter { profile ->
                profile.value.isNotBlank() &&
                    !NextcloudProfileCardSupport.isNextcloudSocialProfile(profile.type, profile.value)
            }
            if (social.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_social_network),
                    modifier = detailFieldTop,
                    rows = social.map { profile ->
                        {
                            val typeLabel = localizedFieldLabel(
                                ContactFieldOptions.social,
                                profile.type,
                            )
                            val openUri = socialProfileOpenUri(profile.type, profile.value)
                            val brandIconRes = ServiceBrandIcons.socialDrawableRes(profile.type)
                            ContactDetailFieldRow(
                                icon = Icons.Default.Public,
                                leadingIcon = brandIconRes?.let { res ->
                                    {
                                        Icon(
                                            painter = painterResource(res),
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = Color.Unspecified,
                                        )
                                    }
                                },
                                value = profile.value,
                                label = typeLabel,
                                asLink = openUri != null,
                                onClick = openUri?.let { uri ->
                                    {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        }
                                    }
                                },
                                trailingContent = if (openUri != null) {
                                    {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = stringResource(R.string.cd_open_profile),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .size(20.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    },
                )
            }

            val cloudIds = NextcloudProfileCardSupport.cloudIdsOutsideProfileCard(
                details.cloudIds,
                nextcloudPresences,
            )
            if (cloudIds.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_federated_cloud_id),
                    modifier = detailFieldTop,
                    rows = cloudIds.map { cloud ->
                        {
                            FederatedCloudIdDetailRow(
                                cloudId = cloud.value,
                                typeLabel = ContactFieldOptions.typedFieldLabel(
                                    context,
                                    ContactFieldOptions.cloudIds,
                                    cloud.type,
                                    cloud.vcardTypeParam,
                                ),
                            )
                        }
                    },
                )
            }

            NextcloudProfileCardSection(
                socialProfiles = details.socialProfiles,
                imAccounts = details.imAccounts,
                cloudIds = details.cloudIds,
                contactId = details.contact.id,
                profileLookupCache = details.contact.nextcloudProfileLookupCache,
                onProfileLookupCached = viewModel::setNextcloudProfileLookup,
                onProfileLookupCleared = viewModel::clearNextcloudProfileLookup,
                talkInviteVerifiedUserId = cachedTalkUserId,
                talkInviteVerifiedCloudId = cachedTalkCloudId,
                onTalkInviteVerified = { userId, cloudId ->
                    viewModel.setTalkInviteVerification(userId, cloudId)
                },
                onTalkInviteVerificationCleared = viewModel::clearTalkInviteVerification,
                modifier = detailFieldTop,
            )

            val urls = details.urls.filter { it.value.isNotBlank() }
            if (urls.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_website),
                    modifier = detailFieldTop,
                    rows = urls.map { url ->
                        {
                            WebsiteDetailRow(
                                url = url,
                                contactId = details.contact.id,
                                websiteLogoCache = details.contact.websiteLogoCache,
                                enableWebsiteLogos = enableWebsiteLogos,
                            )
                        }
                    },
                )
            }

            if (c.timeZone.isNotBlank()) {
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = listOf {
                        TimeZoneDetailRow(
                            timeZoneId = c.timeZone,
                            onClick = { onOpenTimeZoneClock(c.timeZone) },
                        )
                    },
                )
            }

            val languages = details.languages.filter { it.value.isNotBlank() }
            if (languages.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_spoken_languages),
                    modifier = detailFieldTop,
                    rows = languages.map { lang ->
                        {
                            ContactDetailFieldRow(
                                icon = Icons.Default.Translate,
                                value = localizedLanguageName(lang.value),
                                label = null,
                                onClick = null,
                            )
                        }
                    },
                )
            }

            val genderRelationshipRows = buildList<@Composable () -> Unit> {
                if (c.gender.isNotBlank()) {
                    add {
                        ContactDetailFieldRow(
                            icon = Icons.Default.Wc,
                            value = localizedFieldLabel(ContactFieldOptions.gender, c.gender),
                            label = stringResource(R.string.field_gender),
                            onClick = null,
                        )
                    }
                }
                if (c.relationshipToYou.isNotBlank()) {
                    add {
                        ContactDetailFieldRow(
                            icon = Icons.Default.Favorite,
                            value = localizedFieldLabel(
                                ContactFieldOptions.relationshipToYou,
                                c.relationshipToYou,
                            ),
                            label = stringResource(R.string.field_relationship),
                            onClick = null,
                        )
                    }
                }
            }
            if (genderRelationshipRows.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = genderRelationshipRows,
                )
            }

            val related = details.relatedNames.filter { it.value.isNotBlank() }
            if (related.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.section_related_contacts),
                    modifier = detailFieldTop,
                    rows = related.map { rel ->
                        {
                            ContactDetailFieldRow(
                                icon = Icons.Default.People,
                                value = rel.value,
                                label = ContactFieldOptions.typedFieldLabel(
                                    context,
                                    ContactFieldOptions.related,
                                    rel.type,
                                    rel.vcardTypeParam,
                                ),
                                onClick = null,
                            )
                        }
                    },
                )
            }

            val customFields = details.customFields.filter { it.toInput().hasPersistableContent() }
            if (customFields.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = customFields.map { field ->
                        {
                            ContactDetailFieldRow(
                                icon = Icons.AutoMirrored.Filled.Notes,
                                value = field.value,
                                label = field.toInput().detailLabel(),
                                onClick = null,
                            )
                        }
                    },
                )
            }

            if (details.contact.notes.isNotBlank()) {
                ContactDetailFieldSection(
                    title = stringResource(R.string.field_note),
                    modifier = detailFieldTop,
                    rows = listOf {
                        ContactDetailNoteFieldRow(
                            notes = details.contact.notes,
                            notesMarkdown = details.contact.notesMarkdown,
                            onClick = { showNoteEditor = true },
                        )
                    },
                )
            }

            val deathRows = buildList<@Composable () -> Unit> {
                if (c.deathDate.isNotBlank()) {
                    add {
                        ContactDetailFieldRow(
                            icon = Icons.Default.EventBusy,
                            value = c.deathDate,
                            label = stringResource(R.string.field_date_of_death),
                            onClick = if (canOpenContactDateCountdown(c.deathDate)) {
                                {
                                    onOpenDateCountdown(
                                        ContactDateCountdownField.DEATH,
                                        c.deathDate,
                                        contactDisplayName,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                if (c.deathPlace.isNotBlank()) {
                    add {
                        MapsLocationDetailFieldRow(
                            value = c.deathPlace,
                            label = stringResource(R.string.field_place_of_death),
                        )
                    }
                }
            }
            if (deathRows.isNotEmpty()) {
                ContactDetailFieldSection(
                    title = null,
                    modifier = detailFieldTop,
                    rows = deathRows,
                )
            }

            var showQrCode by remember { mutableStateOf(false) }

            MessengerLinkSuggestions(
                contactId = details.contact.id,
                phones = phoneNumbers,
                existingIm = details.imAccounts,
                onLink = viewModel::linkInstantMessaging,
                onDismiss = viewModel::dismissMessengerLink,
                providerIntegrationsCache = providerIntegrationsCache,
                onProviderIntegrationsCached = onProviderIntegrationsCached,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { shareContact(context, details, scope) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
                FilledTonalButton(
                    onClick = { showQrCode = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.label_qr_code))
                }
            }

            if (showNoteEditor) {
                ContactNotesEditSheet(
                    initialNotes = details.contact.notes,
                    initialNotesMarkdown = details.contact.notesMarkdown,
                    onDismiss = { showNoteEditor = false },
                    onSave = { notes, notesMarkdown ->
                        viewModel.saveNotes(notes, notesMarkdown)
                        showNoteEditor = false
                    },
                )
            }

            if (showQrCode) {
                ContactQrCodeSheet(
                    details = details,
                    onDismiss = { showQrCode = false },
                )
            }

            if (showPhoneCallChooser) {
                ActionTargetChooserSheet(
                    title = stringResource(R.string.action_call),
                    items = phoneCallChooserItems,
                    onSelect = { item ->
                        showPhoneCallChooser = false
                        placeCall(item.label, withVideo = false)
                    },
                    onDismiss = { showPhoneCallChooser = false },
                )
            }

            if (showMessageChooser) {
                ActionTargetChooserSheet(
                    title = stringResource(R.string.action_message),
                    items = messageChooserItems,
                    onSelect = { item ->
                        showMessageChooser = false
                        launchSms(context, item.label)
                    },
                    onDismiss = { showMessageChooser = false },
                )
            }

            if (showEmailChooser) {
                ActionTargetChooserSheet(
                    title = stringResource(R.string.chip_email),
                    items = emailChooserItems,
                    onSelect = { item ->
                        showEmailChooser = false
                        launchEmail(context, item.label)
                    },
                    onDismiss = { showEmailChooser = false },
                )
            }

            if (showTalkChooser) {
                ActionTargetChooserSheet(
                    title = stringResource(R.string.brand_talk),
                    items = talkChooserTargets.map { target ->
                        ActionChooserItem(
                            id = target.id,
                            label = target.label,
                            subtitle = target.subtitle,
                            iconRes = R.drawable.ic_nextcloud_talk,
                        )
                    },
                    onSelect = { item ->
                        showTalkChooser = false
                        val target = talkChooserTargets.firstOrNull { it.id == item.id }
                        if (target != null) {
                            openTalkSheetTarget(target)
                        }
                    },
                    onDismiss = { showTalkChooser = false },
                )
            }

            if (showVideoChooser) {
                VideoCallChooserSheet(
                    targets = videoTargets,
                    sheetTitle = stringResource(R.string.action_video),
                    onSelect = { target ->
                        showVideoChooser = false
                        launchVideoTarget(target)
                    },
                    onDismiss = { showVideoChooser = false },
                )
            }
        }
    }
}

@Composable
private fun ContactDetailFieldSection(
    title: String?,
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        GroupedFormSection(rows = rows)
    }
}

@Composable
private fun TimeZoneDetailRow(
    timeZoneId: String,
    onClick: () -> Unit,
) {
    var localTime by remember(timeZoneId) { mutableStateOf(formatContactLocalTime(timeZoneId)) }
    LaunchedEffect(timeZoneId) {
        while (true) {
            localTime = formatContactLocalTime(timeZoneId)
            delay(1_000)
        }
    }
    ContactDetailFieldRow(
        icon = Icons.Default.Schedule,
        value = localTime,
        label = timeZoneId,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineManagerDetailRow(
    displayName: String,
    contactId: Long?,
    onOpenContact: (() -> Unit)?,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyValue = displayName.trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenContact?.invoke() },
                onLongClick = {
                    if (copyValue.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(copyValue))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            contactId = contactId ?: 0L,
            displayName = displayName,
            size = 36.dp,
            loadStoredPhoto = contactId != null,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.field_line_manager),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FederatedCloudIdDetailRow(
    cloudId: String,
    typeLabel: String?,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyValue = cloudId.trim()
    val profileUrl = remember(cloudId) { NextcloudAvatarSupport.profileUrl(cloudId) }
    val linkColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (profileUrl.isNullOrBlank()) return@combinedClickable
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.toast_profile_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onLongClick = {
                    if (copyValue.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(copyValue))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NextcloudUserAvatar(
            cloudId = cloudId,
            displayName = NextcloudAvatarSupport.userIdFromCloudId(cloudId),
            size = 36.dp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = cloudId,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (profileUrl != null) TextDecoration.Underline else TextDecoration.None,
                ),
                color = if (profileUrl != null) linkColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!typeLabel.isNullOrBlank()) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (profileUrl != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.cd_open_profile),
                tint = linkColor,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupsDetailRow(
    groups: List<String>,
    onGroupClick: (String) -> Unit,
) {
    if (groups.isEmpty()) return
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val chipColors = AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val chipBorder = AssistChipDefaults.assistChipBorder(
        enabled = true,
        borderColor = MaterialTheme.colorScheme.secondaryContainer,
    )

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    val copyValue = ContactCategories.join(groups)
                    if (copyValue.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(copyValue))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
            AssistChip(
                onClick = { onGroupClick(group) },
                label = {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = chipColors,
                border = chipBorder,
            )
        }
    }
}

private fun formatContactLocalTime(timeZoneId: String): String {
    return try {
        val zone = ZoneId.of(timeZoneId)
        val now = ZonedDateTime.now(zone)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(now)
        val day = DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(now)
        "$time · $day"
    } catch (_: DateTimeException) {
        timeZoneId
    } catch (_: Exception) {
        timeZoneId
    }
}

private fun socialProfileOpenUri(type: String, raw: String): Uri? {
    val value = raw.trim()
    if (value.isBlank()) return null
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        return Uri.parse(value)
    }
    if (type.equals("nextcloud", ignoreCase = true)) {
        NextcloudAvatarSupport.profileUrl(value)?.let { return Uri.parse(it) }
    }
    if (value.startsWith("www.", ignoreCase = true)) {
        return Uri.parse("https://$value")
    }
    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddressDetailFieldRow(
    addressInput: AddressInput,
    label: String?,
    icon: ImageVector = Icons.Default.LocationOn,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val poBoxPrefix = stringResource(R.string.prefix_po_box)
    val postalText = remember(addressInput, poBoxPrefix) {
        addressInput.detailPostalLines(poBoxPrefix).joinToString("\n")
    }
    val postalCopyText = remember(addressInput, poBoxPrefix) {
        addressInput.detailPostalCopyText(poBoxPrefix)
    }
    val coordinatesText = remember(addressInput) { addressInput.detailCoordinatesDisplayText() }
    val formattedAddress = remember(addressInput) { addressInput.formatted() }
    val coordinates = remember(addressInput) { addressInput.geo.trim() }
    val postalMapsQuery = coordinates.ifBlank { formattedAddress }
    val canOpenPostalInMaps =
        postalMapsQuery.isNotEmpty() && MapsContactsIntents.geoUri(postalMapsQuery) != null
    val canOpenCoordinatesInMaps =
        coordinates.isNotEmpty() && MapsContactsIntents.geoUri(coordinates) != null
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val addressTextStyle = bodyStyle.copy(
        lineHeight = (bodyStyle.lineHeight.value + 4f).sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val coordinatesOnly = postalText.isBlank() && !coordinatesText.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            if (postalText.isNotBlank()) {
                Text(
                    text = postalText,
                    style = addressTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (canOpenPostalInMaps) {
                                MapsContactsIntents.openLocation(context, postalMapsQuery)
                            }
                        },
                        onLongClick = {
                            if (postalCopyText.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(postalCopyText))
                                Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                )
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!coordinatesText.isNullOrBlank()) {
                if (postalText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(7.dp))
                }
                Text(
                    text = coordinatesText,
                    style = if (coordinatesOnly) {
                        addressTextStyle
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = if (coordinatesOnly) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (canOpenCoordinatesInMaps) {
                                MapsContactsIntents.openLocation(context, coordinates)
                            }
                        },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(coordinatesText))
                            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                        },
                    ),
                )
                if (coordinatesOnly && !label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapsLocationDetailFieldRow(
    value: String,
    label: String?,
    icon: ImageVector = Icons.Default.LocationOn,
    singleLine: Boolean = true,
    mapsLocation: String = value,
) {
    val context = LocalContext.current
    val mapsQuery = mapsLocation.trim()
    val canOpenInMaps = mapsQuery.isNotEmpty() && MapsContactsIntents.geoUri(mapsQuery) != null
    ContactDetailFieldRow(
        icon = icon,
        value = value,
        label = label,
        onClick = if (canOpenInMaps) {
            { MapsContactsIntents.openLocation(context, mapsQuery) }
        } else {
            null
        },
        singleLine = singleLine,
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ContactDetailNoteFieldRow(
    notes: String,
    notesMarkdown: Boolean,
    onClick: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyValue = notes.trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (copyValue.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(copyValue))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Notes,
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            if (notesMarkdown) {
                ContactNotesMarkdownPreview(markdown = notes)
            } else {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactActionButtons(
    phoneNumber: String?,
    emailAddress: String?,
    showTalk: Boolean = false,
    talkEnabled: Boolean = true,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onVideo: () -> Unit,
    onTalk: () -> Unit = {},
    onEmail: () -> Unit,
) {
    val hasPhone = !phoneNumber.isNullOrBlank()
    val hasEmail = !emailAddress.isNullOrBlank()
    if (!hasPhone && !hasEmail && !showTalk) return

    data class Action(
        val icon: ImageVector? = null,
        val iconRes: Int? = null,
        val label: String,
        val enabled: Boolean,
        val onClick: () -> Unit,
    )

    val actions = buildList {
        if (hasPhone) {
            add(Action(icon = Icons.Default.Call, label = stringResource(R.string.action_call), enabled = true, onClick = onCall))
            add(Action(icon = Icons.Default.Sms, label = stringResource(R.string.action_message), enabled = true, onClick = onMessage))
            add(Action(icon = Icons.Default.Videocam, label = stringResource(R.string.action_video), enabled = true, onClick = onVideo))
        }
        if (showTalk) {
            add(
                Action(
                    iconRes = R.drawable.ic_nextcloud_talk,
                    label = stringResource(R.string.brand_talk),
                    enabled = talkEnabled,
                    onClick = onTalk,
                ),
            )
        }
        if (hasEmail) {
            add(Action(icon = Icons.Default.Email, label = stringResource(R.string.chip_email), enabled = true, onClick = onEmail))
        }
    }

    val maxItemsInEachRow = when {
        actions.size <= 4 -> actions.size
        else -> 3
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = maxItemsInEachRow,
    ) {
        actions.forEach { action ->
            ContactActionButton(
                icon = action.icon,
                iconRes = action.iconRes,
                label = action.label,
                enabled = action.enabled,
                onClick = action.onClick,
            )
        }
    }
}

@Composable
private fun ContactActionButton(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp),
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(width = 80.dp, height = 52.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    icon != null -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    iconRes != null -> {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

private fun contactPhoneChooserItems(
    context: android.content.Context,
    phones: List<com.nextcloud.contacts.data.local.PhoneEntity>,
): List<ActionChooserItem> = phones.map { phone ->
    val value = phone.value.trim()
    ActionChooserItem(
        id = "phone:${phone.id}:$value",
        label = value,
        subtitle = ContactFieldOptions.typedFieldLabel(
            context,
            ContactFieldOptions.phones,
            phone.type,
            phone.vcardTypeParam,
        ),
        icon = Icons.Default.Phone,
    )
}

private fun contactEmailChooserItems(
    context: android.content.Context,
    emails: List<com.nextcloud.contacts.data.local.EmailEntity>,
): List<ActionChooserItem> = emails.map { email ->
    val value = email.value.trim()
    ActionChooserItem(
        id = "email:${email.id}:$value",
        label = value,
        subtitle = ContactFieldOptions.typedFieldLabel(
            context,
            ContactFieldOptions.emails,
            email.type,
            email.vcardTypeParam,
        ),
        icon = Icons.Default.Email,
    )
}

private fun launchSms(context: android.content.Context, phone: String?) {
    val number = phone?.trim().orEmpty()
    if (number.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.toast_no_phone), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
    context.startActivity(intent)
}

private fun launchEmail(context: android.content.Context, email: String?) {
    val address = email?.trim().orEmpty()
    if (address.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.toast_no_email), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toast_no_email_app), Toast.LENGTH_SHORT).show()
    }
}

private fun shareContact(
    context: android.content.Context,
    details: ContactWithDetails,
    scope: CoroutineScope,
) {
    scope.launch {
        // The photo can be large. Read it off the main thread, then share.
        val vcardFile = withContext(Dispatchers.IO) {
            val container = (context.applicationContext as ContactsApp).container
            val photoBytes = container.contactRepository.getPhotoBytes(details.contact.id)
            val vcard = VCardMapper.toVCard(details, photoBytes)
            val safeName = details.contact.displayName
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .take(50)
                .ifBlank { "contact" }
            val shareDir = File(context.cacheDir, "shared_contacts").apply { mkdirs() }
            File(shareDir, "$safeName.vcf").apply {
                writeText(vcard, Charsets.UTF_8)
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            vcardFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, details.contact.displayName)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_contact)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactQrCodeSheet(
    details: ContactWithDetails,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val qrBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null,
        details.contact.id,
        details.contact.displayName,
        details.phones,
        details.emails,
    ) {
        value = withContext(Dispatchers.Default) {
            createContactQrBitmap(VCardMapper.toVCard(details, photoBytes = null))?.asImageBitmap()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContactAvatar(
                contactId = details.contact.id,
                displayName = details.contact.displayName,
                size = 96.dp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = details.contact.displayName.ifBlank { stringResource(R.string.fallback_contact) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            val qrImage = qrBitmap
            if (qrImage != null) {
                Image(
                    bitmap = qrImage,
                    contentDescription = stringResource(R.string.cd_qr_for, details.contact.displayName.ifBlank { stringResource(R.string.fallback_contact) }),
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.qr_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(R.string.qr_failed),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = { shareContact(context, details, scope) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_share))
            }
            FilledTonalButton(
                onClick = { dismissSheet() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

private fun createContactQrBitmap(content: String, size: Int = 800): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (_: Exception) {
        null
    }
}
