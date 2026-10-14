package com.nextcloud.contacts.platform

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.nextcloud.contacts.R

object ProviderIntegrationSupport {

    private val actionTokens = setOf(
        "profile", "contact", "chat", "message", "text", "call", "voip",
        "video", "videocall", "android",
    )

    fun inferMessagingAction(mimeType: String): MessagingAction? {
        val lower = mimeType.lowercase()
        return when {
            "video" in lower -> MessagingAction.Video
            "voip" in lower || (".call" in lower && "video" !in lower) -> MessagingAction.Voice
            else -> MessagingAction.Message
        }
    }

    fun actionLabel(
        context: Context,
        mimeType: String,
        messagingAction: MessagingAction?,
        summary: String,
        detail: String,
    ): String {
        detail.trim().takeIf { it.isNotBlank() }?.let { return it }
        summary.trim().takeIf { it.isNotBlank() }?.let { return it }
        messagingAction?.let { return defaultActionLabel(context, it) }
        return friendlyMimeLabel(mimeType)
    }

    fun defaultActionLabel(context: Context, action: MessagingAction): String = when (action) {
        MessagingAction.Message -> context.getString(R.string.action_message)
        MessagingAction.Video -> context.getString(R.string.sheet_video_call)
        MessagingAction.Voice -> context.getString(R.string.sheet_voice_call)
    }

    fun appLabel(
        context: Context,
        accountType: String?,
        accountName: String?,
        packageName: String?,
    ): String {
        packageName?.let { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return when (accountType) {
            KnownContactAccountTypes.GOOGLE ->
                accountName?.takeIf { it.isNotBlank() } ?: "Google"
            KnownContactAccountTypes.NEXTCLOUD -> context.getString(R.string.app_name)
            KnownContactAccountTypes.DEVICE -> context.getString(R.string.account_phone)
            KnownContactAccountTypes.SIM -> "SIM"
            else -> accountName?.takeIf { it.isNotBlank() }
                ?: accountType?.let(::friendlyAccountTypeLabel)
                ?: context.getString(R.string.account_connected_app)
        }
    }

    fun accountKey(accountType: String?, accountName: String?): String =
        ContactsProviderAccounts.accountKey(accountType.orEmpty(), accountName.orEmpty())

    fun isMessagingProviderAccount(accountType: String?): Boolean {
        if (accountType.isNullOrBlank()) return false
        return accountType !in setOf(
            KnownContactAccountTypes.NEXTCLOUD,
            KnownContactAccountTypes.GOOGLE,
            KnownContactAccountTypes.DEVICE,
            KnownContactAccountTypes.SIM,
        )
    }

    fun resolveInstalledPackages(
        context: Context,
        mimeType: String,
        accountType: String?,
        dataId: Long,
        explicitPackage: String? = null,
    ): List<String> {
        return packageCandidates(
            mimeType = mimeType,
            accountType = accountType,
            explicitPackage = explicitPackage,
        ).filter { packageName ->
            canHandleProviderAction(context, dataId, mimeType, packageName)
        }
    }

    fun packageCandidates(
        mimeType: String,
        accountType: String?,
        explicitPackage: String? = null,
    ): List<String> {
        val candidates = linkedSetOf<String>()
        explicitPackage?.let { candidates.add(it) }
        accountType?.takeIf { looksLikePackageName(it) }?.let { candidates.add(it) }
        candidates.addAll(packageCandidatesFromMime(mimeType))
        return candidates.toList()
    }

    fun canHandleProviderAction(
        context: Context,
        dataId: Long,
        mimeType: String,
        packageName: String,
    ): Boolean {
        if (dataId <= 0L || packageName.isBlank()) return false
        val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            setPackage(packageName)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    fun packageCandidatesFromMime(mimeType: String): List<String> {
        val prefix = "vnd.android.cursor.item/vnd."
        if (!mimeType.startsWith(prefix, ignoreCase = true)) return emptyList()

        val body = mimeType.substring(prefix.length)
        val parts = body.split('.')
        if (parts.size < 2) return emptyList()

        var end = parts.size
        while (end > 1 && isActionToken(parts[end - 1])) {
            end--
        }

        val candidate = parts.take(end).joinToString(".")
        return if (candidate.contains('.')) listOf(candidate) else emptyList()
    }

    private fun looksLikePackageName(value: String): Boolean = value.contains('.')

    private fun isActionToken(token: String): Boolean {
        val lower = token.lowercase()
        return lower in actionTokens ||
            lower.contains("call") ||
            lower.contains("video") ||
            lower.contains("chat")
    }

    private fun friendlyMimeLabel(mimeType: String): String {
        val token = mimeType.substringAfterLast('.').replace('_', ' ')
        return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun friendlyAccountTypeLabel(accountType: String): String {
        val token = accountType.substringAfterLast('.').replace('_', ' ')
        return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
