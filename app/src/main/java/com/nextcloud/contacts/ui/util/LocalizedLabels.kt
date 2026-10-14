package com.nextcloud.contacts.ui.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.LanguageOptions
import java.util.Locale

@Composable
fun localizedFieldLabel(options: List<ContactFieldOptions.Option>, id: String): String {
    val match = ContactFieldOptions.matchOption(options, id)
    if (match != null) return match.visibleLabel()
    val fallback = id.replace('_', ' ').replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(displayLocale()) else it.toString()
    }
    return fallback.ifBlank { stringResource(R.string.type_other) }
}

@Composable
fun localizedTypedFieldLabel(
    options: List<ContactFieldOptions.Option>,
    type: String,
    vcardTypeParam: String?,
): String {
    val param = vcardTypeParam?.trim().orEmpty()
    val id = if (param.isNotBlank()) param.split(',').first().trim() else type
    return localizedFieldLabel(options, id)
}

@Composable
fun localizedLanguageName(code: String): String = LanguageOptions.label(code, displayLocale())

@Composable
fun localizedUrlLabel(type: String): String {
    if (type.isBlank() || type.equals("website", ignoreCase = true)) {
        return stringResource(R.string.field_website)
    }
    return localizedFieldLabel(ContactFieldOptions.urls, type)
}

@Composable
fun ContactFieldOptions.Option.visibleLabel(): String {
    if (labelRes != 0) return stringResource(labelRes)
    return literalVisibleLabel(this, displayLocale())
}

fun ContactFieldOptions.Option.visibleLabel(context: Context): String {
    if (labelRes != 0) return context.getString(labelRes)
    return literalVisibleLabel(this, context.resources.configuration.primaryLocale())
}

private fun literalVisibleLabel(option: ContactFieldOptions.Option, locale: Locale): String {
    if (option.id.isBlank() || option.id.contains('/')) return option.label
    val exact = LanguageOptions.all.firstOrNull { it.id.equals(option.id, ignoreCase = true) }
        ?: return option.label
    if (exact.label != option.label) return option.label
    return LanguageOptions.label(option.id, locale)
}

@Composable
private fun displayLocale(): Locale = LocalConfiguration.current.primaryLocale()

private fun Configuration.primaryLocale(): Locale =
    if (locales.isEmpty) Locale.getDefault() else locales[0]
