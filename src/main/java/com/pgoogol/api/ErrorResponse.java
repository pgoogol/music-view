package com.pgoogol.api;

import org.slf4j.MDC;

import java.time.Instant;

/**
 * Jednolity format błędu API (docs/rules/errorhandling.md); {@code traceId}
 * z MDC — null dopóki tracing nie zostanie skonfigurowany.
 */
public record ErrorResponse(String errorCode, String message, Instant timestamp, String traceId) {

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now(), MDC.get("traceId"));
    }
}
