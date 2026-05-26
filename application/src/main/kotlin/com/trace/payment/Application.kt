package com.trace.payment

import com.rabbitmq.client.ConnectionFactory
import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.adapters.database.dao.PolicyDAOSpecImpl
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.database.gateway.JooqTransactionManager
import com.trace.payment.adapters.database.gateway.OutboxGatewayImpl
import com.trace.payment.adapters.database.gateway.PaymentGatewayImpl
import com.trace.payment.adapters.message.RabbitMqEventPublisher
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureMetrics
import com.trace.payment.adapters.web.configs.configureRequestId
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.configs.configureSwagger
import com.trace.payment.adapters.web.routes.configureHealthRoutes
import com.trace.payment.adapters.web.routes.configurePaymentRoutes
import com.trace.payment.adapters.web.routes.configurePolicyRoutes
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.core.usecase.AssignPolicyUseCaseImpl
import com.trace.payment.core.usecase.CreatePolicyUseCaseImpl
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
import com.trace.payment.core.usecase.ListPaymentsUseCaseImpl
import com.trace.payment.core.usecase.ListPoliciesUseCaseImpl
import com.trace.payment.core.usecase.ListWalletPoliciesUseCaseImpl
import com.trace.payment.core.usecase.PolicyEvaluatorRegistryImpl
import com.trace.payment.core.usecase.PolicyResolverImpl
import com.trace.payment.core.usecase.ProcessPaymentUseCaseImpl
import com.trace.payment.core.usecase.TxCountLimitEvaluator
import com.trace.payment.core.usecase.ValueLimitEvaluator
import com.trace.payment.scheduler.OutboxScheduler
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        val dataSource = DatabaseFactory.createFromEnv()
        environment.monitor.subscribe(ApplicationStopped) {
            (dataSource as? AutoCloseable)?.close()
        }
        val dsl = JooqFactory.create(dataSource)

        val transactionManager = JooqTransactionManager(dsl)
        val outboxGateway = OutboxGatewayImpl(dsl)
        val walletDAO = WalletDAOSpecImpl(dsl)
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val paymentGateway = PaymentGatewayImpl(dsl)

        val policyResolver = PolicyResolverImpl(policyDAO)
        val policyRegistry = PolicyEvaluatorRegistryImpl().apply {
            register("VALUE_LIMIT", ValueLimitEvaluator())
            register("TX_COUNT_LIMIT", TxCountLimitEvaluator())
        }

        val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO, outboxGateway, transactionManager)
        val createPolicyUseCase = CreatePolicyUseCaseImpl(policyDAO, outboxGateway, transactionManager)
        val listPoliciesUseCase = ListPoliciesUseCaseImpl(policyDAO)
        val listWalletPoliciesUseCase = ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
        val assignPolicyUseCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO, outboxGateway, transactionManager)
        val processPaymentUseCase = ProcessPaymentUseCaseImpl(walletDAO, policyResolver, policyRegistry, paymentGateway, outboxGateway, transactionManager)
        val listPaymentsUseCase = ListPaymentsUseCaseImpl(walletDAO, paymentGateway)

        val rabbitHost = System.getenv("RABBITMQ_HOST") ?: "localhost"
        val rabbitPort = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672
        val rabbitUser = System.getenv("RABBITMQ_USER") ?: "payment"
        val rabbitPass = System.getenv("RABBITMQ_PASS") ?: "payment"
        val exchangeName = System.getenv("RABBITMQ_EXCHANGE") ?: "payment.events"

        val connectionFactory = ConnectionFactory().apply {
            host = rabbitHost
            port = rabbitPort
            username = rabbitUser
            password = rabbitPass
        }
        val eventPublisher = RabbitMqEventPublisher(connectionFactory)

        val scheduler = OutboxScheduler(
            outboxGateway = outboxGateway,
            eventPublisher = eventPublisher,
            exchangeName = exchangeName,
        )
        scheduler.start()
        environment.monitor.subscribe(ApplicationStopped) {
            scheduler.stop()
        }

        configureSerialization()
        configureErrorHandling()
        configureRequestId()
        configureMetrics()
        configureSwagger()
        configureHealthRoutes()
        configureWalletRoutes(createWalletUseCase)
        configurePolicyRoutes(
            createPolicyUseCase = createPolicyUseCase,
            listPoliciesUseCase = listPoliciesUseCase,
            listWalletPoliciesUseCase = listWalletPoliciesUseCase,
            assignPolicyUseCase = assignPolicyUseCase,
        )
        configurePaymentRoutes(processPaymentUseCase, listPaymentsUseCase)
    }.start(wait = true)
}
