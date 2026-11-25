package com.hobbynest;

import com.hobbynest.exception.DataStoreException;
import com.hobbynest.exception.ItemUnavailableException;
import com.hobbynest.model.Booking;
import com.hobbynest.model.BookingStatus;
import com.hobbynest.model.Category;
import com.hobbynest.model.Condition;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.EquipmentFactory;
import com.hobbynest.model.Renter;
import com.hobbynest.payment.MockPaymentGateway;
import com.hobbynest.payment.PaymentGateway.PaymentMethod;
import com.hobbynest.pricing.PricingEngine;
import com.hobbynest.service.AuditLogger;
import com.hobbynest.service.RentalService;
import com.hobbynest.storage.FileDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyAndStorageTest {

    private static final String ADDRESS = "B-12 Sunrise Hostel, Navrangpura, Ahmedabad";

    @TempDir
    Path dir;

    @Test
    void hundredSimultaneousBookingsOnSameItemOnlyOneWins() throws Exception {
        try (TestSupport.Fixture f = new TestSupport.Fixture(dir)) {
            List<Renter> renters = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                renters.add(f.service.addRenter("r" + i, "r" + i + "@test.in", false));
            }
            ExecutorService pool = Executors.newFixedThreadPool(32);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            AtomicInteger other = new AtomicInteger();
            for (Renter r : renters) {
                pool.submit(() -> {
                    go.await();
                    try {
                        f.service.book(r.getId(), f.guitar.getId(), f.day(5), f.day(7), ADDRESS);
                        ok.incrementAndGet();
                    } catch (ItemUnavailableException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        other.incrementAndGet();
                    }
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(1, ok.get());
            assertEquals(99, rejected.get());
            assertEquals(0, other.get());
        }
    }

    @Test
    void differentItemsBookInParallel() throws Exception {
        try (TestSupport.Fixture f = new TestSupport.Fixture(dir)) {
            List<Equipment> items = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int n = i;
                items.add(f.service.addEquipment(id -> EquipmentFactory.create(Category.SPORTS, id, f.owner.getId(),
                        "Racket " + n, Condition.GOOD, 80, 400, 1200, 4000, null)));
            }
            ExecutorService pool = Executors.newFixedThreadPool(20);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger ok = new AtomicInteger();
            for (Equipment e : items) {
                pool.submit(() -> {
                    go.await();
                    f.service.book(f.renter.getId(), e.getId(), f.day(1), f.day(2), ADDRESS);
                    ok.incrementAndGet();
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(20, ok.get());
        }
    }

    @Test
    void dataSurvivesRestart() throws Exception {
        long bookingId;
        try (TestSupport.Fixture f = new TestSupport.Fixture(dir)) {
            Booking b = f.service.book(f.renter.getId(), f.guitar.getId(), f.day(2), f.day(8), ADDRESS);
            f.service.pay(b.getId(), f.renter.getId(), PaymentMethod.UPI, "a@okaxis");
            f.renter.verifyStudent("renter@college.ac.in");
            f.service.addRenter("saver", "saver@test.in", false); // triggers a save including the verified flag
            bookingId = b.getId();
        }
        assertTrue(Files.exists(dir.resolve("bookings.csv")));
        assertTrue(Files.readString(dir.resolve("bookings.csv")).contains("\"B-12 Sunrise Hostel, Navrangpura, Ahmedabad\""));

        try (AuditLogger audit = new AuditLogger(dir.resolve("audit.log"), TestSupport.clock())) {
            RentalService reloaded = new RentalService(new FileDataStore(dir), new PricingEngine(),
                    new MockPaymentGateway(), audit, TestSupport.clock());
            Booking b = reloaded.getBooking(bookingId);
            assertEquals(BookingStatus.CONFIRMED, b.getStatus());
            assertEquals(ADDRESS, b.getDeliveryAddress());
            assertEquals(1499, b.getQuote().totalPayable());
            assertTrue(((Renter) reloaded.getUser(b.getRenterId())).isStudentVerified());
            assertEquals("Guitar", reloaded.getEquipment(b.getEquipmentId()).getTitle());
            // New ids continue after the loaded ones
            assertTrue(reloaded.addRenter("next", "next@test.in", false).getId() > b.getRenterId());
        }
    }

    @Test
    void corruptFileRaisesDataStoreException() throws Exception {
        Files.writeString(dir.resolve("equipment.csv"), "header\n1,NOT_A_CATEGORY,1,x,GOOD,100,500,1800,100,,true\n");
        DataStoreException e = assertThrows(DataStoreException.class, () -> new FileDataStore(dir).load());
        assertEquals(500, e.getHttpStatus());
        assertTrue(e.getMessage().contains("equipment.csv"));
    }

    @Test
    void auditLoggerWritesFromBackgroundThread() throws Exception {
        Path log = dir.resolve("audit.log");
        AuditLogger audit = new AuditLogger(log, TestSupport.clock());
        for (int i = 0; i < 50; i++) {
            audit.log("event " + i);
        }
        audit.close(); // drains the queue before stopping
        assertEquals(50, audit.entriesWritten());
        List<String> lines = Files.readAllLines(log);
        assertEquals(50, lines.size());
        assertTrue(lines.get(0).contains("event 0"));
    }
}
