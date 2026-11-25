package com.hobbynest.service;

import com.hobbynest.exception.DataStoreException;
import com.hobbynest.exception.HobbyNestException;
import com.hobbynest.exception.InvalidRentalPeriodException;
import com.hobbynest.exception.InvalidStatusTransitionException;
import com.hobbynest.exception.ItemUnavailableException;
import com.hobbynest.exception.NotFoundException;
import com.hobbynest.exception.PaymentFailedException;
import com.hobbynest.model.Booking;
import com.hobbynest.model.BookingStatus;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.Owner;
import com.hobbynest.model.Quote;
import com.hobbynest.model.Renter;
import com.hobbynest.model.User;
import com.hobbynest.payment.PaymentGateway;
import com.hobbynest.pricing.Money;
import com.hobbynest.pricing.PricingEngine;
import com.hobbynest.storage.DataStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core booking logic. Thread-safe: every operation that checks availability and then writes
 * holds a lock for that specific equipment item, so two simultaneous requests can never
 * double-book it, while bookings for different items still run in parallel.
 */
public class RentalService {

    public static final Duration PAYMENT_HOLD = Duration.ofMinutes(15);

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, Equipment> equipment = new ConcurrentHashMap<>();
    private final Map<Long, Booking> bookings = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> itemLocks = new ConcurrentHashMap<>();
    private final AtomicLong nextUserId = new AtomicLong(1);
    private final AtomicLong nextEquipmentId = new AtomicLong(1);
    private final AtomicLong nextBookingId = new AtomicLong(1);

    private final DataStore store;
    private final PricingEngine pricing;
    private final PaymentGateway gateway;
    private final AuditLogger audit;
    private final Clock clock;

    public RentalService(DataStore store, PricingEngine pricing, PaymentGateway gateway, AuditLogger audit,
                         Clock clock) throws DataStoreException {
        this.store = store;
        this.pricing = pricing;
        this.gateway = gateway;
        this.audit = audit;
        this.clock = clock;
        DataStore.Snapshot s = store.load();
        s.users().forEach(u -> users.put(u.getId(), u));
        s.equipment().forEach(e -> equipment.put(e.getId(), e));
        s.bookings().forEach(b -> bookings.put(b.getId(), b));
        nextUserId.set(users.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
        nextEquipmentId.set(equipment.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
        nextBookingId.set(bookings.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
    }

    // ---------- Users and listings ----------

    public Renter addRenter(String username, String email, boolean student) throws DataStoreException {
        Renter r = new Renter(nextUserId.getAndIncrement(), username, email, student, 0);
        users.put(r.getId(), r);
        persist();
        return r;
    }

    public Owner addOwner(String username, String email, Owner.OwnerType type) throws DataStoreException {
        Owner o = new Owner(nextUserId.getAndIncrement(), username, email, type);
        users.put(o.getId(), o);
        persist();
        return o;
    }

    /** Builder callback receives the new id so the subclass can be created with it. */
    public Equipment addEquipment(java.util.function.LongFunction<Equipment> builder)
            throws DataStoreException, NotFoundException {
        Equipment e = builder.apply(nextEquipmentId.getAndIncrement());
        if (!(users.get(e.getOwnerId()) instanceof Owner)) {
            throw new NotFoundException("Owner " + e.getOwnerId() + " does not exist.");
        }
        equipment.put(e.getId(), e);
        persist();
        audit.log("listing_created id=" + e.getId() + " owner=" + e.getOwnerId());
        return e;
    }

    public List<Equipment> listEquipment() {
        return equipment.values().stream().filter(Equipment::isActive)
                .sorted(Comparator.comparingLong(Equipment::getId)).toList();
    }

    public Equipment getEquipment(long id) throws NotFoundException {
        Equipment e = equipment.get(id);
        if (e == null) {
            throw new NotFoundException("Equipment " + id + " not found.");
        }
        return e;
    }

    public User getUser(long id) throws NotFoundException {
        User u = users.get(id);
        if (u == null) {
            throw new NotFoundException("User " + id + " not found.");
        }
        return u;
    }

    public Booking getBooking(long id) throws NotFoundException {
        Booking b = bookings.get(id);
        if (b == null) {
            throw new NotFoundException("Booking " + id + " not found.");
        }
        return b;
    }

    public List<Booking> bookingsForUser(long userId) {
        return bookings.values().stream()
                .filter(b -> b.getRenterId() == userId || ownerOf(b) == userId)
                .sorted(Comparator.comparingLong(Booking::getId).reversed()).toList();
    }

    // ---------- Pricing and availability ----------

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Quote quote(long equipmentId, Long renterId, LocalDate start, LocalDate end)
            throws HobbyNestException {
        Equipment e = getEquipment(equipmentId);
        Renter renter = renterId == null ? null : asRenter(getUser(renterId));
        return pricing.quote(e, start, end, renter, today());
    }

    /** A booking blocks the calendar if it is held, or unpaid but still inside the payment window. */
    boolean isBlocking(Booking b) {
        if (!BookingStatus.BLOCKING.contains(b.getStatus())) {
            return false;
        }
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
            return b.getCreatedAt().plus(PAYMENT_HOLD).isAfter(Instant.now(clock));
        }
        return true;
    }

    public boolean isAvailable(long equipmentId, LocalDate start, LocalDate end, Long excludeBookingId) {
        return bookings.values().stream()
                .filter(b -> b.getEquipmentId() == equipmentId)
                .filter(b -> excludeBookingId == null || b.getId() != excludeBookingId)
                .filter(this::isBlocking)
                .noneMatch(b -> b.overlaps(start, end));
    }

    // ---------- Booking flow ----------

    public Booking book(long renterId, long equipmentId, LocalDate start, LocalDate end, String address)
            throws HobbyNestException {
        if (address == null || address.trim().length() < 10) {
            throw new InvalidRentalPeriodException("Please enter a complete delivery address.");
        }
        Renter renter = asRenter(getUser(renterId));
        ReentrantLock lock = lockFor(equipmentId);
        lock.lock();
        try {
            Equipment e = equipment.get(equipmentId);
            if (e == null || !e.isActive()) {
                throw new ItemUnavailableException("This item is no longer available.");
            }
            if (e.getOwnerId() == renterId) {
                throw new InvalidRentalPeriodException("You cannot rent your own listing.");
            }
            Quote q = pricing.quote(e, start, end, renter, today());
            // Going back to change dates should not be blocked by the renter's own unpaid attempt.
            List<Booking> ownUnpaid = bookings.values().stream()
                    .filter(b -> b.getEquipmentId() == equipmentId && b.getRenterId() == renterId)
                    .filter(b -> b.getStatus() == BookingStatus.PENDING_PAYMENT && b.overlaps(start, end))
                    .toList();
            boolean clash = bookings.values().stream()
                    .filter(b -> b.getEquipmentId() == equipmentId && !ownUnpaid.contains(b))
                    .filter(this::isBlocking)
                    .anyMatch(b -> b.overlaps(start, end));
            if (clash) {
                // Nothing has been changed yet, so the renter's earlier attempt is left untouched.
                throw new ItemUnavailableException("This item is already booked for the selected dates.");
            }
            ownUnpaid.forEach(b -> b.setStatus(BookingStatus.CANCELLED));
            Booking b = new Booking(nextBookingId.getAndIncrement(), equipmentId, renterId, start, end,
                    address.trim(), q, Instant.now(clock), BookingStatus.PENDING_PAYMENT, null, false);
            bookings.put(b.getId(), b);
            persist();
            audit.log("booking_created id=" + b.getId() + " equipment=" + equipmentId + " renter=" + renterId);
            return b;
        } finally {
            lock.unlock();
        }
    }

    public Booking pay(long bookingId, long renterId, PaymentGateway.PaymentMethod method, String details)
            throws HobbyNestException {
        Booking b = getBooking(bookingId);
        if (b.getRenterId() != renterId) {
            throw new InvalidStatusTransitionException("Only the renter can pay for this booking.");
        }
        ReentrantLock lock = lockFor(b.getEquipmentId());
        lock.lock();
        try {
            if (b.getStatus() != BookingStatus.PENDING_PAYMENT && b.getStatus() != BookingStatus.PAYMENT_FAILED) {
                throw new InvalidStatusTransitionException("This booking has already been paid or closed.");
            }
            if (!isAvailable(b.getEquipmentId(), b.getStartDate(), b.getEndDate(), b.getId())) {
                throw new ItemUnavailableException("These dates were taken while payment was pending.");
            }
            try {
                String ref = gateway.charge(method, details, b.getQuote().totalPayable());
                b.setPaymentReference(ref);
                b.setStatus(BookingStatus.CONFIRMED);
                audit.log("payment_success booking=" + bookingId + " ref=" + ref);
            } catch (PaymentFailedException ex) {
                b.setStatus(BookingStatus.PAYMENT_FAILED);
                audit.log("payment_failed booking=" + bookingId + " reason=" + ex.getMessage());
                persist();
                throw ex;
            }
            persist();
            return b;
        } finally {
            lock.unlock();
        }
    }

    public Booking transition(long bookingId, long userId, String action) throws HobbyNestException {
        Booking b = getBooking(bookingId);
        boolean isRenter = b.getRenterId() == userId;
        boolean isOwner = ownerOf(b) == userId;
        ReentrantLock lock = lockFor(b.getEquipmentId());
        lock.lock();
        try {
            BookingStatus s = b.getStatus();
            switch (action) {
                case "cancel" -> {
                    require(isRenter, "renter", action);
                    require(Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.PAYMENT_FAILED,
                            BookingStatus.CONFIRMED).contains(s), s, action);
                    b.setStatus(BookingStatus.CANCELLED);
                }
                case "deliver" -> {
                    require(isOwner, "owner", action);
                    require(s == BookingStatus.CONFIRMED, s, action);
                    b.setStatus(BookingStatus.ACTIVE);
                }
                case "return" -> {
                    require(isRenter, "renter", action);
                    require(s == BookingStatus.ACTIVE, s, action);
                    b.setStatus(BookingStatus.RETURNED);
                }
                case "complete" -> {
                    require(isOwner, "owner", action);
                    require(s == BookingStatus.RETURNED, s, action);
                    b.setStatus(BookingStatus.COMPLETED);
                    b.setDepositRefunded(true);
                    asRenter(getUser(b.getRenterId())).recordCompletedRental();
                }
                default -> throw new InvalidStatusTransitionException("Unknown action '" + action + "'.");
            }
            persist();
            audit.log("booking_" + action + " id=" + bookingId + " by=" + userId + " status=" + b.getStatus());
            return b;
        } finally {
            lock.unlock();
        }
    }

    /** Rent-to-Buy price for an item the renter has received. */
    public Map<String, Object> purchaseOffer(long bookingId, long renterId) throws HobbyNestException {
        Booking b = getBooking(bookingId);
        Equipment e = getEquipment(b.getEquipmentId());
        if (b.getRenterId() != renterId) {
            throw new InvalidStatusTransitionException("Only the renter can request to buy.");
        }
        if (!e.isRentToBuy()) {
            throw new InvalidStatusTransitionException("The owner has not enabled Rent-to-Buy for this item.");
        }
        if (!Set.of(BookingStatus.ACTIVE, BookingStatus.RETURNED, BookingStatus.COMPLETED).contains(b.getStatus())) {
            throw new InvalidStatusTransitionException("You can request to buy once you have received the item.");
        }
        int credit = PricingEngine.rentalCredit(b.getQuote(), e.getSalePrice());
        int finalPrice = e.getSalePrice() - credit;
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("sale_price", e.getSalePrice());
        offer.put("rental_credit", credit);
        offer.put("final_price", finalPrice);
        offer.put("commission", Money.percentOf(finalPrice, PricingEngine.SALE_COMMISSION_PERCENT));
        return offer;
    }

    /** Active rentals past their end date. Used by the background OverdueMonitor. */
    public List<Booking> overdueBookings() {
        LocalDate today = today();
        return bookings.values().stream()
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE && b.getEndDate().isBefore(today))
                .toList();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", users.size());
        m.put("listings", listEquipment().size());
        m.put("bookings", bookings.size());
        m.put("confirmed_or_active", bookings.values().stream().filter(this::isBlocking).count());
        return m;
    }

    // ---------- Helpers ----------

    private long ownerOf(Booking b) {
        Equipment e = equipment.get(b.getEquipmentId());
        return e == null ? -1 : e.getOwnerId();
    }

    private ReentrantLock lockFor(long equipmentId) {
        return itemLocks.computeIfAbsent(equipmentId, id -> new ReentrantLock());
    }

    private static Renter asRenter(User u) throws InvalidStatusTransitionException {
        if (u instanceof Renter r) {
            return r;
        }
        throw new InvalidStatusTransitionException("Only renter accounts can book equipment.");
    }

    private static void require(boolean ok, String who, String action) throws InvalidStatusTransitionException {
        if (!ok) {
            throw new InvalidStatusTransitionException("Only the " + who + " can perform '" + action + "'.");
        }
    }

    private static void require(boolean ok, BookingStatus current, String action)
            throws InvalidStatusTransitionException {
        if (!ok) {
            throw new InvalidStatusTransitionException(
                    "Cannot " + action + " a booking that is '" + current.apiName() + "'.");
        }
    }

    private void persist() throws DataStoreException {
        store.save(new DataStore.Snapshot(new ArrayList<>(users.values()), new ArrayList<>(equipment.values()),
                new ArrayList<>(bookings.values())));
    }
}
