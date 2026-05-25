package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.*
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.AssignPolicyUseCaseSpec
import com.trace.payment.boundary.input.CreatePolicyUseCaseSpec
import com.trace.payment.boundary.input.ListPoliciesUseCaseSpec
import com.trace.payment.boundary.input.ListWalletPoliciesUseCaseSpec
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

        post("/policies") {
            val request = call.receive<CreatePolicyRequestDTO>()

            val name = request.name
            if (name.isNullOrBlank()) throw ValidationException("name must not be null")

            val category = request.category
            if (category.isNullOrBlank()) throw ValidationException("category must not be null")

            val policy = createPolicyUseCase.execute(
                name = name,
                category = category,
                maxPerPayment = request.maxPerPayment?.let { BigDecimal(it) },
                daytimeDailyLimit = request.daytimeDailyLimit?.let { BigDecimal(it) },
                nighttimeDailyLimit = request.nighttimeDailyLimit?.let { BigDecimal(it) },
                weekendDailyLimit = request.weekendDailyLimit?.let { BigDecimal(it) },
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
                    createdAt = policy.createdAt.toString(),
                    updatedAt = policy.updatedAt.toString(),
                ),
            )
        }

        get("/policies") {
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
                            createdAt = policy.createdAt.toString(),
                            updatedAt = policy.updatedAt.toString(),
                        )
                    },
                    meta = MetaDTO(total = policies.size),
                ),
            )
        }

        get("/wallets/{walletId}/policies") {
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
                            active = policy.active ?: false,
                            createdAt = policy.createdAt.toString(),
                            updatedAt = policy.updatedAt.toString(),
                        )
                    },
                    meta = MetaDTO(total = policies.size),
                ),
            )
        }

        put("/wallets/{walletId}/policy") {
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
