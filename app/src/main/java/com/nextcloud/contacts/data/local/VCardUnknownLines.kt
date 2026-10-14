package com.nextcloud.contacts.data.local

import org.json.JSONArray
import org.json.JSONObject

/** One vCard property line preserved across sync when not mapped to the app model. */
data class StoredVCardLine(
    val propertyName: String,
    val group: String = "",
    val value: String = "",
    val parameters: List<Pair<String, String>> = emptyList(),
) {
    fun fingerprint(): String = VCardUnknownLines.fingerprint(group, propertyName, value, parameters)
}

object VCardUnknownLines {
    fun decode(raw: String): List<StoredVCardLine> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val paramsArray = obj.optJSONArray("params") ?: JSONArray()
                    val params = buildList {
                        for (paramIndex in 0 until paramsArray.length()) {
                            val param = paramsArray.optJSONObject(paramIndex) ?: continue
                            val name = param.optString("name")
                            val value = param.optString("value")
                            if (name.isNotBlank()) add(name to value)
                        }
                    }
                    add(
                        StoredVCardLine(
                            propertyName = obj.optString("name"),
                            group = obj.optString("group"),
                            value = obj.optString("value"),
                            parameters = params,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encode(lines: List<StoredVCardLine>): String {
        if (lines.isEmpty()) return ""
        val array = JSONArray()
        lines.forEach { line ->
            if (line.propertyName.isBlank()) return@forEach
            val params = JSONArray()
            line.parameters.forEach { (name, value) ->
                if (name.isBlank()) return@forEach
                params.put(JSONObject().put("name", name).put("value", value))
            }
            array.put(
                JSONObject()
                    .put("name", line.propertyName)
                    .put("group", line.group)
                    .put("value", line.value)
                    .put("params", params),
            )
        }
        return if (array.length() == 0) "" else array.toString()
    }

    fun fingerprint(
        group: String,
        propertyName: String,
        value: String,
        parameters: List<Pair<String, String>>,
    ): String {
        val sortedParams = parameters
            .filter { it.first.isNotBlank() }
            .sortedWith(compareBy({ it.first.uppercase() }, { it.second }))
            .joinToString("\u0001") { "${it.first.uppercase()}\u0002${it.second}" }
        return listOf(
            group.trim().uppercase(),
            propertyName.trim().uppercase(),
            value,
            sortedParams,
        ).joinToString("\u0000")
    }
}
