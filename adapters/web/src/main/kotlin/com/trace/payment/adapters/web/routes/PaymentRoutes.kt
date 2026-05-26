package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.CreatePaymentRequestDTO
import com.trace.payment.adapters.web.dtos.CreatePaymentResponseDTO
import com.trace.payment.adapters.web.dtos.DataResponseDTO
import com.trace.payment.adapters.web.dtos.ListPaymentResponseDTO
import com.trace.payment.adapters.web.dtos.MetaDTO
import com.trace.payment.adapters.web.configs.RequestIdKey
import com.trace.payment.boundary.input.ListPaymentsUseCaseSpec
import com.trace.payment.boundary.input.ProcessPaymentUseCaseSpec
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private const val IDEMPOTENCY_KEY_MAX_LENGTH = 255
private const val DEFAULT_PAGE_LIMIT = 20
private const val MAX_PAGE_LIMIT = 100

private val logger = LoggerFactory.getLogger("PaymentRoutes")

fun Application.configurePaymentRoutes(
    processPaymentUseCase: ProcessPaymentUseCaseSpec,
    listPaymentsUseCase: ListPaymentsUseCaseSpec,
) {
    routing {
        post("/wallets/{walletId}/payments", {
            description = "Processa um pagamento para uma carteira"
            tags = listOf("payments")
            request {
                headerParameter<String>("Idempotency-Key") {
                    description = "Chave de idempotência para evitar duplicidade"
                    required = true
                }
                pathParameter<String>("walletId") {
                    description = "ID da carteira"
                    required = true
                }
                body<CreatePaymentRequestDTO> {
                    description = "Dados do pagamento"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "Pagamento processado com sucesso"
                    body<CreatePaymentResponseDTO>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Dados inválidos ou cabeçalho Idempotency-Key ausente"
                }
                HttpStatusCode.UnprocessableEntity to {
                    description = "Pagamento rejeitado por política"
                }
            }
        }) {
            val requestId = call.attributes.getOrNull(RequestIdKey) ?: "unknown"

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

            logger.info("metric=payment_processed requestId={} walletId={} status=APPROVED amount={}", requestId, walletId, amount)
        }

        get("/wallets/{walletId}/payments", {
            description = "Lista os pagamentos de uma carteira com paginação por cursor"
            tags = listOf("payments")
            request {
                pathParameter<String>("walletId") {
                    description = "ID da carteira"
                    required = true
                }
                queryParameter<String>("startDate") {
                    description = "Filtro de data inicial (ISO-8601)"
                    required = false
                }
                queryParameter<String>("endDate") {
                    description = "Filtro de data final (ISO-8601)"
                    required = false
                }
                queryParameter<String>("cursor") {
                    description = "Cursor para paginação"
                    required = false
                }
                queryParameter<Int>("limit") {
                    description = "Limite de itens por página (máx. $MAX_PAGE_LIMIT)"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Lista de pagamentos"
                    body<DataResponseDTO<ListPaymentResponseDTO>>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Parâmetros de consulta inválidos"
                }
            }
        }) {
            val walletId = UUID.fromString(call.parameters["walletId"])

            val startDate = call.request.queryParameters["startDate"]?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    throw BadRequestException("Invalid startDate format")
                }
            }

            val endDate = call.request.queryParameters["endDate"]?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    throw BadRequestException("Invalid endDate format")
                }
            }

            val cursor = call.request.queryParameters["cursor"]?.let {
                if (it.isBlank()) throw BadRequestException("cursor must not be blank")
                it
            }

            val limit = call.request.queryParameters["limit"]?.let {
                val parsed = try {
                    it.toInt()
                } catch (e: Exception) {
                    throw BadRequestException("Invalid limit format")
                }
                if (parsed <= 0) throw BadRequestException("limit must be greater than zero")
                if (parsed > MAX_PAGE_LIMIT) throw BadRequestException("limit must not exceed $MAX_PAGE_LIMIT")
                parsed
            } ?: DEFAULT_PAGE_LIMIT

            val result = listPaymentsUseCase.execute(walletId, startDate, endDate, cursor, limit)

            call.respond(
                HttpStatusCode.OK,
                DataResponseDTO(
                    data = result.items.map { payment ->
                        ListPaymentResponseDTO(
                            id = payment.id.toString(),
                            walletId = payment.walletId.toString(),
                            amount = payment.amount.toString(),
                            occurredAt = payment.occurredAt.toString(),
                            status = payment.status,
                            createdAt = payment.createdAt.toString(),
                            updatedAt = payment.updatedAt.toString(),
                        )
                    },
                    meta = MetaDTO(
                        nextCursor = result.nextCursor,
                        previousCursor = result.previousCursor,
                        total = result.total,
                        totalMatches = null,
                    ),
                ),
            )
        }
    }
}
