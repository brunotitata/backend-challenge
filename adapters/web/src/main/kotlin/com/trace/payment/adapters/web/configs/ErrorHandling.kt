package com.trace.payment.adapters.web.configs

import com.trace.payment.adapters.web.dtos.ErrorDTO
import com.trace.payment.adapters.web.dtos.ErrorResponseDTO
import com.trace.payment.boundary.exceptions.ConflictException
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.boundary.exceptions.UnprocessableEntityException
import com.trace.payment.boundary.exceptions.ValidationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import java.time.format.DateTimeParseException

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(error = ErrorDTO(code = "VALIDATION_ERROR", message = cause.message ?: "Validation error")),
            )
        }

        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDTO(error = ErrorDTO(code = "NOT_FOUND", message = cause.message ?: "Resource not found")),
            )
        }

        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(error = ErrorDTO(code = "BAD_REQUEST", message = cause.message ?: "Invalid request body")),
            )
        }

        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponseDTO(error = ErrorDTO(code = "CONFLICT", message = cause.message ?: "Conflict")),
            )
        }

        exception<UnprocessableEntityException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponseDTO(error = ErrorDTO(code = "UNPROCESSABLE_ENTITY", message = cause.message ?: "Unprocessable entity")),
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(error = ErrorDTO(code = "BAD_REQUEST", message = cause.message ?: "Invalid input")),
            )
        }

        exception<DateTimeParseException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(error = ErrorDTO(code = "BAD_REQUEST", message = "Invalid date format")),
            )
        }

        exception<NumberFormatException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(error = ErrorDTO(code = "BAD_REQUEST", message = "Invalid number format")),
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDTO(error = ErrorDTO(code = "NOT_FOUND", message = "Route not found")),
            )
        }

        status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(error = ErrorDTO(code = "BAD_REQUEST", message = "Invalid request body")),
            )
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDTO(error = ErrorDTO(code = "INTERNAL_SERVER_ERROR", message = "Unexpected internal error")),
            )
        }
    }
}
