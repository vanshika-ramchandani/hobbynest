// Demo-mode API: answers the same paths and returns the same JSON shapes as the Django backend,
// but runs entirely in the browser and stores data in localStorage. Used for the GitHub Pages build.
import { load, nextId, save } from "./db";
import {
  DemoError, PAYMENT_HOLD_MINUTES, SALE_COMMISSION_PCT, depositFor, localISO, nextTier, percentOf, quote,
  rewardTier,
} from "./pricing";

const STATUS_LABELS = {
  pending_payment: "Awaiting payment",
  payment_failed: "Payment failed",
  confirmed: "Confirmed",
  active: "Delivered / in use",
  returned: "Returned, awaiting inspection",
  completed: "Completed",
  cancelled: "Cancelled",
};
const BLOCKING = ["pending_payment", "confirmed", "active", "returned"];
const STUDENT_DOMAINS = [".edu", ".ac.in", ".edu.in"];

const fail = (message, status = 400, code = "bad_request") => {
  throw new DemoError(message, status, code);
};

// ---------- auth helpers ----------

async function hashPassword(username, password) {
  const data = new TextEncoder().encode(`hobbynest-demo:${username}:${password}`);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

const tokenFor = (user) => ({ access: `demo-${user.id}`, refresh: `demo-${user.id}` });

function currentUser(token) {
  const m = /^demo-(\d+)$/.exec(token || "");
  return m ? load().users.find((u) => u.id === Number(m[1])) || null : null;
}

function requireUser(token) {
  const u = currentUser(token);
  if (!u) fail("Authentication credentials were not provided.", 401, "not_authenticated");
  return u;
}

function userJson(u) {
  const { password_hash: _p, consent_given_at: _c, ...rest } = u;
  return { ...rest, reward_tier: rewardTier(u.completed_rentals), next_tier: nextTier(u.completed_rentals) };
}

// ---------- serializers ----------

function equipmentJson(e) {
  const db = load();
  const cat = db.categories.find((c) => c.id === e.category);
  const owner = db.users.find((u) => u.id === e.owner);
  const reviews = db.reviews.filter((r) => r.equipment === e.id);
  const avg = reviews.length ? reviews.reduce((s, r) => s + r.rating, 0) / reviews.length : null;
  return {
    ...e,
    category_name: cat?.name,
    category_slug: cat?.slug,
    owner_name: owner ? `${owner.first_name} ${owner.last_name}`.trim() || owner.username : "",
    deposit: depositFor(e.market_value),
    rating: avg ? Math.round(avg * 10) / 10 : null,
    review_count: reviews.length,
  };
}

function reviewJson(r) {
  const author = load().users.find((u) => u.id === r.author);
  return { id: r.id, booking: r.booking, rating: r.rating, comment: r.comment, author_name: author?.first_name || author?.username, created_at: r.created_at };
}

function purchaseJson(p) {
  const db = load();
  return {
    ...p,
    equipment_title: db.equipment.find((e) => e.id === p.equipment)?.title,
    buyer_name: db.users.find((u) => u.id === p.buyer)?.username,
  };
}

function bookingJson(b) {
  const db = load();
  const review = db.reviews.find((r) => r.booking === b.id);
  const pr = db.purchaseRequests.find((p) => p.booking === b.id);
  return {
    ...b,
    equipment_detail: equipmentJson(db.equipment.find((e) => e.id === b.equipment)),
    status_label: STATUS_LABELS[b.status],
    renter_name: db.users.find((u) => u.id === b.renter)?.username,
    payments: db.payments.filter((p) => p.booking === b.id),
    review: review ? reviewJson(review) : null,
    purchase_request: pr ? purchaseJson(pr) : null,
  };
}

// ---------- availability ----------

function isBlocking(b) {
  if (!BLOCKING.includes(b.status)) return false;
  if (b.status === "pending_payment") {
    return Date.now() - new Date(b.created_at).getTime() < PAYMENT_HOLD_MINUTES * 60000;
  }
  return true;
}

const overlaps = (b, start, end) => b.start_date <= end && b.end_date >= start;

function isAvailable(equipmentId, start, end, excludeId = null) {
  return !load().bookings.some(
    (b) => b.equipment === equipmentId && b.id !== excludeId && isBlocking(b) && overlaps(b, start, end)
  );
}

// ---------- validation ----------

function validateListing(f) {
  const num = (k) => (f[k] === null || f[k] === undefined || f[k] === "" ? null : Number(f[k]));
  const d = num("daily_rate");
  const w = num("weekly_rate");
  const m = num("monthly_rate");
  if (!String(f.title || "").trim()) fail("title: This field is required.");
  if (!String(f.description || "").trim()) fail("description: This field is required.");
  if (d === null || w === null || m === null || num("market_value") === null) fail("Rates and item value are required.");
  if (d < 10) fail("daily_rate: Daily rate must be at least Rs 10.");
  if (w > d * 7) fail("weekly_rate: Weekly rate should not exceed 7 x daily rate.");
  if (m > w * 5) fail("monthly_rate: Monthly rate looks too high compared to weekly.");
  if (f.rent_to_buy && !num("sale_price")) fail("sale_price: Set a sale price to enable Rent-to-Buy.");
}

// ---------- router ----------

export async function mockApi(fullPath, { method = "GET", body } = {}, token) {
  const db = load();
  const [rawPath, qs = ""] = fullPath.split("?");
  const path = rawPath.endsWith("/") ? rawPath : rawPath + "/";
  const q = new URLSearchParams(qs);
  const data = body || {};
  let m;

  // Health
  if (path === "/health/") return { status: "ok", database: "browser-localStorage", mode: "demo" };

  // Auth
  if (path === "/auth/register/" && method === "POST") {
    if (data.consent !== true) fail("consent: You must agree to the Terms and Privacy Policy to register.");
    const username = String(data.username || "").trim();
    const email = String(data.email || "").trim().toLowerCase();
    if (!username) fail("username: This field is required.");
    if (db.users.some((u) => u.username.toLowerCase() === username.toLowerCase())) fail("username: A user with that username already exists.");
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail("email: Enter a valid email address.");
    if (db.users.some((u) => u.email === email)) fail("email: An account with this email already exists.");
    const phone = String(data.phone || "").replace(/\D/g, "");
    if (data.phone && phone.length !== 10) fail("phone: Enter a 10-digit mobile number.");
    const pw = String(data.password || "");
    if (pw.length < 8) fail("password: This password is too short. It must contain at least 8 characters.");
    if (/^\d+$/.test(pw)) fail("password: This password is entirely numeric.");
    const user = {
      id: nextId("user"), username, email, first_name: data.first_name || "", last_name: data.last_name || "",
      role: data.role === "owner" ? "owner" : "renter", phone, city: "Ahmedabad", college_email: "",
      is_student_verified: false, completed_rentals: 0, reward_points: 0, is_staff: false,
      password_hash: await hashPassword(username, pw), consent_given_at: new Date().toISOString(),
    };
    db.users.push(user);
    save();
    return { user: userJson(user), ...tokenFor(user) };
  }

  if (path === "/auth/login/" && method === "POST") {
    const user = db.users.find((u) => u.username === String(data.username || "").trim());
    // Demo accounts (no password) can be opened with any password; see the buttons on the login page.
    if (!user || (user.password_hash && user.password_hash !== (await hashPassword(user.username, String(data.password || ""))))) {
      fail("No active account found with the given credentials", 401, "no_active_account");
    }
    return tokenFor(user);
  }

  if (path === "/auth/me/") {
    const user = requireUser(token);
    if (method === "PATCH") {
      ["first_name", "last_name", "phone", "city"].forEach((k) => {
        if (k in data) user[k] = String(data[k]);
      });
      save();
    }
    return userJson(user);
  }

  if (path === "/auth/verify-student/" && method === "POST") {
    const user = requireUser(token);
    const email = String(data.college_email || "").trim().toLowerCase();
    if (!email.includes("@") || !STUDENT_DOMAINS.some((d) => email.endsWith(d))) {
      fail("Use your college email (ending in .ac.in, .edu or .edu.in).");
    }
    user.college_email = email;
    user.is_student_verified = true;
    save();
    return userJson(user);
  }

  // Categories
  if (path === "/categories/") {
    return db.categories.map((c) => ({
      ...c,
      item_count: db.equipment.filter((e) => e.category === c.id && e.is_active).length,
    }));
  }

  // Equipment
  if (path === "/equipment/" && method === "GET") {
    const me = currentUser(token);
    if (q.get("mine") === "1" && me) return db.equipment.filter((e) => e.owner === me.id).map(equipmentJson);
    let list = db.equipment.filter((e) => e.is_active).map(equipmentJson);
    const text = (q.get("q") || "").trim().toLowerCase().slice(0, 100);
    if (text) list = list.filter((e) => [e.title, e.description, e.category_name].some((s) => s.toLowerCase().includes(text)));
    if (q.get("category")) list = list.filter((e) => e.category_slug === q.get("category"));
    if (/^\d+$/.test(q.get("max_daily") || "")) list = list.filter((e) => e.daily_rate <= Number(q.get("max_daily")));
    if (q.get("rent_to_buy") === "1") list = list.filter((e) => e.rent_to_buy);
    if (q.get("beginner") === "1") list = list.filter((e) => e.beginner_friendly);
    const sort = q.get("sort");
    if (sort === "price") list.sort((a, b) => a.daily_rate - b.daily_rate);
    else if (sort === "-price") list.sort((a, b) => b.daily_rate - a.daily_rate);
    else list.sort((a, b) => b.id - a.id);
    return list;
  }

  if (path === "/equipment/" && method === "POST") {
    const user = requireUser(token);
    if (user.role !== "owner") fail("Switch to an owner account to list equipment.", 403, "permission_denied");
    validateListing(data);
    const item = {
      id: nextId("equipment"), owner: user.id, category: Number(data.category), title: String(data.title).trim(),
      description: String(data.description).trim(), specs: data.specs || "", condition: data.condition || "good",
      daily_rate: Number(data.daily_rate), weekly_rate: Number(data.weekly_rate), monthly_rate: Number(data.monthly_rate),
      market_value: Number(data.market_value), rent_to_buy: !!data.rent_to_buy,
      sale_price: data.rent_to_buy ? Number(data.sale_price) : null, beginner_friendly: !!data.beginner_friendly,
      image_url: "", location: "Ahmedabad", is_active: true, created_at: new Date().toISOString(),
    };
    db.equipment.push(item);
    save();
    return equipmentJson(item);
  }

  if ((m = /^\/equipment\/(\d+)\/(quote|booked|reviews)?\/?$/.exec(path))) {
    const me = currentUser(token);
    const item = db.equipment.find((e) => e.id === Number(m[1]));
    if (!item || (!item.is_active && item.owner !== me?.id)) fail("No Equipment matches the given query.", 404, "not_found");
    const sub = m[2];
    if (!sub && method === "PATCH") {
      if (!me || me.id !== item.owner) fail("You do not have permission to perform this action.", 403, "permission_denied");
      const merged = { ...item, ...data };
      validateListing(merged);
      Object.assign(item, data);
      save();
      return equipmentJson(item);
    }
    if (!sub) return equipmentJson(item);
    if (sub === "quote") {
      const start = q.get("start");
      const end = q.get("end");
      return { ...quote(item, start, end, me), available: isAvailable(item.id, start, end) };
    }
    if (sub === "booked") {
      const today = localISO();
      return db.bookings
        .filter((b) => b.equipment === item.id && isBlocking(b) && b.end_date >= today)
        .sort((a, b) => a.start_date.localeCompare(b.start_date))
        .map((b) => ({ start_date: b.start_date, end_date: b.end_date }));
    }
    return db.reviews.filter((r) => r.equipment === item.id).map(reviewJson);
  }

  // Bookings
  if (path === "/bookings/" && method === "GET") {
    const user = requireUser(token);
    const owned = new Set(db.equipment.filter((e) => e.owner === user.id).map((e) => e.id));
    const list = db.bookings.filter((b) =>
      q.get("as") === "owner" ? owned.has(b.equipment) : b.renter === user.id || owned.has(b.equipment)
    );
    return list.sort((a, b) => b.id - a.id).map(bookingJson);
  }

  if (path === "/bookings/" && method === "POST") {
    const user = requireUser(token);
    const address = String(data.delivery_address || "").trim();
    if (address.length < 10) fail("Please enter a complete delivery address.", 400, "invalid_period");
    const item = db.equipment.find((e) => e.id === Number(data.equipment) && e.is_active);
    if (!item) fail("This item is no longer available.", 409, "item_unavailable");
    if (item.owner === user.id) fail("You cannot rent your own listing.", 400, "invalid_period");
    const { start_date: start, end_date: end } = data;
    const qt = quote(item, start, end, user);
    // The renter's own overlapping unpaid attempt should not block a rebook (same rule as Django).
    const ownUnpaid = db.bookings.filter(
      (b) => b.equipment === item.id && b.renter === user.id && b.status === "pending_payment" && overlaps(b, start, end)
    );
    const clash = db.bookings.some(
      (b) => b.equipment === item.id && !ownUnpaid.includes(b) && isBlocking(b) && overlaps(b, start, end)
    );
    if (clash) fail("This item is already booked for the selected dates.", 409, "item_unavailable");
    ownUnpaid.forEach((b) => (b.status = "cancelled"));
    const { deposit_label: _d, ...amounts } = qt;
    const booking = {
      id: nextId("booking"), renter: user.id, equipment: item.id, start_date: start, end_date: end,
      status: "pending_payment", delivery_address: address, deposit_refunded: false,
      created_at: new Date().toISOString(), ...amounts,
    };
    db.bookings.push(booking);
    save();
    return bookingJson(booking);
  }

  if ((m = /^\/bookings\/(\d+)\/(?:(pay|cancel|deliver|return|complete|review|buy)\/)?$/.exec(path))) {
    const user = requireUser(token);
    const b = db.bookings.find((x) => x.id === Number(m[1]));
    const item = b && db.equipment.find((e) => e.id === b.equipment);
    if (!b || (b.renter !== user.id && item.owner !== user.id)) fail("Not found.", 404, "not_found");
    const action = m[2];
    const isRenter = b.renter === user.id;
    const isOwner = item.owner === user.id;

    if (!action) return bookingJson(b);

    if (action === "pay") {
      if (!isRenter) fail("Only the renter can pay for this booking.", 403, "permission_denied");
      if (!["pending_payment", "payment_failed"].includes(b.status)) {
        fail("This booking has already been paid or closed.", 409, "invalid_transition");
      }
      let declined = "";
      if (data.method === "upi") {
        const upi = String(data.upi_id || "");
        if (!upi.includes("@") || upi.length < 5) fail("Enter a valid UPI ID, for example name@okbank.", 402, "payment_failed");
        if (upi.toLowerCase() === "fail@test") declined = "UPI request declined (test failure ID).";
      } else if (data.method === "card") {
        const digits = String(data.card_number || "").replace(/\D/g, "");
        if (digits.length < 12 || digits.length > 19) fail("Enter a valid card number.", 402, "payment_failed");
        if (digits.endsWith("0002")) declined = "Card declined by issuer (test failure card).";
      } else {
        fail("Unsupported payment method.", 402, "payment_failed");
      }
      const reference = "TEST-" + Math.random().toString(16).slice(2, 12).toUpperCase().padEnd(10, "0");
      db.payments.push({
        id: nextId("payment"), booking: b.id, method: data.method, amount: b.total_payable,
        status: declined ? "failed" : "success", reference, message: declined || "Paid (test mode)",
        created_at: new Date().toISOString(),
      });
      if (declined) {
        b.status = "payment_failed";
        save();
        fail(declined, 402, "payment_failed");
      }
      if (!isAvailable(b.equipment, b.start_date, b.end_date, b.id)) {
        save();
        fail("This item is already booked for the selected dates.", 409, "item_unavailable");
      }
      b.status = "confirmed";
      save();
      return { reference, booking: bookingJson(b) };
    }

    const transitions = {
      cancel: [["pending_payment", "payment_failed", "confirmed"], "cancelled", "renter"],
      deliver: [["confirmed"], "active", "owner"],
      return: [["active"], "returned", "renter"],
      complete: [["returned"], "completed", "owner"],
    };
    if (transitions[action]) {
      const [from, to, who] = transitions[action];
      if (!(who === "renter" ? isRenter : isOwner)) fail(`Only the ${who} can perform '${action}'.`, 409, "invalid_transition");
      if (!from.includes(b.status)) fail(`Cannot ${action} a booking that is '${STATUS_LABELS[b.status]}'.`, 409, "invalid_transition");
      b.status = to;
      if (action === "cancel") {
        db.payments.filter((p) => p.booking === b.id && p.status === "success").forEach((p) => (p.status = "refunded"));
      }
      if (action === "complete") {
        b.deposit_refunded = true;
        const renter = db.users.find((u) => u.id === b.renter);
        renter.completed_rentals += 1;
        renter.reward_points += Math.max(10, Math.floor(b.rental_amount / 100));
      }
      save();
      return bookingJson(b);
    }

    if (action === "review") {
      if (!isRenter) fail("Only the renter can review this rental.", 403, "permission_denied");
      if (b.status !== "completed") fail("You can review only after the rental is completed.", 409, "invalid_transition");
      if (db.reviews.some((r) => r.booking === b.id)) fail("You have already reviewed this rental.", 409, "invalid_transition");
      const rating = Number(data.rating);
      if (!(rating >= 1 && rating <= 5)) fail("rating: Rating must be between 1 and 5.");
      const review = {
        id: nextId("review"), booking: b.id, equipment: b.equipment, author: user.id, rating,
        comment: String(data.comment || "").slice(0, 500), created_at: new Date().toISOString(),
      };
      db.reviews.push(review);
      save();
      return reviewJson(review);
    }

    // buy (Rent-to-Buy)
    if (!isRenter) fail("Only the renter can request to buy.", 403, "permission_denied");
    if (!item.rent_to_buy || !item.sale_price) fail("The owner has not enabled Rent-to-Buy for this item.", 409, "invalid_transition");
    if (!["active", "returned", "completed"].includes(b.status)) fail("You can request to buy once you have received the item.", 409, "invalid_transition");
    if (db.purchaseRequests.some((p) => p.booking === b.id)) fail("A purchase request already exists for this rental.", 409, "invalid_transition");
    const credit = Math.min(Math.floor((b.rental_amount - b.discount_amount) / 2), Math.floor(item.sale_price / 2));
    const finalPrice = item.sale_price - credit;
    const pr = {
      id: nextId("purchase"), booking: b.id, equipment: item.id, buyer: user.id, sale_price: item.sale_price,
      rental_credit: credit, final_price: finalPrice, commission: percentOf(finalPrice, SALE_COMMISSION_PCT),
      status: "pending", created_at: new Date().toISOString(),
    };
    db.purchaseRequests.push(pr);
    save();
    return purchaseJson(pr);
  }

  // Rent-to-Buy requests
  if (path === "/purchase-requests/") {
    const user = requireUser(token);
    const owned = new Set(db.equipment.filter((e) => e.owner === user.id).map((e) => e.id));
    return db.purchaseRequests.filter((p) => p.buyer === user.id || owned.has(p.equipment)).map(purchaseJson);
  }

  if ((m = /^\/purchase-requests\/(\d+)\/(accept|decline)\/$/.exec(path)) && method === "POST") {
    const user = requireUser(token);
    const pr = db.purchaseRequests.find((p) => p.id === Number(m[1]));
    const item = pr && db.equipment.find((e) => e.id === pr.equipment);
    if (!pr) fail("Not found.", 404, "not_found");
    if (item.owner !== user.id) fail("Only the owner can respond.", 403, "permission_denied");
    if (pr.status !== "pending") fail("This request has already been answered.", 409, "invalid_transition");
    pr.status = m[2] === "accept" ? "accepted" : "declined";
    if (m[2] === "accept") item.is_active = false;
    save();
    return purchaseJson(pr);
  }

  // Owner dashboard numbers
  if (path === "/owner/stats/") {
    const user = requireUser(token);
    const owned = new Set(db.equipment.filter((e) => e.owner === user.id).map((e) => e.id));
    const mine = db.bookings.filter((b) => owned.has(b.equipment));
    return {
      listings: db.equipment.filter((e) => e.owner === user.id && e.is_active).length,
      bookings: mine.length,
      active: mine.filter((b) => ["confirmed", "active", "returned"].includes(b.status)).length,
      earned: mine.filter((b) => b.status === "completed").reduce((s, b) => s + b.owner_payout, 0),
      pending_buy_requests: db.purchaseRequests.filter((p) => owned.has(p.equipment) && p.status === "pending").length,
    };
  }

  fail(`Not found: ${method} ${rawPath}`, 404, "not_found");
}
