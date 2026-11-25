package com.hobbynest.model;

/** An individual, studio or academy that lists equipment for rent. */
public class Owner extends User {

    public enum OwnerType { INDIVIDUAL, STUDIO, ACADEMY }

    private final OwnerType ownerType;

    public Owner(long id, String username, String email, OwnerType ownerType) {
        super(id, username, email);
        this.ownerType = ownerType;
    }

    @Override
    public String getRole() {
        return "owner";
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }
}
