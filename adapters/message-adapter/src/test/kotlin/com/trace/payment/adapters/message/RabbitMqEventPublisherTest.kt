package com.trace.payment.adapters.message

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.trace.payment.boundary.common.EventPublisherSpec
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test

class RabbitMqEventPublisherTest {

    @Test
    fun `publish sends message to exchange with routing key`() {
        val mockChannel = mock<Channel>()
        val mockConnection = mock<Connection> {
            on { createChannel() } doReturn mockChannel
        }
        val connectionFactory = mock<ConnectionFactory> {
            on { newConnection() } doReturn mockConnection
        }

        val publisher: EventPublisherSpec = RabbitMqEventPublisher(connectionFactory)
        publisher.publish("payment.events", "wallet", "{\"id\":\"1\"}")

        verify(mockChannel).basicPublish("payment.events", "wallet", null, "{\"id\":\"1\"}".toByteArray(Charsets.UTF_8))
        verify(mockChannel).close()
        verify(mockConnection).close()
    }
}
