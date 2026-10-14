package com.nextcloud.contacts.auth

import android.net.Uri

object ServerUrlNormalizer {

    fun normalize(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return url

        url = url.replace(" ", "")

        if (!hasScheme(url)) {
            url = "https://$url"
        }

        return url.trimEnd('/')
    }

    /** Also tries `nextcloud.` and `nc.` when the host has neither. Skips IP addresses and localhost. */
    fun candidates(input: String): List<String> {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return emptyList()

        val uri = Uri.parse(normalized)
        val host = uri.host?.lowercase() ?: return listOf(normalized)
        val scheme = uri.scheme?.lowercase() ?: "https"
        val port = uri.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()
        val path = uri.encodedPath
            ?.takeIf { it.isNotBlank() && it != "/" }
            ?.trimEnd('/')
            .orEmpty()

        val result = LinkedHashSet<String>()
        fun add(candidateHost: String) {
            result.add("$scheme://$candidateHost$port$path".trimEnd('/'))
        }

        add(host)

        if (!isIpOrLocalHost(host) && !hasNextcloudSubdomain(host)) {
            add("nextcloud.$host")
            add("nc.$host")
        }

        return result.toList()
    }

    /**
     * Poll often returns the public overwrite host. When that host is not the one
     * that answered login, keep the URL that was typed.
     */
    fun credentialsFromPoll(
        userServerUrl: String,
        pollServer: String,
        loginName: String,
        appPassword: String,
    ): AccountCredentials {
        val userBase = normalize(userServerUrl)
        val pollBase = normalize(pollServer)
        val server = if (hostsMatch(userBase, pollBase)) {
            pollBase
        } else {
            userBase
        }
        return AccountCredentials(
            server = server,
            loginName = loginName,
            appPassword = appPassword,
        )
    }

    private fun hasScheme(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    private fun hasNextcloudSubdomain(host: String): Boolean {
        val first = host.substringBefore('.')
        return first == "nextcloud" || first == "nc"
    }

    private fun isIpOrLocalHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return true
        // Uri drops the brackets, so a colon means IPv6.
        if (host.contains(':')) return true
        return false
    }

    private fun hostsMatch(a: String, b: String): Boolean {
        val hostA = Uri.parse(a).host?.lowercase()
        val hostB = Uri.parse(b).host?.lowercase()
        return hostA != null && hostA == hostB
    }
}
