package com.hobbynest.pricing;

import com.hobbynest.model.Rentable;

/** Decides the base rent for a number of days. Swappable (e.g. flat daily pricing for promotions). */
public interface PricingStrategy {

    int rentFor(Rentable item, int days);
}
