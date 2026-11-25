package com.hobbynest.exception;

/** Thrown when rental dates or booking input are invalid (past start, end before start, too long). */
public class InvalidRentalPeriodException extends HobbyNestException {

    public InvalidRentalPeriodException(String message) {
        super(message, 400, "invalid_period");
    }
}
