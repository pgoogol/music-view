package com.pgoogol.common;

/**
 * Awaria zewnętrznego serwisu (5xx, timeout, I/O) → HTTP 502; jedyny typ
 * wyjątku, który retry z {@code common/ratelimit} ponawia.
 */
public class ExternalServiceException extends AppException {

    public ExternalServiceException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ExternalServiceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
