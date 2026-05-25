package com.trace.payment

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.dao.PolicyDAOSpecImpl
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configureHealthRoutes
import com.trace.payment.adapters.web.routes.configurePolicyRoutes
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.core.usecase.AssignPolicyUseCaseImpl
import com.trace.payment.core.usecase.CreatePolicyUseCaseImpl
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
import com.trace.payment.core.usecase.ListPoliciesUseCaseImpl
import com.trace.payment.core.usecase.ListWalletPoliciesUseCaseImpl
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        val dataSource = DatabaseFactory.createFromEnv()

        val walletDAO = WalletDAOSpecImpl(dataSource)
        val policyDAO = PolicyDAOSpecImpl(dataSource)
        val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO)
        val createPolicyUseCase = CreatePolicyUseCaseImpl(policyDAO)
        val listPoliciesUseCase = ListPoliciesUseCaseImpl(policyDAO)
        val listWalletPoliciesUseCase = ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
        val assignPolicyUseCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO)

        configureSerialization()
        configureErrorHandling()
        configureHealthRoutes()
        configureWalletRoutes(createWalletUseCase)
        configurePolicyRoutes(
            createPolicyUseCase = createPolicyUseCase,
            listPoliciesUseCase = listPoliciesUseCase,
            listWalletPoliciesUseCase = listWalletPoliciesUseCase,
            assignPolicyUseCase = assignPolicyUseCase,
        )
    }.start(wait = true)
}
