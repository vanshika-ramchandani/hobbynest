// Same business rules as backend/rentals/services.py and the Java PricingEngine.
// Whole rupees, whole-number percentages, rounding half up.

export const COMMISSION_PCT = 15;
export const SALE_COMMISSION_PCT = 8;
export const DELIVERY_FEE = 99;
export const STUDENT_DISCOUNT_PCT = 5;
export const MAX_TOTAL_DISCOUNT_PCT = 20;
export const MAX_RENTAL_DAYS = 90;
export const PAYMENT_HOLD_MINUTES = 15;

export const REWARD_TIERS = [
  { name: "Gold", needed: 15, discount: 15 },
  { name: "Silver", needed: 7, discount: 10 },
  { name: "Bronze", needed: 3, discount: 5 },
];

const DEPOSIT_TIERS = [
  [3000, 0, "No deposit (low-value item)"],
  [15000, 10, "Small refundable deposit (medium-value item)"],
  [50000, 20, "Refundable deposit + ID check (high-value item)"],
  [Infinity, 25, "Refundable deposit + ID check (premium item)"],
];

export class DemoError extends Error {
  constructor(message, status = 400, code = "bad_request") {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export const divHalfUp = (n, d) => Math.floor((2 * n + d) / (2 * d));
export const percentOf = (amount, pct) => divHalfUp(amount * pct, 100);

export function bestRentalPrice(days, daily, weekly, monthly) {
  const cost = new Array(days + 1).fill(0);
  for (let d = 1; d <= days; d++) {
    cost[d] = Math.min(
      cost[d - 1] + daily,
      cost[Math.max(0, d - 7)] + weekly,
      cost[Math.max(0, d - 30)] + monthly
    );
  }
  return cost[days];
}

export function depositFor(marketValue) {
  for (const [limit, pct, label] of DEPOSIT_TIERS) {
    if (marketValue < limit) return { amount: divHalfUp(marketValue * pct, 100 * 50) * 50, label };
  }
  return { amount: 0, label: "" };
}

export function rewardTier(completed) {
  for (const t of REWARD_TIERS) if (completed >= t.needed) return { name: t.name, discount: t.discount };
  return { name: "Member", discount: 0 };
}

export function nextTier(completed) {
  for (const t of [...REWARD_TIERS].reverse()) {
    if (completed < t.needed) return { name: t.name, needed: t.needed, remaining: t.needed - completed };
  }
  return null;
}

/** Local calendar date as YYYY-MM-DD. */
export function localISO(date = new Date()) {
  return date.toLocaleDateString("en-CA");
}

function parseISO(s) {
  if (typeof s !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
  const d = new Date(s + "T00:00:00");
  return Number.isNaN(d.getTime()) ? null : d;
}

export function validatePeriod(start, end) {
  const s = parseISO(start);
  const e = parseISO(end);
  if (!s) throw new DemoError("'start' must be a date in YYYY-MM-DD format.", 400, "invalid_period");
  if (!e) throw new DemoError("'end' must be a date in YYYY-MM-DD format.", 400, "invalid_period");
  if (start < localISO()) throw new DemoError("Start date cannot be in the past.", 400, "invalid_period");
  if (end < start) throw new DemoError("End date must be on or after the start date.", 400, "invalid_period");
  const days = Math.round((e - s) / 86400000) + 1;
  if (days > MAX_RENTAL_DAYS) {
    throw new DemoError(`Rentals can be at most ${MAX_RENTAL_DAYS} days.`, 400, "invalid_period");
  }
  return days;
}

export function quote(item, start, end, user) {
  const days = validatePeriod(start, end);
  const rental = bestRentalPrice(days, item.daily_rate, item.weekly_rate, item.monthly_rate);

  let pct = 0;
  const labels = [];
  if (user) {
    if (user.is_student_verified) {
      pct += STUDENT_DISCOUNT_PCT;
      labels.push(`Student ${STUDENT_DISCOUNT_PCT}%`);
    }
    const tier = rewardTier(user.completed_rentals);
    if (tier.discount) {
      pct += tier.discount;
      labels.push(`${tier.name} badge ${tier.discount}%`);
    }
  }
  if (pct > MAX_TOTAL_DISCOUNT_PCT) {
    pct = MAX_TOTAL_DISCOUNT_PCT;
    labels.push(`capped at ${MAX_TOTAL_DISCOUNT_PCT}%`);
  }

  const discount = percentOf(rental, pct);
  const after = rental - discount;
  const dep = depositFor(item.market_value);
  const commission = percentOf(after, COMMISSION_PCT);
  return {
    days,
    rental_amount: rental,
    discount_amount: discount,
    discount_label: labels.join(", "),
    delivery_fee: DELIVERY_FEE,
    deposit: dep.amount,
    deposit_label: dep.label,
    total_payable: after + DELIVERY_FEE + dep.amount,
    commission,
    owner_payout: after - commission,
  };
}
