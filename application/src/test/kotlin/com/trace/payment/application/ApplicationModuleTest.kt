package com.trace.payment.application

import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureMetrics
import com.trace.payment.adapters.web.configs.configureRequestId
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
import kotlin.test.assertTrue

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
    fun `request id header is echoed in responses`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureRequestId()
            configureHealthRoutes()
        }
        val response = client.get("/health") {
            header("X-Request-Id", "req-test-123")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("req-test-123", response.headers["X-Request-Id"])
    }

    @Test
    fun `GET metrics exposes prometheus text`() = testApplication {
        application {
            configureMetrics()
        }
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("payments_processed_total"))
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
