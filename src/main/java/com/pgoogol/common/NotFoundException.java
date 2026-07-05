package com.pgoogol.common;

/**
 * Brak zasobu → HTTP 404.
 */
public class NotFoundException extends AppException {

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
