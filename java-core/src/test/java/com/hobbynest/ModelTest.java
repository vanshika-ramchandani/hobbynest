package com.hobbynest;

import com.hobbynest.model.Category;
import com.hobbynest.model.Condition;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.EquipmentFactory;
import com.hobbynest.model.GamingDevice;
import com.hobbynest.model.Owner;
import com.hobbynest.model.Renter;
import com.hobbynest.model.RewardTier;
import com.hobbynest.model.SportsKit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {

    private static Equipment make(Category c, int value) {
        return EquipmentFactory.create(c, 1, 1, "Item", Condition.GOOD, 100, 500, 1800, value, null);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void factoryCreatesMatchingSubclass(Category c) {
        Equipment e = make(c, 5000);
        assertEquals(c, e.getCategory());
        // Every checklist starts and ends with shared steps and has category steps in between
        assertEquals("Photograph the item from all sides", e.dispatchChecklist().get(0));
        assertTrue(e.dispatchChecklist().size() >= 4);
    }

    @Test
    void subclassesOverrideBehaviour() {
        assertInstanceOf(SportsKit.class, make(Category.SPORTS, 1000));
        assertEquals(30, make(Category.SPORTS, 1000).maxRentalDays());
        assertEquals(90, make(Category.MUSIC, 1000).maxRentalDays());
        assertTrue(make(Category.GAMING, 1000).dispatchChecklist().stream().anyMatch(s -> s.contains("factory-reset")));
        assertTrue(new GamingDevice(1, 1, "PS5", Condition.NEW, 600, 3200, 9500, 52000, null).requiresIdVerification());
    }

    @ParameterizedTest(name = "{0} value {1} -> ID check {2}")
    @CsvSource({
            "MUSIC, 14999, false", "MUSIC, 15000, true",
            "PHOTOGRAPHY, 9999, false", "PHOTOGRAPHY, 10000, true", // cameras have a lower threshold
    })
    void idVerificationThresholds(Category c, int value, boolean expected) {
        assertEquals(expected, make(c, value).requiresIdVerification());
    }

    @Test
    void equipmentValidatesRates() {
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFactory.create(Category.ART, 1, 1, "x", Condition.GOOD, 5, 30, 100, 100, null));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFactory.create(Category.ART, 1, 1, "x", Condition.GOOD, 100, 800, 1800, 100, null));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFactory.create(Category.ART, 1, 1, "x", Condition.GOOD, 100, 500, 3000, 100, null));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFactory.create(Category.ART, 1, 1, " ", Condition.GOOD, 100, 500, 1800, 100, null));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFactory.create(Category.ART, 1, 1, "x", Condition.GOOD, 100, 500, 1800, 100, 0));
    }

    @Test
    void rentToBuyFlag() {
        assertFalse(make(Category.MUSIC, 100).isRentToBuy());
        assertTrue(EquipmentFactory.create(Category.MUSIC, 1, 1, "x", Condition.GOOD, 100, 500, 1800, 9000, 7000).isRentToBuy());
    }

    @ParameterizedTest(name = "{0} completed rentals -> {1}")
    @CsvSource({"0, MEMBER", "2, MEMBER", "3, BRONZE", "6, BRONZE", "7, SILVER", "14, SILVER", "15, GOLD", "40, GOLD"})
    void rewardTierBoundaries(int completed, RewardTier tier) {
        assertEquals(tier, RewardTier.forCompletedRentals(completed));
    }

    @Test
    void renterStudentVerificationAndRewards() {
        Renter r = new Renter(1, "aarav", "Aarav@Gmail.com", false, 2);
        assertEquals("aarav@gmail.com", r.getEmail());
        assertFalse(r.verifyStudent("aarav@gmail.com"));
        assertFalse(r.verifyStudent(null));
        assertFalse(r.isStudentVerified());
        assertTrue(r.verifyStudent("aarav@ldce.ac.in"));
        assertTrue(r.isStudentVerified());
        assertEquals(RewardTier.MEMBER, r.getRewardTier());
        r.recordCompletedRental();
        assertEquals(RewardTier.BRONZE, r.getRewardTier());
        assertEquals("renter", r.getRole());
    }

    @Test
    void usersValidateInput() {
        assertThrows(IllegalArgumentException.class, () -> new Renter(1, "", "a@b.in", false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Owner(1, "o", "not-an-email", Owner.OwnerType.STUDIO));
        assertEquals("owner", new Owner(1, "o", "o@b.in", Owner.OwnerType.ACADEMY).getRole());
    }
}
