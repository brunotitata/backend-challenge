package com.trace.payment.core.entities

import java.time.Instant

data class PeriodClassification(
    val periodType: PeriodType,
    val periodStart: Instant,
)
