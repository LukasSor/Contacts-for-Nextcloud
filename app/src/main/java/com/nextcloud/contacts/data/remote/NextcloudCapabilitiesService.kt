package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.auth.LoginFlowService
import okhttp3.Credentials
import okhttp3.Request
import org.json.JSONObject

/** OCS cloud capabilities (calendar app installed and enabled). */
class NextcloudCapabilitiesService(
    private val credentials: AccountCredentials,
) {
    private val httpClient = LoginFlowService.createHttpClient().newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "Authorization",
                        Credentials.basic(credentials.loginName, credentials.appPassword),
                    )
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()

    fun isCalendarAppEnabled(): Boolean = runCatching {
        val base = credentials.server.trimEnd('/')
        val url = "$base/ocs/v1.php/cloud/capabilities?format=json"
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use false
            val root = JSONObject(body)
            val meta = root.optJSONObject("ocs")?.optJSONObject("meta")
            val statusCode = meta?.optInt("statuscode", -1) ?: -1
            if (statusCode != 100) return@use false
            val capabilities = root.optJSONObject("ocs")
                ?.optJSONObject("data")
                ?.optJSONObject("capabilities")
                ?: return@use false
            val calendar = capabilities.optJSONObject("calendar") ?: return@use false
            if (calendar.has("enabled") && !calendar.optBoolean("enabled", true)) return@use false
            true
        }
    }.getOrDefault(false)
}
