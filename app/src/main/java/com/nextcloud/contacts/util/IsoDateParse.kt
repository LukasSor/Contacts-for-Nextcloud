package com.nextcloud.contacts.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val BASIC_DATE = Regex("""\d{8}""")

/**
 * Full ISO date or yyyyMMdd only. Partial dates, year-month, and year stay with
 * the caller so picker, vCard, and countdown keep their own shapes.
 */
fun parseIsoOrBasicLocalDate(value: String): LocalDate? {
    return runCatching {
        when {
            value.matches(ISO_DATE) -> LocalDate.parse(value)
            value.matches(BASIC_DATE) -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
            else -> null
        }
    }.getOrNull()
}
