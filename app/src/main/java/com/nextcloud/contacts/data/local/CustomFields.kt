package com.nextcloud.contacts.data.local

const val DEFAULT_CUSTOM_FIELD_PROPERTY = "X-NCCONTACTS-FIELD"

/** vCard property names that must not be used for custom fields (built-in rows own these). */
val RESERVED_CUSTOM_FIELD_PROPERTY_NAMES: Set<String> = setOf(
    "ADR",
    "ANNIVERSARY",
    "BDAY",
    "BIRTHPLACE",
    "CATEGORIES",
    "CLOUD",
    "DEATHDATE",
    "EMAIL",
    "FN",
    "GENDER",
    "GEO",
    "IMPP",
    "LANG",
    "N",
    "NICKNAME",
    "NOTE",
    "ORG",
    "PHOTO",
    "RELATED",
    "ROLE",
    "TEL",
    "TITLE",
    "TZ",
    "UID",
    "URL",
)

data class CustomFieldInput(
    val label: String = "",
    val value: String = "",
    val propertyName: String = DEFAULT_CUSTOM_FIELD_PROPERTY,
    val propertyGroup: String = "",
    val typeParam: String = "",
    val extraParameters: List<Pair<String, String>> = emptyList(),
) {
    fun normalizedPropertyName(): String {
        val trimmed = propertyName.trim().ifBlank { DEFAULT_CUSTOM_FIELD_PROPERTY }
        return trimmed.uppercase()
    }

    fun sanitizePropertyName(): CustomFieldInput {
        val upper = normalizedPropertyName()
        return if (upper in RESERVED_CUSTOM_FIELD_PROPERTY_NAMES) {
            copy(propertyName = DEFAULT_CUSTOM_FIELD_PROPERTY)
        } else {
            copy(propertyName = upper)
        }
    }

    fun hasPersistableContent(): Boolean {
        if (value.isNotBlank()) return true
        if (label.isNotBlank()) return true
        if (typeParam.isNotBlank()) return true
        if (propertyGroup.isNotBlank()) return true
        if (extraParameters.any { it.first.isNotBlank() || it.second.isNotBlank() }) return true
        if (normalizedPropertyName() != DEFAULT_CUSTOM_FIELD_PROPERTY) return true
        return false
    }

    fun detailLabel(): String? =
        label.ifBlank { null } ?: typeParam.takeIf { it.isNotBlank() }
}

fun CustomFieldEntity.toInput(): CustomFieldInput = CustomFieldInput(
    label = label,
    value = value,
    propertyName = propertyName,
    propertyGroup = propertyGroup.orEmpty(),
    typeParam = typeParam.orEmpty(),
    extraParameters = CustomFieldExtras.decode(extraParametersJson),
)

fun CustomFieldInput.toEntity(contactId: Long): CustomFieldEntity {
    val sanitized = sanitizePropertyName()
    return CustomFieldEntity(
        contactId = contactId,
        label = label.trim(),
        value = value,
        propertyName = sanitized.normalizedPropertyName(),
        propertyGroup = sanitized.propertyGroup.trim().ifBlank { null },
        typeParam = sanitized.typeParam.trim().ifBlank { null },
        extraParametersJson = CustomFieldExtras.encode(sanitized.extraParameters),
    )
}

object CustomFieldExtras {
    private const val RECORD_SEP = '\u001d'
    private const val FIELD_SEP = '\u001e'

    fun encode(params: List<Pair<String, String>>): String {
        val items = params.filter { it.first.isNotBlank() || it.second.isNotBlank() }
        if (items.isEmpty()) return ""
        return items.joinToString(RECORD_SEP.toString()) { (name, value) ->
            "${escape(name)}$FIELD_SEP${escape(value)}"
        }
    }

    fun decode(raw: String): List<Pair<String, String>> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { segment ->
            val parts = segment.split(FIELD_SEP, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            unescape(parts[0]) to unescape(parts[1])
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                RECORD_SEP -> append("\\d")
                FIELD_SEP -> append("\\e")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'd' -> append(RECORD_SEP)
                    'e' -> append(FIELD_SEP)
                    '\\' -> append('\\')
                    else -> {
                        append('\\')
                        append(value[index + 1])
                    }
                }
                index += 2
            } else {
                append(ch)
                index++
            }
        }
    }
}

fun customFieldMergeKey(field: CustomFieldEntity): String = listOf(
    field.propertyName,
    field.propertyGroup.orEmpty(),
    field.typeParam.orEmpty(),
    field.label.trim(),
    field.value.trim(),
    field.extraParametersJson,
).joinToString("\u0000")
