package com.trace.payment.adapters.web.routes

import com.trace.payment.adapters.web.dtos.CreateWalletRequestDTO
import com.trace.payment.adapters.web.dtos.WalletResponseDTO
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.CreateWalletUseCaseSpec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureWalletRoutes(createWalletUseCase: CreateWalletUseCaseSpec) {
    routing {
        post("/wallets") {
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
