package com.trace.payment.boundary.common

interface EventPublisherSpec {
    fun publish(exchange: String, routingKey: String, payload: String)
}
