package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.data.local.AddressInput
import com.nextcloud.contacts.auth.LoginFlowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PlaceSuggestion(
    val id: String,
    val label: String,
    val street: String = "",
    val extendedAddress: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    /** WGS-84 latitude from Photon `geometry.coordinates[1]`. */
    val latitude: Double? = null,
    /** WGS-84 longitude from Photon `geometry.coordinates[0]`. */
    val longitude: Double? = null,
) {
    fun hasCoordinates(): Boolean = latitude != null && longitude != null
    fun toAddressInput(type: String, poBox: String, existingGeo: String = ""): AddressInput {
        val geo = if (hasCoordinates()) {
            VCardMapper.formatGeoValue(latitude!!, longitude!!)
        } else {
            existingGeo
        }
        return AddressInput(
            type = type,
            poBox = poBox,
            extendedAddress = extendedAddress,
            street = street,
            city = city,
            region = region,
            postalCode = postalCode,
            country = country,
            geo = geo,
        )
    }

    fun toSingleLinePlace(): String = label.ifBlank {
        listOf(street, city, region, postalCode, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    fun hasDetailFields(): Boolean =
        extendedAddress.isNotBlank() || city.isNotBlank() || region.isNotBlank() ||
            postalCode.isNotBlank() || country.isNotBlank()
}

class PhotonGeocodingService(
    private val httpClient: OkHttpClient = LoginFlowService.createHttpClient(),
) {
    suspend fun search(baseUrl: String, query: String, limit: Int = 8): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val normalizedBase = normalizeBaseUrl(baseUrl)
            val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
            val url = "$normalizedBase/api?q=$encodedQuery&limit=$limit"
            if (url.toHttpUrlOrNull() == null) {
                throw IllegalArgumentException("Invalid Photon URL")
            }

            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Photon returned ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseResponse(body)
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://photon.komoot.io"

        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()
            if (url.isEmpty()) return DEFAULT_BASE_URL
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                url = "https://$url"
            }
            return url.trimEnd('/')
        }

        private fun parseResponse(body: String): List<PlaceSuggestion> {
            if (body.isBlank()) return emptyList()
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return emptyList()
            val results = ArrayList<PlaceSuggestion>(features.length())
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val suggestion = props.toPlaceSuggestion(
                    featureId = feature.optString("id", "$i"),
                    geometry = feature.optJSONObject("geometry"),
                )
                if (suggestion != null) {
                    results.add(suggestion)
                }
            }
            return results
        }

        private fun JSONObject.toPlaceSuggestion(
            featureId: String,
            geometry: JSONObject?,
        ): PlaceSuggestion? {
            val streetName = optString("street").trim()
            val housenumber = optString("housenumber").trim()
            val name = optString("name").trim()
            val street = when {
                streetName.isNotEmpty() && housenumber.isNotEmpty() -> "$streetName $housenumber"
                streetName.isNotEmpty() -> streetName
                name.isNotEmpty() -> name
                else -> ""
            }
            val city = sequenceOf("city", "town", "village", "locality", "municipality")
                .map { optString(it).trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            val region = sequenceOf("state", "county", "region")
                .map { optString(it).trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            val postalCode = optString("postcode").trim()
            val country = optString("country").trim()
            val district = optString("district").trim()
            val label = buildLabel(
                name = name,
                street = street,
                city = city,
                region = region,
                postalCode = postalCode,
                country = country,
            )
            if (label.isBlank() && street.isBlank() && city.isBlank()) return null
            val stableId = listOf(
                featureId,
                street,
                city,
                postalCode,
                country,
                name,
            ).joinToString("|")
            val coordinates = geometry?.optJSONArray("coordinates")
            val longitude = coordinates?.optDouble(0)?.takeUnless { it.isNaN() }
            val latitude = coordinates?.optDouble(1)?.takeUnless { it.isNaN() }

            return PlaceSuggestion(
                id = stableId,
                label = label,
                street = street,
                extendedAddress = district,
                city = city,
                region = region,
                postalCode = postalCode,
                country = country,
                latitude = latitude,
                longitude = longitude,
            )
        }

        private fun buildLabel(
            name: String,
            street: String,
            city: String,
            region: String,
            postalCode: String,
            country: String,
        ): String {
            val primary = when {
                street.isNotBlank() && name.isNotBlank() && !street.contains(name, ignoreCase = true) ->
                    "$street, $name"
                street.isNotBlank() -> street
                name.isNotBlank() -> name
                else -> ""
            }
            val locality = listOf(postalCode, city, region, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            return when {
                primary.isNotBlank() && locality.isNotBlank() -> "$primary — $locality"
                primary.isNotBlank() -> primary
                else -> locality
            }
        }
    }
}
