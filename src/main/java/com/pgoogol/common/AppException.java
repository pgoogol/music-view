package com.pgoogol.common;

import java.util.Objects;

/**
 * Bazowy wyjątek domenowy (docs/rules/errorhandling.md) — zawsze z maszynowym
 * {@code errorCode}; kod biznesowy nie rzuca surowych RuntimeException.
 */
public abstract class AppException extends RuntimeException {

    private final String errorCode;

    protected AppException(String errorCode, String message) {

        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public String getErrorCode() {
        return errorCode;
    }
}
