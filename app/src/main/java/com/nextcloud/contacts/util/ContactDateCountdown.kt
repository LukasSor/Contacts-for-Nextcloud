package com.nextcloud.contacts.util

import android.content.Context
import com.nextcloud.contacts.R
import java.time.Duration
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class ContactDateCountdownField {
    BIRTHDAY,
    ANNIVERSARY,
    DEATH,
    ;

    val titleRes: Int
        get() = when (this) {
            BIRTHDAY -> R.string.field_birthday
            ANNIVERSARY -> R.string.field_anniversary
            DEATH -> R.string.field_date_of_death
        }
}

data class ParsedContactDate(
    val month: Int,
    val day: Int,
    val year: Int?,
)

fun parseContactDateForCountdown(raw: String): ParsedContactDate? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    val isoPartial = Regex("""^--(\d{2})-(\d{2})$""").matchEntire(value)
    if (isoPartial != null) {
        return ParsedContactDate(
            month = isoPartial.groupValues[1].toInt(),
            day = isoPartial.groupValues[2].toInt(),
            year = null,
        )
    }

    parseIsoOrBasicLocalDate(value)?.let { date ->
        return ParsedContactDate(date.monthValue, date.dayOfMonth, date.year)
    }

    return null
}

fun canOpenContactDateCountdown(raw: String): Boolean =
    parseContactDateForCountdown(raw) != null

fun resolveCountdownTarget(
    parsed: ParsedContactDate,
    field: ContactDateCountdownField,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: ZonedDateTime = ZonedDateTime.now(zoneId),
): ZonedDateTime {
    val today = now.toLocalDate()
    return when (field) {
        ContactDateCountdownField.BIRTHDAY,
        ContactDateCountdownField.ANNIVERSARY,
        -> nextYearlyOccurrenceTarget(parsed.month, parsed.day, now)

        ContactDateCountdownField.DEATH -> {
            val fullDate = parsed.year?.let { year ->
                contactDateInYear(year, parsed.month, parsed.day)
            }
            if (fullDate != null && fullDate.isAfter(today)) {
                fullDate.atStartOfDay(zoneId)
            } else {
                nextYearlyOccurrenceTarget(parsed.month, parsed.day, now)
            }
        }
    }
}

/**
 * Next yearly occurrence: start of that calendar day, or start of tomorrow when today is the day
 * ("later today"), or next year if this year's day has already passed.
 */
fun nextYearlyOccurrenceTarget(
    month: Int,
    day: Int,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
): ZonedDateTime {
    val zone = now.zone
    val today = now.toLocalDate()
    val thisYearDate = contactDateInYear(today.year, month, day)

    return when {
        thisYearDate.isAfter(today) ->
            thisYearDate.atStartOfDay(zone)

        thisYearDate.isEqual(today) ->
            today.plusDays(1).atStartOfDay(zone)

        else -> {
            val nextYearDate = contactDateInYear(today.year + 1, month, day)
            nextYearDate.atStartOfDay(zone)
        }
    }
}

fun contactDateInYear(year: Int, month: Int, day: Int): LocalDate {
    if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
        return LocalDate.of(year, 2, 28)
    }
    return runCatching { LocalDate.of(year, month, day) }.getOrElse {
        val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
        LocalDate.of(year, month, minOf(day, maxDay))
    }
}

fun formatContactDateLabel(parsed: ParsedContactDate, locale: Locale = Locale.getDefault()): String {
    val sampleYear = parsed.year ?: 2000
    val date = contactDateInYear(sampleYear, parsed.month, parsed.day)
    return if (parsed.year != null) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(locale)
            .format(date.withYear(parsed.year))
    } else {
        DateTimeFormatter.ofPattern("d MMMM", locale).format(date)
    }
}

data class CountdownParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
)

fun countdownPartsUntil(target: ZonedDateTime, now: ZonedDateTime): CountdownParts {
    var duration = Duration.between(now, target)
    if (duration.isNegative) {
        duration = Duration.ZERO
    }
    val days = duration.toDays()
    duration = duration.minusDays(days)
    val hours = duration.toHours()
    duration = duration.minusHours(hours)
    val minutes = duration.toMinutes()
    duration = duration.minusMinutes(minutes)
    val seconds = duration.seconds
    return CountdownParts(days, hours, minutes, seconds)
}

fun formatCountdownPrimary(context: Context, parts: CountdownParts): String {
    return if (parts.days > 0L) {
        val count = parts.days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        context.resources.getQuantityString(R.plurals.countdown_days, count, count)
    } else {
        String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            parts.hours,
            parts.minutes,
            parts.seconds,
        )
    }
}

fun formatCountdownSecondary(parts: CountdownParts): String? {
    return if (parts.days > 0L) {
        String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            parts.hours,
            parts.minutes,
            parts.seconds,
        )
    } else {
        null
    }
}
