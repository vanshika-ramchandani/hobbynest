package com.hobbynest.exception;

/** Thrown when data files cannot be read or written, or contain corrupt rows. */
public class DataStoreException extends HobbyNestException {

    public DataStoreException(String message) {
        super(message, 500, "storage_error");
    }

    public DataStoreException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
