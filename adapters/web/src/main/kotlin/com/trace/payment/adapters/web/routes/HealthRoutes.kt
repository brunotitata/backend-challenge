package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.HealthResponseDTO
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHealthRoutes() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponseDTO(status = "OK"))
        }
    }
}
