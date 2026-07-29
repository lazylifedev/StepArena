package com.lazyapps.steparena.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object StepArenaTimeFormatter {
    fun time(
        instant: Instant,
        zoneId: ZoneId,
        locale: Locale,
        use24Hour: Boolean,
    ): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zoneId).format(instant)
    }

    fun dateTime(instant: Instant, zoneId: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zoneId)
            .format(instant)
}
