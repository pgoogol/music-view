package com.pgoogol.common.ratelimit;

import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.RateLimitedException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wspólna ochrona wywołań zewnętrznych API (M1.3): rate limiter + retry
 * z wykładniczym backoffem (Resilience4j — D16). Ponawiane są wyłącznie
 * {@link ExternalServiceException} (5xx/timeout/429); 4xx nie są ponawiane.
 * Przy 429 z nagłówkiem Retry-After odczekiwany jest wskazany czas.
 * Limiter działa wewnątrz retry — każda ponowiona próba też podlega limitowi.
 */
public final class ApiCallGuard {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration PERMIT_WAIT_TIMEOUT = Duration.ofMinutes(5);

    private final RateLimiter rateLimiter;
    private final Retry retry;

    private ApiCallGuard(RateLimiter rateLimiter, Retry retry) {

        this.rateLimiter = rateLimiter;
        this.retry = retry;
    }

    public static ApiCallGuard of(String name, int requestsPerSecond) {
        return of(name, requestsPerSecond, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF);
    }

    public static ApiCallGuard of(String name, int requestsPerSecond, int maxAttempts,
                                  Duration initialBackoff) {

        Objects.requireNonNull(name, "name");
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(requestsPerSecond)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(PERMIT_WAIT_TIMEOUT)
            .build();
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .retryOnException(ex -> ex instanceof ExternalServiceException)
            .intervalBiFunction((attempt, either) -> backoffMillis(attempt, initialBackoff,
                either.isLeft() ? either.getLeft() : null))
            .build();
        return new ApiCallGuard(
            RateLimiter.of(name, rateLimiterConfig),
            Retry.of(name, retryConfig));
    }

    public <T> T execute(Supplier<T> call) {

        Objects.requireNonNull(call, "call");
        return Retry.decorateSupplier(retry, RateLimiter.decorateSupplier(rateLimiter, call)).get();
    }

    private static long backoffMillis(int attempt, Duration initialBackoff, Throwable failure) {

        if (failure instanceof RateLimitedException rateLimited
                && Objects.nonNull(rateLimited.getRetryAfter())) {
            return rateLimited.getRetryAfter().toMillis();
        }
        return initialBackoff.toMillis() * (1L << (attempt - 1));
    }
}
