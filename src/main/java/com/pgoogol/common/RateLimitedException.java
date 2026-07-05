package com.pgoogol.common;

import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * 429 Too Many Requests od zewnętrznego serwisu; jeśli serwis podał
 * {@code Retry-After}, retry honoruje go zamiast zwykłego backoffu
 * (docs/rules/errorhandling.md).
 */
public class RateLimitedException extends ExternalServiceException {

    private final transient Duration retryAfter;

    public RateLimitedException(String errorCode, String message, @Nullable Duration retryAfter) {

        super(errorCode, message);
        this.retryAfter = retryAfter;
    }

    @Nullable
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
