package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMaxUploadSize(MaxUploadSizeExceededException ex) {

        log.warn("Upload rejected: file too large");
        return ErrorResponse.of("FILE_TOO_LARGE", "Przesłany plik przekracza dopuszczalny rozmiar");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {

        log.error("Unexpected error", ex);
        return ErrorResponse.of("INTERNAL_ERROR", "Wystąpił nieoczekiwany błąd");
    }
}
