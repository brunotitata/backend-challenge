package com.trace.payment.core.entities

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object PeriodClassifier {

    private val DAYTIME_START = LocalTime.of(6, 0)
    private val DAYTIME_END = LocalTime.of(18, 0)
    private val NIGHT_START = LocalTime.of(18, 0)

    fun classify(occurredAt: Instant, zone: ZoneId): PeriodClassification {
        val zoned = occurredAt.atZone(zone)
        val dayOfWeek = zoned.dayOfWeek

        return if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            classifyWeekend(zoned)
        } else {
            classifyWeekday(zoned)
        }
    }

    private fun classifyWeekend(zoned: ZonedDateTime): PeriodClassification {
        val periodStart = zoned.toLocalDate().atStartOfDay(zoned.zone).toInstant()
        return PeriodClassification(PeriodType.WEEKEND, periodStart)
    }

    private fun classifyWeekday(zoned: ZonedDateTime): PeriodClassification {
        val localTime = zoned.toLocalTime()

        return when {
            localTime < DAYTIME_START -> classifyNightBeforeSix(zoned)
            localTime < DAYTIME_END -> classifyDaytime(zoned)
            else -> classifyNightAfterEighteen(zoned)
        }
    }

    private fun classifyDaytime(zoned: ZonedDateTime): PeriodClassification {
        val periodStart = zoned.toLocalDate()
            .atTime(DAYTIME_START)
            .atZone(zoned.zone)
            .toInstant()
        return PeriodClassification(PeriodType.DAYTIME, periodStart)
    }

    private fun classifyNightAfterEighteen(zoned: ZonedDateTime): PeriodClassification {
        val periodStart = zoned.toLocalDate()
            .atTime(NIGHT_START)
            .atZone(zoned.zone)
            .toInstant()
        return PeriodClassification(PeriodType.NIGHTTIME, periodStart)
    }

    private fun classifyNightBeforeSix(zoned: ZonedDateTime): PeriodClassification {
        val previousDay = zoned.toLocalDate().minusDays(1)
        val periodStart = previousDay
            .atTime(NIGHT_START)
            .atZone(zoned.zone)
            .toInstant()
        return PeriodClassification(PeriodType.NIGHTTIME, periodStart)
    }
}
