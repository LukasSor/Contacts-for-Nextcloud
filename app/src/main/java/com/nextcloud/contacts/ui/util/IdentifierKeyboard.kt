package com.nextcloud.contacts.ui.util

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * Keyboard options for values a space would break: URLs, emails, and similar identifiers.
 * Turning off capitalization and autocorrect stops typical Android keyboards from inserting
 * a space after ".".
 */
object IdentifierKeyboard {
    val email: KeyboardOptions = options(KeyboardType.Email)
    val uri: KeyboardOptions = options(KeyboardType.Uri)

    fun options(keyboardType: KeyboardType): KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        keyboardType = keyboardType,
    )

    /**
     * IMPP stays on the normal keyboard unless the protocol is an address or URI,
     * or the typed value already is one.
     */
    fun forInstantMessage(type: String, value: String): KeyboardOptions = when {
        isAddressStyleIm(type) -> uri
        !isAddressOrUrl(value) -> KeyboardOptions.Default
        isEmailAddress(value) -> email
        else -> uri
    }

    /** Raw vCard value for identifier properties. Other properties keep the normal keyboard. */
    fun forVCardProperty(propertyName: String, type: String, value: String): KeyboardOptions =
        when (propertyName.trim().uppercase()) {
            "EMAIL" -> email
            "URL", "CLOUD", "X-SOCIALPROFILE" -> uri
            "IMPP" -> forInstantMessage(type, value)
            else -> KeyboardOptions.Default
        }

    private val addressStyleImTypes = setOf("sip", "xmpp", "matrix", "nextcloud")

    private fun isAddressStyleIm(type: String): Boolean =
        type.trim().lowercase() in addressStyleImTypes

    private fun isEmailAddress(value: String): Boolean {
        val trimmed = value.trim()
        if ('@' !in trimmed || trimmed.any { it.isWhitespace() }) return false
        if ("://" in trimmed) return false
        val scheme = trimmed.substringBefore(':', missingDelimiterValue = "")
        if (scheme.isNotEmpty() && scheme.all { it.isLetter() } && ':' in trimmed) return false
        return true
    }

    private fun isAddressOrUrl(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        if ("://" in trimmed || '@' in trimmed) return true
        val scheme = trimmed.substringBefore(':', missingDelimiterValue = "")
        return scheme.length in 2..16 && scheme.all { it.isLetter() } && ':' in trimmed
    }
}
