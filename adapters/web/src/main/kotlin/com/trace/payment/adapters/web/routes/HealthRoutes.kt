package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.HealthResponseDTO
import io.github.smiley4.ktorswaggerui.dsl.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHealthRoutes() {
    routing {
        get("/health", {
            description = "Verifica a saúde da aplicação"
            tags = listOf("health")
            response {
                HttpStatusCode.OK to {
                    description = "Aplicação está saudável"
                    body<HealthResponseDTO>()
                }
            }
        }) {
            call.respond(HttpStatusCode.OK, HealthResponseDTO(status = "OK"))
        }
    }
}
