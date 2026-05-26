package com.trace.payment.adapters.message

import com.rabbitmq.client.ConnectionFactory
import com.trace.payment.boundary.common.EventPublisherSpec
import org.slf4j.LoggerFactory

class RabbitMqEventPublisher(
    private val connectionFactory: ConnectionFactory,
) : EventPublisherSpec {

    private val logger = LoggerFactory.getLogger(RabbitMqEventPublisher::class.java)

    override fun publish(exchange: String, routingKey: String, payload: String) {
        connectionFactory.newConnection().use { connection ->
            connection.createChannel().use { channel ->
                channel.basicPublish(exchange, routingKey, null, payload.toByteArray(Charsets.UTF_8))
                logger.debug("Published event to exchange={}, routingKey={}", exchange, routingKey)
            }
        }
    }
}
