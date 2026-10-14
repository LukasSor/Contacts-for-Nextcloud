package com.nextcloud.contacts.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LoginFlowService(private val httpClient: OkHttpClient) {

    data class LoginSession(
        val loginUrl: String,
        val pollUrl: String,
        val pollToken: String,
        val resolvedServerUrl: String,
    )

    suspend fun startLogin(serverUrl: String): LoginSession = withContext(Dispatchers.IO) {
        val candidates = ServerUrlNormalizer.candidates(serverUrl)
        if (candidates.isEmpty()) {
            error("Enter your Nextcloud URL")
        }

        var lastError: Exception? = null
        for (base in candidates) {
            try {
                return@withContext startLoginAt(base)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Cannot reach Nextcloud server")
    }

    private fun startLoginAt(base: String): LoginSession {
        val request = Request.Builder()
            .url("$base/index.php/login/v2")
            .post(FormBody.Builder().build())
            .header("User-Agent", USER_AGENT)
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            error("Failed to start login: HTTP ${response.code}")
        }
        val body = response.body?.string() ?: error("Empty login response")
        val json = JSONObject(body)
        return LoginSession(
            loginUrl = json.getString("login"),
            pollUrl = json.getJSONObject("poll").getString("endpoint"),
            pollToken = json.getJSONObject("poll").getString("token"),
            resolvedServerUrl = base,
        )
    }

    suspend fun pollForCredentials(
        pollUrl: String,
        pollToken: String,
        timeoutMs: Long = 20 * 60 * 1000,
    ): AccountCredentials = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var networkErrors = 0
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val request = Request.Builder()
                    .url(pollUrl)
                    .post(FormBody.Builder().add("token", pollToken).build())
                    .header("User-Agent", USER_AGENT)
                    .build()
                val response = httpClient.newCall(request).execute()
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: error("Empty poll response")
                        val json = JSONObject(body)
                        return@withContext AccountCredentials(
                            server = json.getString("server").trimEnd('/'),
                            loginName = json.getString("loginName"),
                            appPassword = json.getString("appPassword"),
                        )
                    }
                    404 -> {
                        networkErrors = 0
                        delay(1000)
                    }
                    else -> error("Login poll failed: HTTP ${response.code}")
                }
            } catch (e: IOException) {
                networkErrors++
                if (networkErrors >= 30) throw e
                delay(2000)
            }
        }
        error("Login timed out")
    }

    companion object {
        private const val USER_AGENT = "NextcloudContacts/1.0 (Android)"

        fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }
            .build()
    }
}
