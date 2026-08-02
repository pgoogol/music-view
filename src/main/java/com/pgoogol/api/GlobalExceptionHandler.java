package com.pgoogol.api;

import com.pgoogol.common.ConflictException;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.NotFoundException;
import com.pgoogol.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Jedyny punkt mapowania wyjątków na odpowiedzi błędów — bez rozproszonych
 * {@code @ExceptionHandler} po kontrolerach; bez stack trace'ów w odpowiedzi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(ValidationException ex) {

        log.warn("Validation failed: {} — {}", ex.getErrorCode(), ex.getMessage());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBeanValidation(MethodArgumentNotValidException ex) {

        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        log.warn("Request body validation failed: {}", details);
        return ErrorResponse.of("VALIDATION_FAILED", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleParameterTypeMismatch(MethodArgumentTypeMismatchException ex) {

        // np. sort=BPMM albo genreFamily=SALSA — wartość spoza enuma to błąd klienta, nie serwera
        log.warn("Invalid request parameter '{}': {}", ex.getName(), ex.getValue());
        return ErrorResponse.of("INVALID_PARAMETER",
            "Niepoprawna wartość parametru '%s': %s".formatted(ex.getName(), ex.getValue()));
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException ex) {

        log.warn("Conflict: {} — {}", ex.getErrorCode(), ex.getMessage());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Konflikt wykryty przez JPA (D29) — dwa żądania weszły równolegle i drugie
     * pisałoby po świeżo zmienionym wierszu. Nieświeżego klienta łapiemy wcześniej,
     * porównując wersję z żądania; tutaj zostaje wyścig transakcji.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(OptimisticLockingFailureException ex) {

        log.info("Konflikt zapisu (blokada optymistyczna): {}", ex.getMessage());
        return ErrorResponse.of("RESOURCE_MODIFIED",
            "Wpis zmienił się w innym miejscu — odśwież i spróbuj ponownie");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException ex) {

        log.warn("Resource not found: {}", ex.getErrorCode());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMaxUploadSize(MaxUploadSizeExceededException ex) {

        log.warn("Upload rejected: file too large");
        return ErrorResponse.of("FILE_TOO_LARGE", "Przesłany plik przekracza dopuszczalny rozmiar");
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleExternalService(ExternalServiceException ex) {

        log.error("External service failure: {} — {}", ex.getErrorCode(), ex.getMessage());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {

        log.error("Unexpected error", ex);
        return ErrorResponse.of("INTERNAL_ERROR", "Wystąpił nieoczekiwany błąd");
    }
}
