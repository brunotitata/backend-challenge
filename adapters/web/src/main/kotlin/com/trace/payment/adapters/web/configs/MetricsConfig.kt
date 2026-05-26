package com.trace.payment.adapters.web.configs

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

object PaymentMetrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val approvedPayments = Counter.builder("payments_processed_total")
        .description("Total processed payment attempts by status")
        .tag("status", "approved")
        .register(registry)

    private val rejectedPayments = Counter.builder("payments_processed_total")
        .description("Total processed payment attempts by status")
        .tag("status", "rejected")
        .register(registry)

    fun recordApproved() {
        approvedPayments.increment()
    }

    fun recordRejected() {
        rejectedPayments.increment()
    }
}

fun Application.configureMetrics() {
    routing {
        get("/metrics") {
            call.respondText(PaymentMetrics.registry.scrape(), ContentType.Text.Plain)
        }
    }
}
