package com.nextcloud.contacts.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object MessagingContactsIntents {

    enum class LaunchResult {
        Success,
        NeedsCallPermission,
        Failed,
    }

    /** ACTION_CALL when CALL_PHONE is granted. There is no setup switch. */
    fun startsCallDirectly(callPermissionGranted: Boolean): Boolean = callPermissionGranted

    fun placePhoneCall(
        context: Context,
        phone: String,
        withVideo: Boolean = false,
    ): LaunchResult {
        val number = phone.trim().replace(" ", "")
        if (number.isEmpty()) return LaunchResult.Failed

        val uri = Uri.fromParts("tel", number, null)
        val callPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED
        val canCall = startsCallDirectly(callPermissionGranted)
        if (canCall) {
            val call = Intent(Intent.ACTION_CALL, uri).apply {
                if (withVideo) {
                    putExtra("android.telecom.extra.START_CALL_WITH_VIDEO", true)
                }
            }
            when (val result = startActivityResult(context, call)) {
                LaunchResult.NeedsCallPermission -> Unit
                else -> return result
            }
        }
        val dial = Intent(Intent.ACTION_DIAL, uri).apply {
            if (withVideo) {
                putExtra("android.telecom.extra.START_CALL_WITH_VIDEO", true)
            }
        }
        return when (val result = startActivityResult(context, dial)) {
            LaunchResult.NeedsCallPermission -> LaunchResult.Failed
            else -> result
        }
    }

    private fun startActivityResult(context: Context, intent: Intent): LaunchResult {
        return try {
            context.startActivity(intent)
            LaunchResult.Success
        } catch (_: SecurityException) {
            LaunchResult.NeedsCallPermission
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Failed
        } catch (_: Exception) {
            LaunchResult.Failed
        }
    }

    fun openProviderAction(context: Context, action: ContactsProviderIntegrations.Action): LaunchResult {
        val packages = ProviderIntegrationSupport.resolveInstalledPackages(
            context = context,
            mimeType = action.mimeType,
            accountType = action.accountType,
            dataId = action.dataId,
            explicitPackage = action.packageName,
        ).ifEmpty {
            ProviderIntegrationSupport.packageCandidates(
                mimeType = action.mimeType,
                accountType = action.accountType,
                explicitPackage = action.packageName,
            )
        }
        return launchDataRow(context, action.dataId, action.mimeType, packages)
    }

    fun launchDataRow(
        context: Context,
        dataId: Long,
        mimeType: String,
        preferredPackages: List<String> = emptyList(),
    ): LaunchResult {
        if (dataId <= 0L || mimeType.isBlank()) return LaunchResult.Failed

        val packages = preferredPackages.filter { it.isNotBlank() }.distinct()
        if (packages.isEmpty()) return LaunchResult.Failed

        val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
        var needsCallPermission = false
        for (packageName in packages) {
            val intent = buildDataIntent(dataId, mimeType, packageName, uri)
            when (startContactDataIntent(context, intent)) {
                LaunchResult.Success -> return LaunchResult.Success
                LaunchResult.NeedsCallPermission -> needsCallPermission = true
                LaunchResult.Failed -> Unit
            }
        }
        return if (needsCallPermission) LaunchResult.NeedsCallPermission else LaunchResult.Failed
    }

    private fun buildDataIntent(
        dataId: Long,
        mimeType: String,
        packageName: String,
        uri: Uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId),
    ): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(packageName)
        }
    }

    private fun startContactDataIntent(context: Context, intent: Intent): LaunchResult {
        return try {
            context.startActivity(intent)
            LaunchResult.Success
        } catch (_: SecurityException) {
            LaunchResult.NeedsCallPermission
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Failed
        } catch (_: Exception) {
            LaunchResult.Failed
        }
    }

    fun phonesMatch(left: String, right: String): Boolean {
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        val minLength = minOf(left.length, right.length)
        if (minLength < 7) return false
        return left.takeLast(minLength) == right.takeLast(minLength)
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }

    /**
     * Opens a linked Instant messaging entry: prefers the system Connected-apps
     * profile/chat action when available, otherwise a messenger deep link.
     */
    fun openInstantMessaging(
        context: Context,
        imType: String,
        handle: String,
        phones: List<String> = emptyList(),
    ): LaunchResult {
        val type = imType.trim().lowercase()
        val value = handle.trim()
        if (type.isEmpty() || value.isEmpty()) return LaunchResult.Failed

        val lookupPhones = (phones + value).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val providerResult = openProviderActionForIm(context, type, lookupPhones)
        if (providerResult == LaunchResult.Success || providerResult == LaunchResult.NeedsCallPermission) {
            return providerResult
        }

        return openDeepLink(context, type, value)
    }

    private fun startViewUri(
        context: Context,
        uri: Uri,
        preferredPackages: List<String>,
    ): Boolean {
        val intents = preferredPackages.map { pkg ->
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } + Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        for (intent in intents) {
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return true
        }
        return false
    }

    private fun openProviderActionForIm(
        context: Context,
        imType: String,
        phones: List<String>,
    ): LaunchResult {
        if (phones.isEmpty()) return LaunchResult.Failed
        val actions = ContactsProviderIntegrations.discover(context, phones)
        val groups = ContactsProviderIntegrations.groupActions(context, actions)
        val matching = groups.filter { group ->
            MessengerImMapping.matchesImType(
                imType = imType,
                packageName = group.packageName,
                accountType = group.accountType,
                label = group.label,
            )
        }
        if (matching.isEmpty()) return LaunchResult.Failed

        val preferred = matching.flatMap { it.actions }.sortedWith { a, b ->
            val orderA = a.messagingAction?.sortOrder ?: Int.MAX_VALUE
            val orderB = b.messagingAction?.sortOrder ?: Int.MAX_VALUE
            if (orderA != orderB) orderA - orderB
            else a.label.compareTo(b.label, ignoreCase = true)
        }
        // Prefer chat/message, then profile-ish, then anything else.
        val ordered = preferred.sortedBy { action ->
            when (action.messagingAction) {
                MessagingAction.Message -> 0
                null -> 1
                MessagingAction.Voice -> 2
                MessagingAction.Video -> 3
            }
        }
        var needsCallPermission = false
        for (action in ordered) {
            when (val result = openProviderAction(context, action)) {
                LaunchResult.Success -> return LaunchResult.Success
                LaunchResult.NeedsCallPermission -> needsCallPermission = true
                LaunchResult.Failed -> Unit
            }
        }
        return if (needsCallPermission) LaunchResult.NeedsCallPermission else LaunchResult.Failed
    }

    private fun openDeepLink(context: Context, imType: String, handle: String): LaunchResult {
        val uri = deepLinkUri(imType, handle) ?: return LaunchResult.Failed
        return if (startViewUri(context, uri, MessengerImMapping.packagesForImType(imType))) {
            LaunchResult.Success
        } else {
            LaunchResult.Failed
        }
    }

    private fun deepLinkUri(imType: String, handle: String): Uri? {
        if (handle.contains("://")) {
            return runCatching { Uri.parse(handle) }.getOrNull()
        }
        val digits = digitsOnly(handle)
        val e164 = when {
            handle.trimStart().startsWith("+") && digits.isNotEmpty() -> "+$digits"
            digits.isNotEmpty() -> digits
            else -> null
        }
        val encodedHandle = Uri.encode(handle.trim().removePrefix("@"))
        return when (imType.lowercase()) {
            "whatsapp" -> e164?.let { Uri.parse("https://wa.me/${it.removePrefix("+")}") }
            "signal" -> e164?.let { Uri.parse("https://signal.me/#p/$it") }
            "telegram" -> when {
                handle.startsWith("@") || handle.any { it.isLetter() } ->
                    Uri.parse("https://t.me/${handle.removePrefix("@")}")
                e164 != null -> Uri.parse("https://t.me/+$digits")
                else -> null
            }
            "threema" -> Uri.parse("https://threema.id/$encodedHandle")
            "matrix" -> {
                val id = handle.removePrefix("matrix:")
                Uri.parse("https://matrix.to/#/${Uri.encode(id)}")
            }
            "xmpp" -> Uri.parse("xmpp:$handle")
            "sip" -> Uri.parse(if (handle.startsWith("sip:", true)) handle else "sip:$handle")
            "line" -> e164?.let { Uri.parse("https://line.me/R/ti/p/~$it") }
                ?: Uri.parse("https://line.me/R/ti/p/@$encodedHandle")
            "zoom" -> null
            "wechat", "kakaotalk" -> null
            else -> null
        }
    }
}
