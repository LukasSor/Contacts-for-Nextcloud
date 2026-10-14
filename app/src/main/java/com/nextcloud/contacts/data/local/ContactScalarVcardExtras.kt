package com.nextcloud.contacts.data.local

import org.json.JSONObject

/** vCard syntax metadata for single-value contact fields (NOTE, N, ORG, …). */
object ContactScalarVcardExtras {
    fun decode(raw: String): Map<String, VCardSyntaxExtras> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { key ->
                    val obj = root.optJSONObject(key) ?: return@forEach
                    put(
                        key.uppercase(),
                        VCardSyntaxExtras(
                            propertyGroup = obj.optString("group"),
                            typeParam = obj.optString("type"),
                            extraParameters = CustomFieldExtras.decode(obj.optString("extra")),
                            abLabel = obj.optString("abLabel"),
                            rawValue = obj.optString("raw"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(map: Map<String, VCardSyntaxExtras>): String {
        val filtered = map.filterValues { it.hasPersistableContent() }
        if (filtered.isEmpty()) return ""
        val root = JSONObject()
        filtered.forEach { (key, extras) ->
            root.put(
                key.uppercase(),
                JSONObject()
                    .put("group", extras.propertyGroup)
                    .put("type", extras.typeParam)
                    .put("extra", CustomFieldExtras.encode(extras.extraParameters))
                    .put("abLabel", extras.abLabel)
                    .put("raw", extras.rawValue),
            )
        }
        return root.toString()
    }

    private fun VCardSyntaxExtras.hasPersistableContent(): Boolean =
        propertyGroup.isNotBlank() ||
            typeParam.isNotBlank() ||
            abLabel.isNotBlank() ||
            rawValue.isNotBlank() ||
            extraParameters.any { it.first.isNotBlank() || it.second.isNotBlank() }
}
