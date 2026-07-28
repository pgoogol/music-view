package com.pgoogol.common.ratelimit;

import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ApiCallGuardTest {

    @Test
    void execute_whenExternalServiceFailsThenRecovers_retriesAndReturnsResult() {

        // given
        ApiCallGuard guard = ApiCallGuard.of("test-retry", 100, 3, Duration.ofMillis(10));
        AtomicInteger attempts = new AtomicInteger();

        // when
        String result = guard.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ExternalServiceException("TEST_UNAVAILABLE", "chwilowa awaria");
            }
            return "ok";
        });

        // then
        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void execute_whenFailuresExceedMaxAttempts_throwsLastException() {

        // given
        ApiCallGuard guard = ApiCallGuard.of("test-exhausted", 100, 2, Duration.ofMillis(10));
        AtomicInteger attempts = new AtomicInteger();

        // when
        Throwable thrown = catchThrowable(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            throw new ExternalServiceException("TEST_UNAVAILABLE", "trwała awaria");
        }));

        // then
        assertThat(thrown).isInstanceOf(ExternalServiceException.class);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void execute_whenValidationExceptionThrown_doesNotRetry() {

        // given
        ApiCallGuard guard = ApiCallGuard.of("test-4xx", 100, 3, Duration.ofMillis(10));
        AtomicInteger attempts = new AtomicInteger();

        // when
        Throwable thrown = catchThrowable(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            throw new ValidationException("TEST_BAD_REQUEST", "błąd 4xx");
        }));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void execute_whenRateLimitSet_throttlesCallsPerSecond() {

        // given — 2 zapytania/s; 4 wywołania muszą zająć ≥ 1 s (dwa pełne okna)
        ApiCallGuard guard = ApiCallGuard.of("test-throttle", 2, 1, Duration.ofMillis(10));
        long start = System.nanoTime();

        // when
        AtomicInteger calls = new AtomicInteger();
        guard.execute(calls::incrementAndGet);
        guard.execute(calls::incrementAndGet);
        guard.execute(calls::incrementAndGet);
        guard.execute(calls::incrementAndGet);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // then
        assertThat(calls.get()).isEqualTo(4);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(900);
    }
}
