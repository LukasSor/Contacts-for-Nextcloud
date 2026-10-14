package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.auth.LoginFlowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Talk Spreed OCS API (1:1 get-or-create room).
 */
class NextcloudTalkService(
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

    suspend fun getOrCreateOneToOneRoom(inviteUserId: String): Result<String> = withContext(Dispatchers.IO) {
        val userId = inviteUserId.trim()
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing user id"))
        }
        runCatching {
            val base = credentials.server.trimEnd('/')
            val url = "$base/ocs/v2.php/apps/spreed/api/v4/room"
            val body = FormBody.Builder()
                .add("roomType", "1")
                .add("invite", userId)
                .build()
            val request = Request.Builder().url(url).post(body).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (response.code != 200 && response.code != 201) {
                error("Talk room request failed: HTTP ${response.code}")
            }
            val ocs = JSONObject(responseBody).optJSONObject("ocs")
            val statusCode = ocs?.optJSONObject("meta")?.optInt("statuscode", -1) ?: -1
            if (statusCode != 200 && statusCode != 201) {
                error("Talk room request failed: OCS $statusCode")
            }
            val token = ocs?.optJSONObject("data")?.optString("token")?.trim().orEmpty()
            if (token.isBlank()) {
                error("Talk room response missing token")
            }
            token
        }
    }
}
