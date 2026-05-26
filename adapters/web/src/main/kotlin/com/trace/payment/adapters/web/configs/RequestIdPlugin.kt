package com.trace.payment.adapters.web.configs

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.util.*
import java.util.UUID

val RequestIdKey = AttributeKey<String>("RequestId")

fun Application.configureRequestId() {
    install(createApplicationPlugin(name = "RequestIdPlugin") {
        onCall { call ->
            val requestId = call.request.headers["X-Request-Id"] ?: UUID.randomUUID().toString()
            call.attributes.put(RequestIdKey, requestId)
        }
    })
}
