package com.hobbynest.exception;

/** Thrown when equipment is already booked for overlapping dates, or is no longer listed. */
public class ItemUnavailableException extends HobbyNestException {

    public ItemUnavailableException(String message) {
        super(message, 409, "item_unavailable");
    }
}
