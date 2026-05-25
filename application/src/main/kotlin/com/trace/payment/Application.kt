package com.trace.payment

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configureHealthRoutes
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        val dataSource = DatabaseFactory.createFromEnv()

        val walletDAO = WalletDAOSpecImpl(dataSource)
        val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO)

        configureSerialization()
        configureErrorHandling()
        configureHealthRoutes()
        configureWalletRoutes(createWalletUseCase)
    }.start(wait = true)
}
