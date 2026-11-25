package com.hobbynest.model;

import java.util.concurrent.atomic.AtomicInteger;

/** A customer who rents equipment. Tracks student status and reward progress. */
public class Renter extends User {

    private static final String[] STUDENT_DOMAINS = {".ac.in", ".edu", ".edu.in"};

    private volatile boolean studentVerified;
    private final AtomicInteger completedRentals;

    public Renter(long id, String username, String email, boolean studentVerified, int completedRentals) {
        super(id, username, email);
        this.studentVerified = studentVerified;
        this.completedRentals = new AtomicInteger(completedRentals);
    }

    @Override
    public String getRole() {
        return "renter";
    }

    /** Prototype verification: the college email must be on an academic domain. */
    public boolean verifyStudent(String collegeEmail) {
        if (collegeEmail == null || !collegeEmail.contains("@")) {
            return false;
        }
        String e = collegeEmail.trim().toLowerCase();
        for (String domain : STUDENT_DOMAINS) {
            if (e.endsWith(domain)) {
                studentVerified = true;
                return true;
            }
        }
        return false;
    }

    public RewardTier getRewardTier() {
        return RewardTier.forCompletedRentals(completedRentals.get());
    }

    /** Called when the owner confirms the item came back in good condition. Thread-safe. */
    public int recordCompletedRental() {
        return completedRentals.incrementAndGet();
    }

    public boolean isStudentVerified() { return studentVerified; }
    public int getCompletedRentals() { return completedRentals.get(); }
}
