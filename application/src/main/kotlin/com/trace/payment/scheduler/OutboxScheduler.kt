package com.trace.payment.scheduler

import com.trace.payment.boundary.common.EventPublisherSpec
import com.trace.payment.boundary.database.OutboxGatewaySpec
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class OutboxScheduler(
    private val outboxGateway: OutboxGatewaySpec,
    private val eventPublisher: EventPublisherSpec,
    private val exchangeName: String,
    private val pollIntervalSeconds: Long = 15,
    private val batchSize: Int = 100,
) {

    private val logger = LoggerFactory.getLogger(OutboxScheduler::class.java)
    private var executor: ScheduledExecutorService? = null

    fun start() {
        logger.info("Starting OutboxScheduler with interval={}s, exchange={}", pollIntervalSeconds, exchangeName)
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "outbox-scheduler").apply { isDaemon = true }
        }
        executor?.scheduleWithFixedDelay(
            { processBatch() },
            pollIntervalSeconds,
            pollIntervalSeconds,
            TimeUnit.SECONDS,
        )
    }

    fun stop() {
        logger.info("Stopping OutboxScheduler")
        executor?.shutdown()
        executor?.awaitTermination(5, TimeUnit.SECONDS)
        executor = null
    }

    fun processBatch() {
        try {
            val events = outboxGateway.findUnprocessed(batchSize)
            if (events.isEmpty()) return

            logger.info("Processing {} unprocessed outbox events", events.size)
            for (event in events) {
                try {
                    eventPublisher.publish(exchangeName, event.aggregateType, event.payload)
                    outboxGateway.markAsSent(event.id)
                    logger.debug("Published and marked event {} as sent", event.id)
                } catch (e: Exception) {
                    logger.error("Failed to publish event {}: {}", event.id, e.message, e)
                    outboxGateway.markAsError(event.id)
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing outbox batch: {}", e.message, e)
        }
    }
}
