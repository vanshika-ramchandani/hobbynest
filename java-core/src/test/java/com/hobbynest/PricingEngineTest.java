package com.hobbynest;

import com.hobbynest.exception.InvalidRentalPeriodException;
import com.hobbynest.model.Category;
import com.hobbynest.model.Condition;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.EquipmentFactory;
import com.hobbynest.model.Quote;
import com.hobbynest.model.Renter;
import com.hobbynest.pricing.BestRatePricing;
import com.hobbynest.pricing.DailyOnlyPricing;
import com.hobbynest.pricing.Money;
import com.hobbynest.pricing.PricingEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static com.hobbynest.TestSupport.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingEngineTest {

    private final PricingEngine engine = new PricingEngine();

    private static Equipment item(Category c, int daily, int weekly, int monthly, int value) {
        return EquipmentFactory.create(c, 1, 1, "Item", Condition.GOOD, daily, weekly, monthly, value, null);
    }

    private static Equipment guitar() {
        return item(Category.MUSIC, 100, 500, 1800, 9000);
    }

    @ParameterizedTest(name = "{0} days at 100/500/1800 costs {1}")
    @CsvSource({
            "1, 100",     // single day
            "4, 400",     // daily cheaper than a week
            "5, 500",     // tie: 5 days = 1 week price
            "6, 500",     // overshoot: one week cheaper than 6 days
            "7, 500",     // exactly one week
            "8, 600",     // week + 1 day
            "14, 1000",   // two weeks
            "28, 1800",   // month (1800) cheaper than 4 weeks (2000)
            "30, 1800",
            "31, 1900",
            "90, 5400",   // maximum length: 3 months
    })
    void bestRateChoosesCheapestCombination(int days, int expected) {
        assertEquals(expected, new BestRatePricing().rentFor(guitar(), days));
    }

    @Test
    void strategiesArePolymorphic() {
        assertEquals(700, new DailyOnlyPricing().rentFor(guitar(), 7));
        assertEquals(500, new BestRatePricing().rentFor(guitar(), 7));
        assertThrows(IllegalArgumentException.class, () -> new BestRatePricing().rentFor(guitar(), 0));
    }

    @ParameterizedTest(name = "market value {0} -> deposit {1}")
    @CsvSource({
            "0, 0", "2999, 0",              // low tier, upper boundary
            "3000, 300", "3250, 350",        // medium tier lower boundary; 325 rounds half-up to 350
            "14999, 1500", "15000, 3000",    // medium/high boundary
            "49999, 10000", "50000, 12500",  // high/premium boundary
            "140000, 35000",
    })
    void depositTiersAndBoundaries(int value, int deposit) {
        assertEquals(deposit, PricingEngine.depositFor(value));
    }

    @Test
    void moneyRoundsHalfUpLikeDjango() {
        // Defect D-05: Python round(28.5) == 28 (banker's rounding). Both services now round half up.
        assertEquals(29, Money.percentOf(570, 5));
        assertEquals(75, Money.percentOf(500, 15));
        assertEquals(0, Money.percentOf(0, 15));
        assertThrows(IllegalArgumentException.class, () -> Money.divHalfUp(-1, 2));
    }

    @Test
    void quoteMatchesDjangoReferenceValues() throws Exception {
        // Same scenario as backend FlowTests.test_booking_breakdown: guitar, 7 days, no discounts.
        Quote q = engine.quote(guitar(), TODAY.plusDays(2), TODAY.plusDays(8), null, TODAY);
        assertEquals(7, q.days());
        assertEquals(500, q.rentalAmount());
        assertEquals(900, q.deposit());
        assertEquals(99, q.deliveryFee());
        assertEquals(1499, q.totalPayable());
        assertEquals(75, q.commission());
        assertEquals(425, q.ownerPayout());
        assertEquals(500, q.commission() + q.ownerPayout());
    }

    @Test
    void discountsStackStudentAndBadge() throws Exception {
        Renter bronzeStudent = new Renter(1, "a", "a@x.in", true, 3);
        Quote q = engine.quote(guitar(), TODAY, TODAY.plusDays(6), bronzeStudent, TODAY);
        assertEquals(50, q.discountAmount()); // 5% student + 5% bronze of 500
        assertEquals("Student 5%, Bronze badge 5%", q.discountLabel());

        Renter goldStudent = new Renter(2, "b", "b@x.in", true, 15);
        Quote g = engine.quote(guitar(), TODAY, TODAY.plusDays(6), goldStudent, TODAY);
        assertEquals(100, g.discountAmount()); // 5 + 15 = 20%, exactly at the cap
        assertFalse(g.discountLabel().contains("capped"));
    }

    @Test
    void halfRupeeDiscountRoundsUp() throws Exception {
        Equipment e = item(Category.ART, 570, 3990, 15000, 2000);
        Quote q = engine.quote(e, TODAY, TODAY, new Renter(1, "s", "s@x.in", true, 0), TODAY);
        assertEquals(29, q.discountAmount());
    }

    @Test
    void periodValidation() {
        Equipment g = guitar();
        assertThrows(InvalidRentalPeriodException.class, () -> PricingEngine.validatePeriod(g, TODAY.minusDays(1), TODAY, TODAY));
        assertThrows(InvalidRentalPeriodException.class, () -> PricingEngine.validatePeriod(g, TODAY.plusDays(2), TODAY.plusDays(1), TODAY));
        assertThrows(InvalidRentalPeriodException.class, () -> PricingEngine.validatePeriod(g, null, TODAY, TODAY));
        assertThrows(InvalidRentalPeriodException.class, () -> PricingEngine.validatePeriod(g, TODAY, TODAY.plusDays(90), TODAY)); // 91 days
    }

    @Test
    void periodBoundariesAccepted() throws Exception {
        assertEquals(1, PricingEngine.validatePeriod(guitar(), TODAY, TODAY, TODAY));
        assertEquals(90, PricingEngine.validatePeriod(guitar(), TODAY, TODAY.plusDays(89), TODAY));
    }

    @Test
    void sportsKitsHaveShorterMaximum() throws Exception {
        Equipment kit = item(Category.SPORTS, 80, 400, 1200, 4000);
        LocalDate start = TODAY;
        assertEquals(30, PricingEngine.validatePeriod(kit, start, start.plusDays(29), TODAY));
        InvalidRentalPeriodException e = assertThrows(InvalidRentalPeriodException.class,
                () -> PricingEngine.validatePeriod(kit, start, start.plusDays(30), TODAY));
        assertTrue(e.getMessage().contains("30"));
    }

    @Test
    void rentalCreditIsHalfOfPaidRentCappedAtHalfSalePrice() throws Exception {
        Quote q = engine.quote(guitar(), TODAY, TODAY.plusDays(6), null, TODAY); // rent 500
        assertEquals(250, PricingEngine.rentalCredit(q, 7000));
        assertEquals(100, PricingEngine.rentalCredit(q, 200)); // capped at half of sale price
    }
}
