package com.trace.payment.core.entities

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PeriodClassifierTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val classifier = PeriodClassifier

    @Test
    fun `weekday at 05_59_59 should be NIGHTTIME`() {
        val instant = instantOnWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(5, 59, 59, 999_999_999))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.NIGHTTIME, result.periodType)
    }

    @Test
    fun `weekday at 06_00_00 should be DAYTIME`() {
        val instant = instantOnWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(6, 0, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.DAYTIME, result.periodType)
    }

    @Test
    fun `weekday at 17_59_59 should be DAYTIME`() {
        val instant = instantOnWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(17, 59, 59, 999_999_999))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.DAYTIME, result.periodType)
    }

    @Test
    fun `weekday at 18_00_00 should be NIGHTTIME`() {
        val instant = instantOnWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(18, 0, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.NIGHTTIME, result.periodType)
    }

    @Test
    fun `weekday at 23_59_59 should be NIGHTTIME`() {
        val instant = instantOnWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(23, 59, 59, 999_999_999))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.NIGHTTIME, result.periodType)
    }

    @Test
    fun `saturday at 00_00_00 should be WEEKEND`() {
        val instant = instantOnDayAt(DayOfWeek.SATURDAY, LocalTime.of(0, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.WEEKEND, result.periodType)
    }

    @Test
    fun `saturday at 12_00_00 should be WEEKEND`() {
        val instant = instantOnDayAt(DayOfWeek.SATURDAY, LocalTime.of(12, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.WEEKEND, result.periodType)
    }

    @Test
    fun `saturday at 22_00_00 should be WEEKEND`() {
        val instant = instantOnDayAt(DayOfWeek.SATURDAY, LocalTime.of(22, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.WEEKEND, result.periodType)
    }

    @Test
    fun `sunday at 23_59_59 should be WEEKEND`() {
        val instant = instantOnDayAt(DayOfWeek.SUNDAY, LocalTime.of(23, 59, 59, 999_999_999))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.WEEKEND, result.periodType)
    }

    @Test
    fun `monday 23_00 and tuesday 01_00 should have same night periodStart`() {
        val monday23 = instantOnDayAt(DayOfWeek.MONDAY, LocalTime.of(23, 0))
        val tuesday01 = instantOnDayAt(DayOfWeek.TUESDAY, LocalTime.of(1, 0))

        val mondayResult = classifier.classify(monday23, zone)
        val tuesdayResult = classifier.classify(tuesday01, zone)

        assertEquals(PeriodType.NIGHTTIME, mondayResult.periodType)
        assertEquals(PeriodType.NIGHTTIME, tuesdayResult.periodType)
        assertEquals(mondayResult.periodStart, tuesdayResult.periodStart)
    }

    @Test
    fun `daytime should have periodStart at 06_00 of same day`() {
        val instant = instantOnWeekdayAt(DayOfWeek.WEDNESDAY, LocalTime.of(10, 30))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.DAYTIME, result.periodType)

        val expectedStart = zoned(instant).toLocalDate()
            .atTime(LocalTime.of(6, 0))
            .atZone(zone)
            .toInstant()
        assertEquals(expectedStart, result.periodStart)
    }

    @Test
    fun `weekend should have periodStart at 00_00 of same day`() {
        val instant = instantOnDayAt(DayOfWeek.SATURDAY, LocalTime.of(14, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.WEEKEND, result.periodType)

        val expectedStart = zoned(instant).toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
        assertEquals(expectedStart, result.periodStart)
    }

    @Test
    fun `nighttime after 18_00 should have periodStart at 18_00 of same day`() {
        val instant = instantOnWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(23, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.NIGHTTIME, result.periodType)

        val expectedStart = zoned(instant).toLocalDate()
            .atTime(LocalTime.of(18, 0))
            .atZone(zone)
            .toInstant()
        assertEquals(expectedStart, result.periodStart)
    }

    @Test
    fun `nighttime before 06_00 should have periodStart at 18_00 of previous day`() {
        val instant = instantOnWeekdayAt(DayOfWeek.TUESDAY, LocalTime.of(1, 0))
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.NIGHTTIME, result.periodType)

        val expectedStart = zoned(instant).toLocalDate().minusDays(1)
            .atTime(LocalTime.of(18, 0))
            .atZone(zone)
            .toInstant()
        assertEquals(expectedStart, result.periodStart)
    }

    @Test
    fun `classification should work without database or framework`() {
        val instant = Instant.parse("2024-08-26T10:00:00Z")
        val result = classifier.classify(instant, zone)
        assertEquals(PeriodType.DAYTIME, result.periodType)
    }

    private fun instantOnWeekdayAt(dayOfWeek: DayOfWeek, time: LocalTime): Instant {
        return instantOnDayAt(dayOfWeek, time)
    }

    private fun instantOnDayAt(dayOfWeek: DayOfWeek, time: LocalTime): Instant {
        val date = findNextDateForDayOfWeek(dayOfWeek)
        return date.atTime(time).atZone(zone).toInstant()
    }

    private fun findNextDateForDayOfWeek(target: DayOfWeek): LocalDate {
        var date = LocalDate.of(2024, 1, 1)
        while (date.dayOfWeek != target) {
            date = date.plusDays(1)
        }
        return date
    }

    private fun zoned(instant: Instant): ZonedDateTime = instant.atZone(zone)
}
