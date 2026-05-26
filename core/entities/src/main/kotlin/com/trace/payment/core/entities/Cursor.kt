package com.trace.payment.core.entities

import java.time.Instant
import java.util.Base64
import java.util.UUID

data class Cursor(
    val occurredAt: Instant,
    val id: UUID,
    val direction: Direction = Direction.FWD,
) {
    enum class Direction { FWD, BWD }

    fun encode(): String {
        val raw = "${direction.name}$SEPARATOR$occurredAt$SEPARATOR$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    companion object {
        private const val SEPARATOR = "|"

        fun decode(cursor: String): Cursor {
            val decoded = try {
                Base64.getUrlDecoder().decode(cursor).decodeToString()
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid cursor format")
            }
            val parts = decoded.split(SEPARATOR)
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid cursor format")
            }
            val dir = try {
                Direction.valueOf(parts[0])
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor format")
            }
            val occurredAt = try {
                Instant.parse(parts[1])
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor format")
            }
            val id = try {
                UUID.fromString(parts[2])
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor format")
            }
            return Cursor(occurredAt, id, dir)
        }
    }
}
