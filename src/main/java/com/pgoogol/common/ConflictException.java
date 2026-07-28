package com.pgoogol.common;

/**
 * Konflikt stanu (duplikat, nielegalne przejście) → HTTP 409.
 */
public class ConflictException extends AppException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
