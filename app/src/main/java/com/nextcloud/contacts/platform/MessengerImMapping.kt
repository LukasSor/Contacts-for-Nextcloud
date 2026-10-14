package com.nextcloud.contacts.platform

import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.ImEntity

/** Device messaging apps to Nextcloud `IMPP` types. */
object MessengerImMapping {

    const val NEXTCLOUD_IM_TYPE = "nextcloud"

    fun isNextcloudImType(type: String): Boolean =
        type.equals(NEXTCLOUD_IM_TYPE, ignoreCase = true)

    /**
     * True when this Nextcloud IM row refers to the same federated cloud ID
     * ([cloudId] or a profile URL that resolves to it).
     */
    fun isMatchingNextcloudIm(
        imType: String,
        imValue: String,
        cloudId: String,
        cloudIdFromProfileUrl: (String) -> String?,
    ): Boolean {
        if (!isNextcloudImType(imType)) return false
        val trimmed = imValue.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.equals(cloudId.trim(), ignoreCase = true)) return true
        cloudIdFromProfileUrl(trimmed)?.let { resolved ->
            if (resolved.equals(cloudId.trim(), ignoreCase = true)) return true
        }
        return false
    }

    data class Suggestion(
        val imType: String,
        val appLabel: String,
        val handle: String,
        val packageName: String?,
    )

    fun imTypeForApp(
        packageName: String?,
        accountType: String?,
        label: String,
    ): String? {
        val haystack = listOfNotNull(packageName, accountType, label)
            .joinToString(" ")
            .lowercase()
        return when {
            "whatsapp" in haystack -> "whatsapp"
            "signal" in haystack || "securesms" in haystack -> "signal"
            "telegram" in haystack -> "telegram"
            "threema" in haystack -> "threema"
            "viber" in haystack -> "other"
            "discord" in haystack -> "discord"
            "matrix" in haystack || "element" in haystack -> "matrix"
            "xmpp" in haystack || "conversations" in haystack -> "xmpp"
            "kakaotalk" in haystack || "kakao" in haystack -> "kakaotalk"
            "wechat" in haystack || "wechatsdk" in haystack -> "wechat"
            "line.android" in haystack || haystack.endsWith(" line") || "jp.naver.line" in haystack -> "line"
            "sip" in haystack && "whatsapp" !in haystack -> "sip"
            "zoom" in haystack -> "zoom"
            else -> null
        }?.takeIf { type -> ContactFieldOptions.im.any { it.id == type } || type == "other" }
    }

    fun suggestions(
        groups: List<ContactsProviderIntegrations.AppGroup>,
        phones: List<String>,
        existingIm: List<ImEntity>,
        dismissedKeys: Set<String>,
        contactId: Long,
    ): List<Suggestion> {
        val primaryHandle = phones.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (primaryHandle.isEmpty() && groups.isEmpty()) return emptyList()

        val seenTypes = mutableSetOf<String>()
        return groups.mapNotNull { group ->
            val imType = imTypeForApp(group.packageName, group.accountType, group.label)
                ?: return@mapNotNull null
            if (!seenTypes.add(imType)) return@mapNotNull null
            if (dismissKey(contactId, imType) in dismissedKeys) return@mapNotNull null
            if (alreadyLinked(existingIm, imType, primaryHandle)) return@mapNotNull null

            val handle = primaryHandle.ifBlank {
                group.actions
                    .asSequence()
                    .mapNotNull { action ->
                        action.detail?.trim()?.takeIf { it.isNotBlank() }
                            ?: action.label.trim().takeIf { it.isNotBlank() && it != group.label }
                    }
                    .firstOrNull()
                    .orEmpty()
            }
            if (handle.isBlank()) return@mapNotNull null

            Suggestion(
                imType = imType,
                appLabel = group.label,
                handle = handle,
                packageName = group.packageName,
            )
        }
    }

    fun dismissKey(contactId: Long, imType: String): String = "$contactId|$imType"

    fun alreadyLinked(existingIm: List<ImEntity>, imType: String, handle: String): Boolean {
        val handleDigits = MessagingContactsIntents.digitsOnly(handle)
        return existingIm.any { im ->
            if (!im.type.equals(imType, ignoreCase = true)) return@any false
            if (im.value.isBlank()) return@any false
            if (handle.isBlank()) return@any true
            val existingDigits = MessagingContactsIntents.digitsOnly(im.value)
            when {
                handleDigits.isNotEmpty() && existingDigits.isNotEmpty() ->
                    MessagingContactsIntents.phonesMatch(handleDigits, existingDigits)
                else -> im.value.trim().equals(handle.trim(), ignoreCase = true)
            }
        }
    }

    fun typeLabel(context: android.content.Context, imType: String): String =
        ContactFieldOptions.label(context, ContactFieldOptions.im, imType)

    fun packagesForImType(imType: String): List<String> = when (imType.lowercase()) {
        "whatsapp" -> listOf("com.whatsapp", "com.whatsapp.w4b")
        "signal" -> listOf("org.thoughtcrime.securesms")
        "telegram" -> listOf("org.telegram.messenger", "org.telegram.messenger.web")
        "threema" -> listOf("ch.threema.app")
        "discord" -> listOf("com.discord")
        "matrix" -> listOf("im.vector.app", "io.element.android.x")
        "xmpp" -> listOf("eu.siacs.conversations")
        "kakaotalk" -> listOf("com.kakao.talk")
        "wechat" -> listOf("com.tencent.mm")
        "line" -> listOf("jp.naver.line.android")
        "sip" -> emptyList()
        "zoom" -> listOf("us.zoom.videomeetings")
        else -> emptyList()
    }

    fun matchesImType(
        imType: String,
        packageName: String?,
        accountType: String?,
        label: String,
    ): Boolean = imTypeForApp(packageName, accountType, label)
        ?.equals(imType, ignoreCase = true) == true

    fun imTypeForGroup(group: ContactsProviderIntegrations.AppGroup): String? =
        imTypeForApp(
            packageName = group.packageName,
            accountType = group.accountType,
            label = group.label,
        )

    /** Instant messaging types that already have a matching Connected app (protocol). */
    fun connectedImTypes(groups: List<ContactsProviderIntegrations.AppGroup>): Set<String> =
        groups.mapNotNull { imTypeForGroup(it)?.lowercase() }.toSet()

    /**
     * True when this IMPP row is the same protocol as a Connected app.
     * Handle matching for suggestions/auto-link stays in [alreadyLinked].
     */
    fun isCoveredByConnectedApp(im: ImEntity, connectedImTypes: Set<String>): Boolean =
        im.type.ifBlank { "other" }.lowercase() in connectedImTypes

    /** True when this Connected app has a same-protocol Instant messaging account. */
    fun isLinkedToInstantMessaging(
        group: ContactsProviderIntegrations.AppGroup,
        existingIm: List<ImEntity>,
    ): Boolean {
        val imType = imTypeForGroup(group) ?: return false
        return existingIm.any { im ->
            im.value.isNotBlank() && im.type.equals(imType, ignoreCase = true)
        }
    }

    /** Stored IMPP handle for a Connected app group, when already linked on the contact. */
    fun linkedInstantMessagingHandle(
        group: ContactsProviderIntegrations.AppGroup,
        existingIm: List<ImEntity>,
    ): String? {
        val imType = imTypeForGroup(group) ?: return null
        return existingIm.firstOrNull { im ->
            im.value.isNotBlank() && im.type.equals(imType, ignoreCase = true)
        }?.value
    }
}
