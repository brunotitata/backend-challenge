package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.CreateWalletRequestDTO
import com.trace.payment.adapters.web.dtos.WalletResponseDTO
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.CreateWalletUseCaseSpec
import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureWalletRoutes(createWalletUseCase: CreateWalletUseCaseSpec) {
    routing {
        post("/wallets", {
            description = "Cria uma nova carteira"
            tags = listOf("wallets")
            request {
                body<CreateWalletRequestDTO> {
                    description = "Dados para criação da carteira"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "Carteira criada com sucesso"
                    body<WalletResponseDTO>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Dados inválidos"
                }
            }
        }) {
            val request = call.receive<CreateWalletRequestDTO>()

            val ownerName = request.ownerName
            if (ownerName.isNullOrBlank()) {
                throw ValidationException("ownerName must not be blank")
            }

            val wallet = createWalletUseCase.execute(ownerName)

            call.respond(
                HttpStatusCode.Created,
                WalletResponseDTO(
                    id = wallet.id.toString(),
                    ownerName = wallet.ownerName,
                    createdAt = wallet.createdAt.toString(),
                ),
            )
        }
    }
}
