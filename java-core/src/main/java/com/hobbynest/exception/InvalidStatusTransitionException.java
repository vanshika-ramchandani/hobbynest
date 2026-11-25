package com.hobbynest.exception;

/** Thrown when a booking action is not allowed from its current status or by this user. */
public class InvalidStatusTransitionException extends HobbyNestException {

    public InvalidStatusTransitionException(String message) {
        super(message, 409, "invalid_transition");
    }
}
