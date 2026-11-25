package com.hobbynest;

import com.hobbynest.model.Category;
import com.hobbynest.model.Condition;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.EquipmentFactory;
import com.hobbynest.model.Owner;
import com.hobbynest.model.Renter;
import com.hobbynest.payment.MockPaymentGateway;
import com.hobbynest.pricing.PricingEngine;
import com.hobbynest.service.AuditLogger;
import com.hobbynest.service.RentalService;
import com.hobbynest.storage.FileDataStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Shared fixtures: a controllable clock and a service wired to a temp folder. */
public final class TestSupport {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** Fixed "today" so date rules never depend on when tests run. */
    public static final LocalDate TODAY = LocalDate.of(2026, 1, 10);

    private TestSupport() {
    }

    /** Clock that tests can move forward. */
    public static final class MutableClock extends Clock {
        private volatile Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return IST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public static MutableClock clock() {
        return new MutableClock(TODAY.atTime(10, 0).toInstant(ZoneOffset.ofHoursMinutes(5, 30)));
    }

    public static final class Fixture implements AutoCloseable {
        public final MutableClock clock = clock();
        public final AuditLogger audit;
        public final RentalService service;
        public final Owner owner;
        public final Renter renter;
        public final Renter otherRenter;
        public final Equipment guitar;

        public Fixture(Path dir) throws Exception {
            audit = new AuditLogger(dir.resolve("audit.log"), clock);
            service = new RentalService(new FileDataStore(dir), new PricingEngine(), new MockPaymentGateway(),
                    audit, clock);
            owner = service.addOwner("owner", "owner@test.in", Owner.OwnerType.STUDIO);
            renter = service.addRenter("renter", "renter@test.in", false);
            otherRenter = service.addRenter("other", "other@test.in", false);
            guitar = service.addEquipment(id -> EquipmentFactory.create(Category.MUSIC, id, owner.getId(), "Guitar",
                    Condition.GOOD, 100, 500, 1800, 9000, 7000));
        }

        public LocalDate day(int offset) {
            return TODAY.plusDays(offset);
        }

        @Override
        public void close() {
            audit.close();
        }
    }
}
