package com.nextcloud.contacts.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object TalkContactsIntents {
    const val TALK_PACKAGE = "com.nextcloud.talk2"

    /** MIME for Talk phone-book integration rows (Nextcloud Talk Android). */
    const val TALK_CHAT_MIME = "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat"

    fun isTalkChatProviderAction(action: ContactsProviderIntegrations.Action): Boolean {
        if (action.mimeType.equals(TALK_CHAT_MIME, ignoreCase = true)) return true
        val mimeLower = action.mimeType.lowercase()
        if (!mimeLower.contains("com.nextcloud.talk2")) return false
        return "chat" in mimeLower || action.messagingAction == MessagingAction.Message
    }

    fun findTalkChatAction(
        groups: List<ContactsProviderIntegrations.AppGroup>,
        disabledAccountKeys: Set<String> = emptySet(),
    ): ContactsProviderIntegrations.Action? {
        for (group in groups) {
            if (ContactsProviderAccounts.isDisabled(
                    accountType = group.accountType,
                    accountName = group.actions.firstOrNull()?.accountName,
                    disabledKeys = disabledAccountKeys,
                )
            ) {
                continue
            }
            for (action in group.actions) {
                if (isTalkChatProviderAction(action)) return action
            }
        }
        return null
    }

    fun openChat(context: Context, serverHost: String, roomToken: String): Boolean {
        val host = serverHost.trim()
        val token = roomToken.trim()
        if (host.isBlank() || token.isBlank()) return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nextcloudtalk://$host/call/$token"))
        if (isTalkInstalled(context)) {
            intent.setPackage(TALK_PACKAGE)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isTalkInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(TALK_PACKAGE, 0)
            true
        }.getOrDefault(false)
}
