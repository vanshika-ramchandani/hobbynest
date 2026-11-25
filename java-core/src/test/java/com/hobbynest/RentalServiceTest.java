package com.hobbynest;

import com.hobbynest.exception.InvalidRentalPeriodException;
import com.hobbynest.exception.InvalidStatusTransitionException;
import com.hobbynest.exception.ItemUnavailableException;
import com.hobbynest.exception.NotFoundException;
import com.hobbynest.exception.PaymentFailedException;
import com.hobbynest.model.Booking;
import com.hobbynest.model.BookingStatus;
import com.hobbynest.model.Renter;
import com.hobbynest.payment.PaymentGateway.PaymentMethod;
import com.hobbynest.service.OverdueMonitor;
import com.hobbynest.service.RentalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RentalServiceTest {

    private static final String ADDRESS = "B-12 Sunrise Hostel, Navrangpura, Ahmedabad";

    @TempDir
    Path dir;
    TestSupport.Fixture f;
    RentalService s;

    @BeforeEach
    void setUp() throws Exception {
        f = new TestSupport.Fixture(dir);
        s = f.service;
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private Booking book(Renter r, int from, int to) throws Exception {
        return s.book(r.getId(), f.guitar.getId(), f.day(from), f.day(to), ADDRESS);
    }

    @Test
    void bookingStartsPendingWithFullBreakdown() throws Exception {
        Booking b = book(f.renter, 2, 8);
        assertEquals(BookingStatus.PENDING_PAYMENT, b.getStatus());
        assertEquals(1499, b.getQuote().totalPayable());
        assertEquals(ADDRESS, b.getDeliveryAddress());
    }

    @Test
    void overlappingBookingByAnotherRenterIsRejected() throws Exception {
        Booking b = book(f.renter, 2, 8);
        s.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "a@okaxis");
        ItemUnavailableException e = assertThrows(ItemUnavailableException.class, () -> book(f.otherRenter, 8, 10));
        assertEquals(409, e.getHttpStatus());
        // Adjacent dates (day after the end) are fine
        assertEquals(BookingStatus.PENDING_PAYMENT, book(f.otherRenter, 9, 10).getStatus());
    }

    @Test
    void unpaidHoldBlocksOthersOnlyFor15Minutes() throws Exception {
        book(f.renter, 2, 8);
        assertThrows(ItemUnavailableException.class, () -> book(f.otherRenter, 3, 4));
        f.clock.advance(Duration.ofMinutes(16));
        assertEquals(BookingStatus.PENDING_PAYMENT, book(f.otherRenter, 3, 4).getStatus());
    }

    @Test
    void renterCanRebookOverOwnUnpaidAttempt() throws Exception {
        Booking first = book(f.renter, 2, 8);
        Booking nonOverlapping = book(f.renter, 20, 22);
        Booking second = book(f.renter, 3, 9);
        assertEquals(BookingStatus.CANCELLED, first.getStatus());
        assertEquals(BookingStatus.PENDING_PAYMENT, nonOverlapping.getStatus()); // untouched
        assertEquals(BookingStatus.PENDING_PAYMENT, second.getStatus());
    }

    @Test
    void failedRebookLeavesRenterOriginalAttemptIntact() throws Exception {
        Booking paidByOther = book(f.otherRenter, 10, 12);
        s.pay(paidByOther.getId(), f.otherRenter.getId(), PaymentMethod.UPI, "b@okaxis");
        Booking mine = book(f.renter, 6, 8);
        // New dates overlap both my unpaid booking and the other renter's paid one
        assertThrows(ItemUnavailableException.class, () -> book(f.renter, 7, 11));
        assertEquals(BookingStatus.PENDING_PAYMENT, mine.getStatus());
    }

    @Test
    void cancelledBookingFreesDates() throws Exception {
        Booking b = book(f.renter, 2, 8);
        s.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "a@okaxis");
        s.transition(b.getId(), f.renter.getId(), "cancel");
        assertEquals(BookingStatus.PENDING_PAYMENT, book(f.otherRenter, 2, 8).getStatus());
    }

    @Test
    void failedPaymentThenRetrySucceeds() throws Exception {
        Booking b = book(f.renter, 2, 8);
        assertThrows(PaymentFailedException.class, () -> s.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "fail@test"));
        assertEquals(BookingStatus.PAYMENT_FAILED, b.getStatus());
        s.pay(b.getId(), f.renter.getId(), PaymentMethod.CARD, "4111111111111111");
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
        assertTrue(b.getPaymentReference().startsWith("TEST-"));
        // Cannot pay twice
        assertThrows(InvalidStatusTransitionException.class,
                () -> s.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "a@okaxis"));
    }

    @Test
    void onlyRenterCanPay() throws Exception {
        Booking b = book(f.renter, 2, 8);
        assertThrows(InvalidStatusTransitionException.class,
                () -> s.pay(b.getId(), f.otherRenter.getId(), PaymentMethod.UPI, "a@okaxis"));
    }

    @Test
    void fullLifecycleRefundsDepositAndCountsReward() throws Exception {
        Booking b = book(f.renter, 2, 8);
        long renter = f.renter.getId();
        long owner = f.owner.getId();
        s.pay(b.getId(), renter, PaymentMethod.UPI, "a@okaxis");
        assertThrows(InvalidStatusTransitionException.class, () -> s.transition(b.getId(), renter, "deliver"));
        s.transition(b.getId(), owner, "deliver");
        assertThrows(InvalidStatusTransitionException.class, () -> s.transition(b.getId(), renter, "cancel"));
        s.transition(b.getId(), renter, "return");
        assertThrows(InvalidStatusTransitionException.class, () -> s.transition(b.getId(), renter, "complete"));
        s.transition(b.getId(), owner, "complete");
        assertEquals(BookingStatus.COMPLETED, b.getStatus());
        assertTrue(b.isDepositRefunded());
        assertEquals(1, f.renter.getCompletedRentals());
        assertThrows(InvalidStatusTransitionException.class, () -> s.transition(b.getId(), owner, "fly"));
    }

    @Test
    void rentToBuyOfferAfterDelivery() throws Exception {
        Booking b = book(f.renter, 2, 8);
        assertThrows(InvalidStatusTransitionException.class, () -> s.purchaseOffer(b.getId(), f.renter.getId()));
        s.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "a@okaxis");
        s.transition(b.getId(), f.owner.getId(), "deliver");
        Map<String, Object> offer = s.purchaseOffer(b.getId(), f.renter.getId());
        assertEquals(250, offer.get("rental_credit"));
        assertEquals(6750, offer.get("final_price"));
        assertEquals(540, offer.get("commission"));
        assertThrows(InvalidStatusTransitionException.class, () -> s.purchaseOffer(b.getId(), f.otherRenter.getId()));
    }

    @Test
    void invalidRequestsRaiseSpecificExceptions() throws Exception {
        assertThrows(ItemUnavailableException.class,
                () -> s.book(f.renter.getId(), 999, f.day(2), f.day(3), ADDRESS));
        assertThrows(NotFoundException.class, () -> s.book(999, f.guitar.getId(), f.day(2), f.day(3), ADDRESS));
        assertThrows(InvalidRentalPeriodException.class,
                () -> s.book(f.renter.getId(), f.guitar.getId(), f.day(2), f.day(3), "short"));
        assertThrows(InvalidRentalPeriodException.class, () -> book(f.renter, -1, 3));
        assertThrows(InvalidStatusTransitionException.class, // owners cannot book
                () -> s.book(f.owner.getId(), f.guitar.getId(), f.day(2), f.day(3), ADDRESS));
        assertThrows(NotFoundException.class, () -> s.getBooking(42));

        f.guitar.setActive(false);
        assertThrows(ItemUnavailableException.class, () -> book(f.renter, 2, 3));
    }

    @Test
    void overdueMonitorFindsLateReturns() throws Exception {
        Booking b = book(f.renter, 0, 2);
        s.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "a@okaxis");
        s.transition(b.getId(), f.owner.getId(), "deliver");
        OverdueMonitor monitor = new OverdueMonitor(s, f.audit);
        assertEquals(0, monitor.runOnce());
        f.clock.advance(Duration.ofDays(4));
        assertEquals(1, monitor.runOnce());
        assertEquals(1, monitor.getLastOverdueCount());
        monitor.close();
    }

    @Test
    void quoteUsesRenterDiscounts() throws Exception {
        f.renter.verifyStudent("renter@college.ac.in");
        assertEquals(25, s.quote(f.guitar.getId(), f.renter.getId(), f.day(2), f.day(8)).discountAmount());
        assertEquals(0, s.quote(f.guitar.getId(), null, f.day(2), f.day(8)).discountAmount());
        assertFalse(s.listEquipment().isEmpty());
        assertTrue(s.bookingsForUser(f.owner.getId()).isEmpty());
    }
}
