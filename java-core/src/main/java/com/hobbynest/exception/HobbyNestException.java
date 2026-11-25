package com.hobbynest.exception;

/**
 * Base checked exception for every business rule violation in HobbyNest.
 * Each subclass carries the HTTP status and a machine-readable code, so the API layer
 * can turn any domain error into a clean response without knowing the details.
 */
public abstract class HobbyNestException extends Exception {

    private final int httpStatus;
    private final String code;

    protected HobbyNestException(String message, int httpStatus, String code) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }
}
