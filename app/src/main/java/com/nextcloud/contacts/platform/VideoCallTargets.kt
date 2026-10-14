package com.nextcloud.contacts.platform

import android.content.Context
import android.telecom.TelecomManager
import com.nextcloud.contacts.R

/**
 * Voice- and video-capable launch targets for call chooser bottom sheets.
 */
object VideoCallTargets {

    enum class Mode {
        Voice,
        Video,
    }

    data class Target(
        val id: String,
        val label: String,
        val detail: String? = null,
        val packageName: String?,
        val iconRes: Int? = null,
        val source: Source,
    )

    sealed class Source {
        data class Phone(val number: String, val withVideo: Boolean) : Source()
        data class Provider(val action: ContactsProviderIntegrations.Action) : Source()
        data object Talk : Source()
    }

    fun talkTarget(context: Context): Target {
        val talkInstalled = TalkContactsIntents.isTalkInstalled(context)
        return Target(
            id = "nextcloud:talk",
            label = "Talk",
            packageName = if (talkInstalled) TalkContactsIntents.TALK_PACKAGE else null,
            iconRes = if (talkInstalled) null else R.drawable.ic_nextcloud_talk,
            source = Source.Talk,
        )
    }

    fun collect(
        context: Context,
        phones: List<String>,
        disabledAccountKeys: Set<String>,
        mode: Mode = Mode.Video,
        includeConnectedApps: Boolean = true,
    ): List<Target> {
        val targets = mutableListOf<Target>()
        val withVideo = mode == Mode.Video

        val primaryPhone = phones.firstOrNull { it.isNotBlank() }?.trim()
        if (!primaryPhone.isNullOrEmpty()) {
            val packageName = phoneAppPackage(context)
            targets += Target(
                id = "phone:$primaryPhone:$mode",
                label = context.getString(R.string.sheet_voice_call),
                packageName = packageName,
                source = Source.Phone(primaryPhone, withVideo = withVideo),
            )
        }

        val messagingAction = when (mode) {
            Mode.Voice -> MessagingAction.Voice
            Mode.Video -> MessagingAction.Video
        }
        val providerLabelSuffix = when (mode) {
            Mode.Voice -> context.getString(R.string.suffix_voice_call)
            Mode.Video -> context.getString(R.string.suffix_video_call)
        }

        if (includeConnectedApps) {
            val actions = ContactsProviderIntegrations.discover(context, phones)
            val groups = ContactsProviderIntegrations.groupActions(context, actions)
            groups.forEach { group ->
                if (
                    ContactsProviderAccounts.isDisabled(
                        accountType = group.accountType,
                        accountName = group.actions.firstOrNull()?.accountName,
                        disabledKeys = disabledAccountKeys,
                    )
                ) {
                    return@forEach
                }
                group.actions
                    .filter { it.messagingAction == messagingAction }
                    .forEach { action ->
                        val detail = action.detail?.trim()?.takeIf { it.isNotBlank() && it != group.label }
                        targets += Target(
                            id = "provider:${action.dataId}:$mode",
                            label = "${group.label} $providerLabelSuffix",
                            detail = detail,
                            packageName = group.packageName ?: action.packageName,
                            source = Source.Provider(action),
                        )
                    }
            }
        }

        return targets
    }

    fun launch(
        context: Context,
        target: Target,
    ): MessagingContactsIntents.LaunchResult {
        return when (val source = target.source) {
            is Source.Phone -> MessagingContactsIntents.placePhoneCall(
                context = context,
                phone = source.number,
                withVideo = source.withVideo,
            )
            is Source.Provider -> MessagingContactsIntents.openProviderAction(context, source.action)
            Source.Talk -> MessagingContactsIntents.LaunchResult.Failed
        }
    }

    private fun phoneAppPackage(context: Context): String? {
        return runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
