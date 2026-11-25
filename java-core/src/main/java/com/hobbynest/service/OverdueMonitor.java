package com.hobbynest.service;

import com.hobbynest.model.Booking;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background job: every interval, finds rentals past their end date and logs a reminder
 * (in production this would trigger an SMS or email and a late-return charge).
 */
public class OverdueMonitor implements AutoCloseable {

    private final RentalService service;
    private final AuditLogger audit;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "overdue-monitor");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger lastOverdueCount = new AtomicInteger();

    public OverdueMonitor(RentalService service, AuditLogger audit) {
        this.service = service;
        this.audit = audit;
    }

    public void start(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(this::runOnce, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /** One check. Public so tests can run it without waiting for the schedule. */
    public int runOnce() {
        List<Booking> overdue = service.overdueBookings();
        for (Booking b : overdue) {
            audit.log("overdue_reminder booking=" + b.getId() + " renter=" + b.getRenterId()
                    + " due=" + b.getEndDate());
        }
        lastOverdueCount.set(overdue.size());
        return overdue.size();
    }

    public int getLastOverdueCount() {
        return lastOverdueCount.get();
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
