package dev.ipf.whitenoise.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class IdentityFormatterTest {
    @Test
    fun shortNeverReturnsStringLongerThanInput() {
        // Regression for #377: with the default 8/4 split, an ellipsis is 3
        // characters, so any abbreviated form is `prefix + 3 + suffix = 15`
        // chars. Inputs of length 14 or 15 used to expand instead of shrink
        // because the guard counted the ellipsis as a single char.
        for (length in 0..40) {
            val input = "a".repeat(length)
            val shortened = IdentityFormatter.short(input)
            assertTrue(
                "short($length-char input) returned ${shortened.length} chars: $shortened",
                shortened.length <= input.length,
            )
        }
    }

    @Test
    fun shortReturnsInputUnchangedWhenAbbreviationWouldNotShorten() {
        // 8 + 3 (ellipsis) + 4 = 15. Inputs of length 15 or less must round-trip.
        assertEquals("a".repeat(15), IdentityFormatter.short("a".repeat(15)))
        assertEquals("a".repeat(14), IdentityFormatter.short("a".repeat(14)))
        assertEquals("a".repeat(13), IdentityFormatter.short("a".repeat(13)))
    }

    @Test
    fun shortAbbreviatesInputsLongerThanPrefixSuffixAndEllipsis() {
        // 16-char input is the first length where abbreviation is a real win.
        val input = "abcdefghIJKLMNOP"
        assertEquals("abcdefgh...MNOP", IdentityFormatter.short(input))
    }

    @Test
    fun farFutureTimestampUsesExplicitLabel() {
        val tomorrow = (Instant.now().epochSecond + 86_400L).toULong()

        assertEquals("future", IdentityFormatter.relativeTime(tomorrow))
    }

    @Test
    fun slightlyAheadTimestampReadsAsNowNotFuture() {
        // Clock skew shouldn't render the literal "future".
        val skewedAhead = (Instant.now().epochSecond + 5L).toULong()

        assertEquals("now", IdentityFormatter.relativeTime(skewedAhead))
    }

    @Test
    fun initialsTakeLeadingCodePointFromEachWord() {
        // Latin smoke test: the existing two-word path still works.
        assertEquals("AB", IdentityFormatter.initials("alice bobson"))
    }

    @Test
    fun initialsDropEmojiWordInFavorOfLetter() {
        // #427 supersedes the old "render the emoji whole" behavior: a letter
        // always wins over an emoji/symbol grapheme, since an emoji alone in the
        // avatar circle clips or shows as tofu. The #112 concern (never emit a
        // lone surrogate half) still holds — the emoji is dropped, not split.
        val grinningFace = String(Character.toChars(0x1F600))

        assertEquals("B", IdentityFormatter.initials("$grinningFace bob"))
        assertEquals("A", IdentityFormatter.initials("Alice $grinningFace"))
        assertEquals("A", IdentityFormatter.initials("$grinningFace Alice"))
    }

    @Test
    fun initialsForEmojiOnlyNameRenderFirstGraphemeWhole() {
        // No letters anywhere → fall back to the first emoji grapheme, taken
        // whole (not a split surrogate). #427.
        val grinningFace = String(Character.toChars(0x1F600))
        val fire = String(Character.toChars(0x1F525))

        assertEquals(grinningFace, IdentityFormatter.initials("$grinningFace$fire"))
    }

    @Test
    fun initialsSingleWordWithTrailingEmojiUsesLetters() {
        // A single word keeps the two-letter monogram from its letters; the
        // trailing emoji is simply never reached (#427). Deliberately "BO", not
        // "B" — single-word names always yield up to two letters, matching the
        // existing "Xavier"-style behavior.
        val fire = String(Character.toChars(0x1F525))

        assertEquals("BO", IdentityFormatter.initials("Bob$fire"))
    }

    @Test
    fun initialsDropZwjEmojiWordInFavorOfLetter() {
        // A ZWJ emoji sequence (family) is a multi-codepoint grapheme; whether
        // or not the break iterator groups it, the letter word still wins. #427.
        val family = "👨‍👩‍👧"

        assertEquals("F", IdentityFormatter.initials("$family Family"))
    }

    @Test
    fun relativeTimeDoesNotCrashOnOutOfRangeTimestamps() {
        // #468: untrusted epoch values must not throw DateTimeException into the
        // render path. ULong.MAX_VALUE wraps to -1L and a high-bit value wraps to
        // Long.MIN_VALUE; both clamp to a safe instant instead of crashing.
        val zone = ZoneId.systemDefault()
        // Epoch 0 (1970) is well over a year ago, so the ladder renders the
        // locale SHORT date (2-digit year for US) with no time component.
        val epochZeroFormatted =
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(Locale.US)
                .format(Instant.ofEpochSecond(0).atZone(zone).toLocalDate())

        assertEquals(epochZeroFormatted, IdentityFormatter.relativeTime(ULong.MAX_VALUE, RelativeTimeCopy.Default, Locale.US))
        assertEquals(
            epochZeroFormatted,
            IdentityFormatter.relativeTime(0x8000000000000000uL, RelativeTimeCopy.Default, Locale.US),
        )
        // A positive value far past year 9999 clamps and still formats without throwing.
        val farFuture = IdentityFormatter.relativeTime(999_999_999_999uL, RelativeTimeCopy.Default, Locale.US)
        assertTrue(farFuture.isNotBlank())
    }

    @Test
    fun initialsTakeTwoNonBmpCodePointsFromOneWord() {
        // Single-word name made entirely of non-BMP code points: both initials
        // must arrive whole. Pre-fix this would split a surrogate pair.
        val mathBoldX = String(Character.toChars(0x1D54F))
        val mathBoldA = String(Character.toChars(0x1D400))
        val word = mathBoldX + mathBoldA + "vier"
        val expected = (mathBoldX + mathBoldA).uppercase()

        assertEquals(expected, IdentityFormatter.initials(word))
    }

    @Test
    fun initialsFallBackForBlankInput() {
        assertEquals("DM", IdentityFormatter.initials(""))
        assertEquals("DM", IdentityFormatter.initials("   "))
    }

    @Test
    fun relativeTimeShowsCompactHoursWithinTheFirstDay() {
        // Past an hour but inside 24h: compact "Nh", no clock time (#848).
        val twoHoursAgo = (Instant.now().epochSecond - 2 * 3_600L).toULong()

        assertEquals("2h", IdentityFormatter.relativeTime(twoHoursAgo, RelativeTimeCopy.Default, Locale.US))
    }

    @Test
    fun relativeTimePassesCountToPluralCallback() {
        // The unit callbacks must receive the integer count so a real
        // getQuantityString-backed callback can pick the correct plural form.
        val fortyFiveMinutesAgo = (Instant.now().epochSecond - (45 * 60L)).toULong()
        val copy =
            RelativeTimeCopy(
                future = "FUT",
                now = "NOW",
                yesterday = "YDAY",
                minutes = { count -> "min=$count" },
                hours = { count -> "hr=$count" },
            )

        assertEquals("min=45", IdentityFormatter.relativeTime(fortyFiveMinutesAgo, copy, Locale.US))
    }

    @Test
    fun relativeTimeShowsDateWithoutTimeForOlderInstants() {
        // 8 days ago is past the weekday window: locale month/day, no year, no
        // time. US renders month-first ("Jun 14").
        val eightDaysAgo = Instant.now().minusSeconds(8 * 86_400L)
        val zone = ZoneId.systemDefault()
        val date = eightDaysAgo.atZone(zone).toLocalDate()
        val expected = "${date.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${date.dayOfMonth}"

        assertEquals(expected, IdentityFormatter.relativeTime(eightDaysAgo.epochSecond.toULong(), RelativeTimeCopy.Default, Locale.US))
    }

    @Test
    fun relativeTimeShowsWeekdayWithinThePastWeek() {
        val threeDaysAgo = Instant.now().minusSeconds(3 * 86_400L)
        val zone = ZoneId.systemDefault()
        val expected =
            threeDaysAgo
                .atZone(zone)
                .toLocalDate()
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.US)

        assertEquals(expected, IdentityFormatter.relativeTime(threeDaysAgo.epochSecond.toULong(), RelativeTimeCopy.Default, Locale.US))
    }

    @Test
    fun relativeTimeShowsYesterdayForThePreviousDay() {
        // 26h ago is the previous calendar day for most wall-clock times; assert
        // against the day-distance the formatter computes so the test is
        // deterministic regardless of the hour it runs.
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val instant = now.minusSeconds(26 * 3_600L)
        val messageDate = instant.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(messageDate, now.atZone(zone).toLocalDate())
        val expected =
            if (days <= 1L) {
                "yesterday"
            } else {
                messageDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
            }

        assertEquals(expected, IdentityFormatter.relativeTime(instant.epochSecond.toULong(), RelativeTimeCopy.Default, Locale.US))
    }

    @Test
    fun relativeTimeShowsTwoDigitYearPastAYear() {
        // Past a year old: the locale-equivalent SHORT date, which for US uses a
        // 2-digit year (e.g. "6/14/25") and carries no clock component.
        val longAgo = Instant.now().minusSeconds(400 * 86_400L)
        val zone = ZoneId.systemDefault()
        val expected =
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(Locale.US)
                .format(longAgo.atZone(zone).toLocalDate())

        assertEquals(expected, IdentityFormatter.relativeTime(longAgo.epochSecond.toULong(), RelativeTimeCopy.Default, Locale.US))
    }

    // The locale-aware date rungs (#861) are exercised with fixed message/now
    // dates and an explicit `now` so the assertions are deterministic. Both
    // instants are anchored at noon in the system zone, so the day-distance the
    // formatter computes is stable regardless of the host time zone.
    private fun noonInstant(date: LocalDate): Instant = date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()

    private fun relativeTimeOn(
        messageDate: LocalDate,
        today: LocalDate,
        locale: Locale,
    ): String =
        IdentityFormatter.relativeTime(
            noonInstant(messageDate).epochSecond.toULong(),
            RelativeTimeCopy.Default,
            locale,
            noonInstant(today),
        )

    @Test
    fun relativeTimeNoYearRungIsLocaleOrderedWithoutClock() {
        // 8 days back stays in the no-year rung. US is month-first, German is
        // day-first numeric, and the CJK form keeps its month/day markers (and
        // drops the year marker) — none carry a clock component.
        val today = LocalDate.of(2025, 6, 22)
        val message = LocalDate.of(2025, 6, 14)

        assertEquals("Jun 14", relativeTimeOn(message, today, Locale.US))
        assertEquals("14.06", relativeTimeOn(message, today, Locale.GERMANY))
        assertEquals("6月14日", relativeTimeOn(message, today, Locale.CHINA))

        // No clock component (no ':', AM/PM, or 24h time markers) in any locale.
        assertNoClockComponent(relativeTimeOn(message, today, Locale.US))
        assertNoClockComponent(relativeTimeOn(message, today, Locale.GERMANY))
        assertNoClockComponent(relativeTimeOn(message, today, Locale.CHINA))
    }

    @Test
    fun relativeTimeYearRungIsLocaleShortDateWithoutClock() {
        // Past a year: the locale-equivalent SHORT date. US/German collapse to a
        // 2-digit year; the CJK SHORT date keeps the locale's own (4-digit) year
        // convention. No locale shows a clock component.
        val today = LocalDate.of(2026, 6, 22)
        val message = LocalDate.of(2025, 5, 14)

        assertEquals("5/14/25", relativeTimeOn(message, today, Locale.US))
        assertEquals("14.05.25", relativeTimeOn(message, today, Locale.GERMANY))
        assertEquals("2025/5/14", relativeTimeOn(message, today, Locale.CHINA))

        assertNoClockComponent(relativeTimeOn(message, today, Locale.US))
        assertNoClockComponent(relativeTimeOn(message, today, Locale.GERMANY))
        assertNoClockComponent(relativeTimeOn(message, today, Locale.CHINA))
    }

    @Test
    fun relativeTimeNoYearRungIsMonthFirstForUsAndDayFirstForGermany() {
        // Element order, not just localized month names, must follow the locale:
        // the US day number trails the month while the German day number leads.
        val today = LocalDate.of(2025, 12, 20)
        val message = LocalDate.of(2025, 11, 3)

        val us = relativeTimeOn(message, today, Locale.US)
        val de = relativeTimeOn(message, today, Locale.GERMANY)

        // US: month token precedes the day number ("Nov 3").
        assertTrue("US should be month-first: $us", us.indexOf("Nov") < us.indexOf("3"))
        // German: day number precedes the month number ("03.11").
        assertTrue("German should be day-first: $de", de.indexOf("03") < de.indexOf("11"))
    }

    @Test
    fun relativeTimeNoYearRungHandlesQuotedLiteralDatePatterns() {
        // Some CLDR patterns put quoted words next to the year. The no-year
        // stripper must treat those quoted literals atomically instead of leaving
        // an invalid pattern that falls back to the copy.now label.
        val today = LocalDate.of(2025, 6, 22)
        val message = LocalDate.of(2025, 6, 14)

        val trailingQuotedLiteral = relativeTimeOn(message, today, Locale.forLanguageTag("pt-BR"))
        val leadingQuotedLiteral = relativeTimeOn(message, today, Locale.forLanguageTag("lv-LV"))

        assertTrue(
            "pt-BR should render a no-year date, not fallback to now: $trailingQuotedLiteral",
            trailingQuotedLiteral != RelativeTimeCopy.Default.now,
        )
        assertTrue(
            "lv-LV should render a no-year date, not fallback to now: $leadingQuotedLiteral",
            leadingQuotedLiteral != RelativeTimeCopy.Default.now,
        )
        assertTrue("pt-BR should still include the day: $trailingQuotedLiteral", trailingQuotedLiteral.contains("14"))
        assertTrue("lv-LV should still include the day: $leadingQuotedLiteral", leadingQuotedLiteral.contains("14"))
        assertTrue("pt-BR no-year rung should omit the year: $trailingQuotedLiteral", !trailingQuotedLiteral.contains("2025"))
        assertTrue("lv-LV no-year rung should omit the year: $leadingQuotedLiteral", !leadingQuotedLiteral.contains("2025"))
        assertNoClockComponent(trailingQuotedLiteral)
        assertNoClockComponent(leadingQuotedLiteral)
    }

    private fun assertNoClockComponent(rendered: String) {
        // Beyond 24h the row must never include a clock: no ':' separator, no
        // AM/PM marker, and no hour/minute pattern letters leaking through.
        assertTrue("Unexpected clock component in: $rendered", rendered.none { it == ':' })
        val upper = rendered.uppercase(Locale.ROOT)
        assertTrue("Unexpected AM/PM in: $rendered", !upper.contains("AM") && !upper.contains("PM"))
    }
}
