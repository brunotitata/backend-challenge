package com.trace.payment.application

import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configureHealthRoutes
import com.trace.payment.boundary.exceptions.ValidationException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationModuleTest {
    @Test
    fun `GET health returns operational status as JSON`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureHealthRoutes()
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"OK"}""", response.bodyAsText())
    }

    @Test
    fun `unknown route returns standardized not found error`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureHealthRoutes()
        }
        val response = client.get("/missing")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            """{"error":{"code":"NOT_FOUND","message":"Route not found"}}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `validation exception returns standardized validation error`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                get("/validation-error") {
                    throw ValidationException("ownerName must not be blank")
                }
            }
        }
        val response = client.get("/validation-error")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            """{"error":{"code":"VALIDATION_ERROR","message":"ownerName must not be blank"}}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `unexpected exception returns standardized internal error`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                get("/unexpected-error") {
                    error("boom")
                }
            }
        }
        val response = client.get("/unexpected-error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(
            """{"error":{"code":"INTERNAL_SERVER_ERROR","message":"Unexpected internal error"}}""",
            response.bodyAsText(),
        )
    }
}
