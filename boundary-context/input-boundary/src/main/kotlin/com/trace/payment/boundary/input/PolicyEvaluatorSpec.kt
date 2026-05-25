package com.trace.payment.boundary.input

import com.trace.payment.core.entities.PolicyEntity

interface PolicyEvaluatorSpec {
    fun evaluate(policy: PolicyEntity, context: Map<String, Any?>): Boolean
}
