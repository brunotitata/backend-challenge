package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.*
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.AssignPolicyUseCaseSpec
import com.trace.payment.boundary.input.CreatePolicyUseCaseSpec
import com.trace.payment.boundary.input.ListPoliciesUseCaseSpec
import com.trace.payment.boundary.input.ListWalletPoliciesUseCaseSpec
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.put
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

fun Application.configurePolicyRoutes(
    createPolicyUseCase: CreatePolicyUseCaseSpec,
    listPoliciesUseCase: ListPoliciesUseCaseSpec,
    listWalletPoliciesUseCase: ListWalletPoliciesUseCaseSpec,
    assignPolicyUseCase: AssignPolicyUseCaseSpec,
) {
    routing {

        post("/policies", {
            description = "Cria uma nova política"
            tags = listOf("policies")
            request {
                body<CreatePolicyRequestDTO> {
                    description = "Dados para criação da política"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "Política criada com sucesso"
                    body<PolicyResponseDTO>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Dados inválidos"
                }
            }
        }) {
            val request = call.receive<CreatePolicyRequestDTO>()

            val name = request.name
            if (name.isNullOrBlank()) throw ValidationException("name must not be null")

            val category = request.category
            if (category.isNullOrBlank()) throw ValidationException("category must not be null")

            val policy = createPolicyUseCase.execute(
                name = name,
                category = category,
                maxPerPayment = request.maxPerPayment?.content?.let { BigDecimal(it) },
                daytimeDailyLimit = request.daytimeDailyLimit?.content?.let { BigDecimal(it) },
                nighttimeDailyLimit = request.nighttimeDailyLimit?.content?.let { BigDecimal(it) },
                weekendDailyLimit = request.weekendDailyLimit?.content?.let { BigDecimal(it) },
                dailyTransactionLimit = request.dailyTransactionLimit,
            )

            call.respond(
                HttpStatusCode.Created,
                PolicyResponseDTO(
                    id = policy.id.toString(),
                    name = policy.name,
                    category = policy.category,
                    maxPerPayment = policy.maxPerPayment?.toString(),
                    daytimeDailyLimit = policy.daytimeDailyLimit?.toString(),
                    nighttimeDailyLimit = policy.nighttimeDailyLimit?.toString(),
                    weekendDailyLimit = policy.weekendDailyLimit?.toString(),
                    dailyTransactionLimit = policy.dailyTransactionLimit,
                    createdAt = policy.createdAt.toString(),
                    updatedAt = policy.updatedAt.toString(),
                ),
            )
        }

        get("/policies", {
            description = "Lista todas as políticas"
            tags = listOf("policies")
            response {
                HttpStatusCode.OK to {
                    description = "Lista de políticas"
                    body<DataResponseDTO<PolicyResponseDTO>>()
                }
            }
        }) {
            val policies = listPoliciesUseCase.execute()
            call.respond(
                DataResponseDTO(
                    data = policies.map { policy ->
                        PolicyResponseDTO(
                            id = policy.id.toString(),
                            name = policy.name,
                            category = policy.category,
                            maxPerPayment = policy.maxPerPayment?.toString(),
                            daytimeDailyLimit = policy.daytimeDailyLimit?.toString(),
                            nighttimeDailyLimit = policy.nighttimeDailyLimit?.toString(),
                            weekendDailyLimit = policy.weekendDailyLimit?.toString(),
                            dailyTransactionLimit = policy.dailyTransactionLimit,
                            createdAt = policy.createdAt.toString(),
                            updatedAt = policy.updatedAt.toString(),
                        )
                    },
                    meta = MetaDTO(total = policies.size),
                ),
            )
        }

        get("/wallets/{walletId}/policies", {
            description = "Lista as políticas ativas de uma carteira"
            tags = listOf("policies")
            request {
                pathParameter<String>("walletId") {
                    description = "ID da carteira"
                    required = true
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Lista de políticas da carteira"
                    body<DataResponseDTO<WalletPolicyResponseDTO>>()
                }
                HttpStatusCode.BadRequest to {
                    description = "ID da carteira inválido"
                }
            }
        }) {
            val walletId = UUID.fromString(call.parameters["walletId"])
            val policies = listWalletPoliciesUseCase.execute(walletId)
            call.respond(
                DataResponseDTO(
                    data = policies.map { policy ->
                        WalletPolicyResponseDTO(
                            id = policy.id.toString(),
                            name = policy.name,
                            category = policy.category,
                            maxPerPayment = policy.maxPerPayment?.toString(),
                            daytimeDailyLimit = policy.daytimeDailyLimit?.toString(),
                            nighttimeDailyLimit = policy.nighttimeDailyLimit?.toString(),
                            weekendDailyLimit = policy.weekendDailyLimit?.toString(),
                            dailyTransactionLimit = policy.dailyTransactionLimit,
                            active = policy.active ?: false,
                            createdAt = policy.createdAt.toString(),
                            updatedAt = policy.updatedAt.toString(),
                        )
                    },
                    meta = MetaDTO(total = policies.size),
                ),
            )
        }

        put("/wallets/{walletId}/policy", {
            description = "Atribui uma política a uma carteira"
            tags = listOf("policies")
            request {
                pathParameter<String>("walletId") {
                    description = "ID da carteira"
                    required = true
                }
                body<AssignPolicyRequestDTO> {
                    description = "Dados para atribuição da política"
                    required = true
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Política atribuída com sucesso"
                    body<AssignPolicyResponseDTO>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Dados inválidos"
                }
            }
        }) {
            val walletId = UUID.fromString(call.parameters["walletId"])
            val request = call.receive<AssignPolicyRequestDTO>()
            val policyIdStr = request.policyId
            if (policyIdStr.isNullOrBlank()) throw ValidationException("policyId must not be blank")
            val policyId = UUID.fromString(policyIdStr)

            assignPolicyUseCase.execute(walletId, policyId)

            call.respond(
                AssignPolicyResponseDTO(
                    walletId = walletId.toString(),
                    policyId = policyId.toString(),
                    active = true,
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
    }
}
