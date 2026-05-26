package com.trace.payment.adapters.web.configs

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.server.application.*

fun Application.configureSwagger() {
    install(SwaggerUI) {
        swagger {
            swaggerUrl = "swagger-ui"
            forwardRoot = false
        }
        info {
            title = "Trace Payment API"
            version = "1.0"
            description = "API para processamento de pagamentos e gerenciamento de carteiras e políticas"
        }
        server {
            url = "http://localhost:8080"
            description = "Servidor de desenvolvimento"
        }
    }
}
