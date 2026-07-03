package com.pgoogol.common;

/**
 * Nieprawidłowe wejście od klienta API → HTTP 400.
 */
public class ValidationException extends AppException {

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
