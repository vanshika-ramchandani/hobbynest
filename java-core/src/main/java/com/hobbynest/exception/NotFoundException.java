package com.hobbynest.exception;

/** Thrown when a requested equipment, user or booking does not exist. */
public class NotFoundException extends HobbyNestException {

    public NotFoundException(String message) {
        super(message, 404, "not_found");
    }
}
