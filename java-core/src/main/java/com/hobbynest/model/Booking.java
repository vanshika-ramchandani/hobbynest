package com.hobbynest.model;

import java.time.Instant;
import java.time.LocalDate;

/** A rental of one item by one renter for a date range. Status changes go through RentalService. */
public class Booking {

    private final long id;
    private final long equipmentId;
    private final long renterId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String deliveryAddress;
    private final Quote quote;
    private final Instant createdAt;
    private volatile BookingStatus status;
    private volatile String paymentReference;
    private volatile boolean depositRefunded;

    public Booking(long id, long equipmentId, long renterId, LocalDate startDate, LocalDate endDate,
                   String deliveryAddress, Quote quote, Instant createdAt, BookingStatus status,
                   String paymentReference, boolean depositRefunded) {
        this.id = id;
        this.equipmentId = equipmentId;
        this.renterId = renterId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.deliveryAddress = deliveryAddress;
        this.quote = quote;
        this.createdAt = createdAt;
        this.status = status;
        this.paymentReference = paymentReference;
        this.depositRefunded = depositRefunded;
    }

    /** True when this booking's dates overlap [start, end] (inclusive). */
    public boolean overlaps(LocalDate start, LocalDate end) {
        return !startDate.isAfter(end) && !endDate.isBefore(start);
    }

    public long getId() { return id; }
    public long getEquipmentId() { return equipmentId; }
    public long getRenterId() { return renterId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public Quote getQuote() { return quote; }
    public Instant getCreatedAt() { return createdAt; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String ref) { this.paymentReference = ref; }
    public boolean isDepositRefunded() { return depositRefunded; }
    public void setDepositRefunded(boolean refunded) { this.depositRefunded = refunded; }
}
