package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.CreatePaymentRequestDTO
import com.trace.payment.adapters.web.dtos.CreatePaymentResponseDTO
import com.trace.payment.boundary.input.ProcessPaymentUseCaseSpec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID

private const val IDEMPOTENCY_KEY_MAX_LENGTH = 255

fun Application.configurePaymentRoutes(processPaymentUseCase: ProcessPaymentUseCaseSpec) {
    routing {
        post("/wallets/{walletId}/payments") {

            val idempotencyKey = call.request.headers["Idempotency-Key"]
            if (idempotencyKey.isNullOrBlank()) {
                throw BadRequestException("Idempotency-Key header is required")
            }
            if (idempotencyKey.length > IDEMPOTENCY_KEY_MAX_LENGTH) {
                throw BadRequestException("Idempotency-Key header must not exceed $IDEMPOTENCY_KEY_MAX_LENGTH characters")
            }

            val walletId = UUID.fromString(call.parameters["walletId"])
            val request = call.receive<CreatePaymentRequestDTO>()

            val amount = request.amount ?: throw BadRequestException("amount is required")

            val occurredAtStr = request.occurredAt ?: throw BadRequestException("occurredAt is required")
            val occurredAt = Instant.parse(occurredAtStr)

            val payment = processPaymentUseCase.execute(walletId, amount, occurredAt, idempotencyKey)

            call.respond(
                HttpStatusCode.Created,
                CreatePaymentResponseDTO(
                    paymentId = payment.id.toString(),
                    status = payment.status,
                    amount = payment.amount.toString(),
                    occurredAt = payment.occurredAt.toString(),
                ),
            )
        }
    }
}
