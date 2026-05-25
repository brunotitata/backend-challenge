package com.trace.payment.boundary.input

interface PolicyEvaluatorRegistrySpec {
    fun register(category: String, evaluator: PolicyEvaluatorSpec)
    fun get(category: String): PolicyEvaluatorSpec?
}
