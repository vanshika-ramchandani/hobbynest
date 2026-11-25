package com.hobbynest.model;

/** Common account data for renters and owners. */
public abstract class User {

    private final long id;
    private final String username;
    private final String email;

    protected User(long id, String username, String email) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("A valid email is required");
        }
        this.id = id;
        this.username = username;
        this.email = email.toLowerCase();
    }

    public abstract String getRole();

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }

    @Override
    public String toString() {
        return getRole() + " " + username;
    }
}
