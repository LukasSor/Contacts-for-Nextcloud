package com.nextcloud.contacts.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkContactsIntentsTest {

    @Test
    fun isTalkChatProviderAction_matchesTalkChatMime() {
        val action = ContactsProviderIntegrations.Action(
            dataId = 1L,
            mimeType = TalkContactsIntents.TALK_CHAT_MIME,
            label = "Chat via Nextcloud Talk",
            detail = null,
            accountType = TalkContactsIntents.TALK_PACKAGE,
            accountName = "Nextcloud Talk",
            packageName = TalkContactsIntents.TALK_PACKAGE,
            messagingAction = MessagingAction.Message,
        )
        assertTrue(TalkContactsIntents.isTalkChatProviderAction(action))
    }

    @Test
    fun isTalkChatProviderAction_rejectsUnrelatedMime() {
        val action = ContactsProviderIntegrations.Action(
            dataId = 2L,
            mimeType = "vnd.android.cursor.item/vnd.com.whatsapp.profile",
            label = "Message",
            detail = null,
            accountType = "com.whatsapp",
            accountName = null,
            packageName = "com.whatsapp",
            messagingAction = MessagingAction.Message,
        )
        assertFalse(TalkContactsIntents.isTalkChatProviderAction(action))
    }

    @Test
    fun findTalkChatAction_respectsDisabledAccountKeys() {
        val accountType = TalkContactsIntents.TALK_PACKAGE
        val accountName = "Nextcloud Talk"
        val key = ContactsProviderAccounts.accountKey(accountType, accountName)
        val action = ContactsProviderIntegrations.Action(
            dataId = 3L,
            mimeType = TalkContactsIntents.TALK_CHAT_MIME,
            label = "Chat via Nextcloud Talk",
            detail = null,
            accountType = accountType,
            accountName = accountName,
            packageName = TalkContactsIntents.TALK_PACKAGE,
            messagingAction = MessagingAction.Message,
        )
        val group = ContactsProviderIntegrations.AppGroup(
            key = key,
            label = "Nextcloud Talk",
            packageName = TalkContactsIntents.TALK_PACKAGE,
            accountType = accountType,
            actions = listOf(action),
        )
        assertNotNull(TalkContactsIntents.findTalkChatAction(listOf(group), emptySet()))
        assertNull(TalkContactsIntents.findTalkChatAction(listOf(group), setOf(key)))
    }
}
