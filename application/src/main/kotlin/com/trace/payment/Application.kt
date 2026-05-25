package com.trace.payment

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.adapters.database.dao.IdempotencyRepositoryImpl
import com.trace.payment.adapters.database.dao.PolicyDAOSpecImpl
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.database.gateway.PaymentGatewayImpl
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configureHealthRoutes
import com.trace.payment.adapters.web.routes.configurePaymentRoutes
import com.trace.payment.adapters.web.routes.configurePolicyRoutes
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.core.usecase.AssignPolicyUseCaseImpl
import com.trace.payment.core.usecase.CreatePolicyUseCaseImpl
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
import com.trace.payment.core.usecase.ListPoliciesUseCaseImpl
import com.trace.payment.core.usecase.ListWalletPoliciesUseCaseImpl
import com.trace.payment.core.usecase.PolicyEvaluatorRegistryImpl
import com.trace.payment.core.usecase.PolicyResolverImpl
import com.trace.payment.core.usecase.ProcessPaymentUseCaseImpl
import com.trace.payment.core.usecase.ValueLimitEvaluator
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        val dataSource = DatabaseFactory.createFromEnv()
        val dsl = JooqFactory.create(dataSource)

        val walletDAO = WalletDAOSpecImpl(dsl)
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val paymentGateway = PaymentGatewayImpl(dsl)
        val idempotencyRepository = IdempotencyRepositoryImpl(dsl)

        val policyResolver = PolicyResolverImpl(policyDAO)
        val policyRegistry = PolicyEvaluatorRegistryImpl().apply {
            register("VALUE_LIMIT", ValueLimitEvaluator())
        }

        val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO)
        val createPolicyUseCase = CreatePolicyUseCaseImpl(policyDAO)
        val listPoliciesUseCase = ListPoliciesUseCaseImpl(policyDAO)
        val listWalletPoliciesUseCase = ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
        val assignPolicyUseCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO)
        val processPaymentUseCase = ProcessPaymentUseCaseImpl(walletDAO, policyResolver, policyRegistry, paymentGateway, idempotencyRepository)

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
        configurePaymentRoutes(processPaymentUseCase)
    }.start(wait = true)
}
