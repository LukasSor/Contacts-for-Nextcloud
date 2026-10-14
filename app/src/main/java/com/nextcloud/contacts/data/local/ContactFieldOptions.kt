package com.nextcloud.contacts.data.local

import android.content.Context
import androidx.annotation.StringRes
import com.nextcloud.contacts.R

/** Type / select options aligned with Nextcloud Contacts web UI. */
object ContactFieldOptions {
    data class Option(
        val id: String,
        val label: String,
        @StringRes val labelRes: Int = 0,
    ) {
        fun resolve(context: Context): String =
            if (labelRes != 0) context.getString(labelRes) else label
    }

    val phones = listOf(
        Option("home", "Home", R.string.type_home),
        Option("work", "Work", R.string.type_work),
        Option("mobile", "Mobile", R.string.type_mobile),
        Option("work_mobile", "Work mobile", R.string.type_work_mobile),
        Option("home_mobile", "Home mobile", R.string.type_home_mobile),
        Option("fax", "Fax", R.string.type_fax),
        Option("fax_home", "Fax home", R.string.type_fax_home),
        Option("fax_work", "Fax work", R.string.type_fax_work),
        Option("pager", "Pager", R.string.type_pager),
        Option("voice", "Voice", R.string.type_voice),
        Option("car", "Car", R.string.type_car),
        Option("work_pager", "Work pager", R.string.type_work_pager),
        Option("other", "Other", R.string.type_other),
    )

    val emails = listOf(
        Option("home", "Home", R.string.type_home),
        Option("work", "Work", R.string.type_work),
        Option("other", "Other", R.string.type_other),
    )

    val addresses = listOf(
        Option("home", "Home", R.string.type_home),
        Option("work", "Work", R.string.type_work),
        Option("other", "Other", R.string.type_other),
    )

    /**
     * Website-oriented types (not Home/Work/Other like phone/email).
     * Blank id matches Nextcloud web default (no TYPE → readable name "Website").
     */
    val urls = listOf(
        Option("", "Website", R.string.field_website),
        Option("homepage", "Homepage", R.string.type_homepage),
        Option("work", "Work", R.string.type_work),
        Option("blog", "Blog", R.string.type_blog),
        Option("profile", "Profile", R.string.type_profile),
        Option("portfolio", "Portfolio", R.string.type_portfolio),
        Option("other", "Other", R.string.type_other),
    )

    val cloudIds = listOf(
        Option("home", "Home", R.string.type_home),
        Option("work", "Work", R.string.type_work),
        Option("other", "Other", R.string.type_other),
    )

    val im = listOf(
        Option("discord", "Discord"),
        Option("irc", "IRC"),
        Option("kakaotalk", "KakaoTalk"),
        Option("kik", "KiK"),
        Option("line", "Line"),
        Option("matrix", "Matrix"),
        Option("nextcloud", "Nextcloud"),
        Option("qq", "QQ"),
        Option("signal", "Signal"),
        Option("sip", "SIP"),
        Option("telegram", "Telegram"),
        Option("threema", "Threema"),
        Option("wechat", "WeChat"),
        Option("whatsapp", "WhatsApp"),
        Option("xmpp", "XMPP"),
        Option("zoom", "Zoom"),
        Option("other", "Other", R.string.type_other),
    )

    val social = listOf(
        Option("facebook", "Facebook"),
        Option("github", "GitHub"),
        Option("instagram", "Instagram"),
        Option("linkedin", "LinkedIn"),
        Option("xing", "Xing"),
        Option("pinterest", "Pinterest"),
        Option("qzone", "QZone"),
        Option("tumblr", "Tumblr"),
        Option("twitter", "Twitter"),
        Option("wechat", "WeChat"),
        Option("youtube", "YouTube"),
        Option("mastodon", "Mastodon"),
        Option("diaspora", "Diaspora"),
        Option("nextcloud", "Nextcloud"),
        Option("other", "Other", R.string.type_other),
    )

    val related = listOf(
        Option("contact", "Contact", R.string.type_contact),
        Option("agent", "Agent", R.string.type_agent),
        Option("emergency", "Emergency", R.string.type_emergency),
        Option("friend", "Friend", R.string.type_friend),
        Option("colleague", "Colleague", R.string.type_colleague),
        Option("coworker", "Co-worker", R.string.type_coworker),
        Option("manager", "Line manager", R.string.type_manager),
        Option("assistant", "Assistant", R.string.type_assistant),
        Option("spouse", "Spouse", R.string.type_spouse),
        Option("child", "Child", R.string.type_child),
        Option("mother", "Mother", R.string.type_mother),
        Option("father", "Father", R.string.type_father),
        Option("parent", "Parent", R.string.type_parent),
        Option("brother", "Brother", R.string.type_brother),
        Option("sister", "Sister", R.string.type_sister),
        Option("relative", "Relative", R.string.type_relative),
        Option("other", "Other", R.string.type_other),
    )

    val gender = listOf(
        Option("", "—", R.string.label_dash),
        Option("F", "Female", R.string.type_female),
        Option("M", "Male", R.string.type_male),
        Option("O", "Other", R.string.type_other),
        Option("N", "None", R.string.type_none),
        Option("U", "Unknown", R.string.type_unknown),
    )

    val relationshipToYou = listOf(
        Option("", "—", R.string.label_dash),
        Option("spouse", "Spouse", R.string.type_spouse),
        Option("child", "Child", R.string.type_child),
        Option("mother", "Mother", R.string.type_mother),
        Option("father", "Father", R.string.type_father),
        Option("parent", "Parent", R.string.type_parent),
        Option("brother", "Brother", R.string.type_brother),
        Option("sister", "Sister", R.string.type_sister),
        Option("relative", "Relative", R.string.type_relative),
        Option("friend", "Friend", R.string.type_friend),
        Option("colleague", "Colleague", R.string.type_colleague),
        Option("manager", "Line manager", R.string.type_manager),
        Option("assistant", "Assistant", R.string.type_assistant),
    )

    fun label(context: Context, options: List<Option>, id: String): String {
        matchOption(options, id)?.let { return it.resolve(context) }
        val locale = context.resources.configuration.locales[0]
        val fallback = id.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
        return fallback.ifBlank { context.getString(R.string.type_other) }
    }

    /** Prefer vCard TYPE parameter text for detail labels when the stored type id is generic. */
    fun typedFieldLabel(
        context: Context,
        options: List<Option>,
        type: String,
        vcardTypeParam: String?,
    ): String {
        val param = vcardTypeParam?.trim().orEmpty()
        if (param.isNotBlank()) {
            return label(context, options, param.split(',').first().trim())
        }
        return label(context, options, type)
    }

    /** Blank / website TYPE → "Website" (matches Nextcloud web); legacy home → Homepage. */
    fun urlLabel(context: Context, type: String): String {
        if (type.isBlank() || type.equals("website", ignoreCase = true)) {
            return context.getString(R.string.field_website)
        }
        return label(context, urls, type)
    }

    /**
     * Map legacy / alternate URL type ids to a canonical option id.
     * Blank and `website` → blank (default Website, no TYPE on write).
     * Legacy `home` → `homepage`.
     */
    fun canonicalUrlType(raw: String): String {
        if (raw.isBlank()) return ""
        val normalized = normalizeType(raw)
        return when (normalized) {
            "website" -> ""
            "home" -> "homepage"
            else -> normalized
        }
    }

    fun matchOption(options: List<Option>, id: String): Option? {
        if (id.isBlank()) return options.firstOrNull { it.id.isBlank() }
        options.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
        options.firstOrNull { it.id == normalizeType(id) }?.let { return it }
        // Language / locale tags may use either underscore or hyphen separators.
        val hyphenated = id.lowercase().replace('_', '-')
        if (hyphenated != id.lowercase()) {
            options.firstOrNull { it.id.equals(hyphenated, ignoreCase = true) }?.let { return it }
        }
        // URL: blank/website → Website; legacy HOME → Homepage
        if (options === urls) {
            val canonical = canonicalUrlType(id)
            if (canonical.isBlank()) return options.firstOrNull { it.id.isBlank() }
            options.firstOrNull { it.id == canonical }?.let { return it }
        }
        return null
    }

    fun normalizeType(raw: String): String {
        val lower = raw.lowercase().replace(' ', '_').replace(',', '_')
        return when {
            lower == "cell" || lower == "cell_voice" -> "mobile"
            lower == "home_voice" -> "home"
            lower == "work_voice" -> "work"
            lower == "work_cell" -> "work_mobile"
            lower == "home_cell" -> "home_mobile"
            lower == "home_fax" -> "fax_home"
            lower == "work_fax" -> "fax_work"
            lower == "work_pager" -> "work_pager"
            lower.contains("mobile") && lower.contains("work") -> "work_mobile"
            lower.contains("mobile") && lower.contains("home") -> "home_mobile"
            lower.contains("fax") && lower.contains("home") -> "fax_home"
            lower.contains("fax") && lower.contains("work") -> "fax_work"
            lower.contains("cell") -> "mobile"
            lower.contains("fax") -> "fax"
            lower.contains("pager") && lower.contains("work") -> "work_pager"
            lower.contains("pager") -> "pager"
            lower.contains("car") -> "car"
            lower.contains("voice") && !lower.contains("home") && !lower.contains("work") -> "voice"
            else -> lower.trim('_').ifBlank { "other" }
        }
    }
}
