package com.trace.payment.boundary.input

data class EvaluationResult(
    val approved: Boolean,
    val reason: String? = null,
)
