package com.trace.payment.core.entities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Instant
import java.util.UUID
import java.util.Base64

class CursorTest {

    @Test
    fun `encode produces Base64 URL-safe string`() {
        val occurredAt = Instant.parse("2024-08-26T10:00:00.0000Z")
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val cursor = Cursor(occurredAt, id)

        val encoded = cursor.encode()
        val decoded = Cursor.decode(encoded)

        assertEquals(occurredAt, decoded.occurredAt)
        assertEquals(id, decoded.id)
        assertEquals(Cursor.Direction.FWD, decoded.direction)
    }

    @Test
    fun `decode recovers original values`() {
        val occurredAt = Instant.parse("2024-08-25T22:31:44.4758Z")
        val id = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
        val cursor = Cursor(occurredAt, id)

        val encoded = cursor.encode()
        val decoded = Cursor.decode(encoded)

        assertEquals(occurredAt, decoded.occurredAt)
        assertEquals(id, decoded.id)
    }

    @Test
    fun `decode with BWD direction recovers direction`() {
        val occurredAt = Instant.parse("2024-08-26T10:00:00.0000Z")
        val id = UUID.randomUUID()
        val cursor = Cursor(occurredAt, id, Cursor.Direction.BWD)

        val encoded = cursor.encode()
        val decoded = Cursor.decode(encoded)

        assertEquals(Cursor.Direction.BWD, decoded.direction)
    }

    @Test
    fun `decode of invalid string throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            Cursor.decode("invalid-base64!!!")
        }
    }

    @Test
    fun `decode of valid base64 but wrong format throws exception`() {
        val encoded = Base64.getUrlEncoder().encodeToString("invalid-format".toByteArray())
        assertFailsWith<IllegalArgumentException> {
            Cursor.decode(encoded)
        }
    }
}
