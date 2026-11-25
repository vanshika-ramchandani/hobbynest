// In-browser data store for demo mode (GitHub Pages). Everything lives in localStorage.
import { localISO, quote } from "./pricing";

const KEY = "hn_demo_db_v1";

const CATEGORIES = [
  ["Musical Instruments", "music", "music", "Guitars, keyboards, ukuleles and more"],
  ["Photography", "photography", "camera", "Cameras, lenses, tripods and lights"],
  ["Sports", "sports", "trophy", "Badminton, cricket, table tennis and tennis"],
  ["Art & Creative", "art", "palette", "Easels, pottery wheels and craft tools"],
  ["Gaming & Tech", "gaming", "gamepad", "Consoles, VR and DJ controllers"],
];

const OWNERS = [
  ["rhythm_studio", "Rhythm", "Music Studio", "rhythm.studio@hobbynest.test"],
  ["lensbox", "LensBox", "Rentals", "lensbox@hobbynest.test"],
  ["smash_academy", "Smash", "Sports Academy", "smash.academy@hobbynest.test"],
  ["kavya_owner", "Kavya", "Shah", "kavya.owner@hobbynest.test"],
];

// [owner idx, category slug, title, description, specs, condition, daily, weekly, monthly, value, sale price, beginner]
const ITEMS = [
  [0, "music", "Yamaha F310 Acoustic Guitar", "Full-size acoustic guitar, perfect for beginners learning chords. Comes with gig bag, capo and picks.", "Spruce top, steel strings, 6 strings", "good", 120, 600, 1800, 9000, 7000, true],
  [0, "music", "Casio CT-S300 Keyboard (61 keys)", "Portable keyboard with touch-sensitive keys and built-in lessons. Adapter and stand included.", "61 keys, 400 tones, lesson mode", "new", 150, 800, 2400, 11000, 8500, true],
  [0, "music", "Kala Soprano Ukulele", "Lightweight ukulele, easiest string instrument to start with. Tuner included.", "Mahogany, Aquila strings", "good", 60, 300, 900, 3500, 2600, true],
  [0, "music", "Pioneer DDJ-400 DJ Controller", "2-channel rekordbox controller. Try DJing before investing. Headphones included.", "2 decks, built-in mixer, USB", "good", 450, 2200, 6500, 28000, null, false],
  [1, "photography", "Canon EOS 1500D + 18-55mm Kit", "Entry-level DSLR ideal for learning manual photography. Two batteries and 32GB card.", "24.1MP APS-C, Full HD video", "good", 500, 2500, 7500, 32000, 26000, true],
  [1, "photography", "Sony A7 III Mirrorless (body)", "Full-frame mirrorless for creators and freelancers. ID verification required.", "24.2MP full frame, 4K video, IBIS", "new", 1800, 9500, 28000, 140000, null, false],
  [1, "photography", "DJI Osmo Mobile 6 Gimbal", "Smartphone gimbal for smooth reels and vlogs.", "3-axis, ActiveTrack 6.0", "good", 200, 1000, 3000, 13000, 10000, true],
  [1, "photography", "Godox SL60W LED Light + Softbox", "Continuous studio light for product shoots and portraits.", "60W, 5600K, Bowens mount", "fair", 250, 1200, 3500, 9000, null, false],
  [2, "sports", "Yonex Badminton Kit (2 rackets + shuttles)", "Two Yonex Nanoray rackets and a tube of nylon shuttles.", "Nanoray series, Mavis 350 shuttles", "good", 80, 400, 1200, 4000, null, true],
  [2, "sports", "SS Cricket Kit (Full, adult)", "Bat, pads, gloves, helmet and kit bag. Sanitised after every rental.", "English willow bat, size SH", "good", 250, 1300, 3800, 15000, null, false],
  [2, "sports", "Stag Table Tennis Set", "Two rackets, clamp-on net and 6 balls. Fits any table.", "Stag Iconic, 3-star balls", "new", 50, 250, 700, 2200, 1800, true],
  [2, "sports", "Head Tennis Racket Pair", "Two Head Ti racket with cover and 3 balls.", "Titanium frame, 4 3/8 grip", "good", 100, 500, 1500, 6000, null, true],
  [3, "art", "Studio Easel + Acrylic Starter Set", "Wooden H-frame easel with 24 acrylic colours, brushes and 3 canvases.", "Adjustable up to 150cm", "good", 90, 450, 1300, 5000, null, true],
  [3, "art", "Electric Pottery Wheel", "Quiet pottery wheel with splash pan. Clay sold separately.", "350W, 25cm wheel head, 0-300 rpm", "good", 300, 1500, 4500, 18000, 14000, false],
  [3, "gaming", "PlayStation 5 + 2 Controllers", "PS5 disc edition with two DualSense controllers and 3 games.", "825GB SSD, 4K output", "good", 600, 3200, 9500, 52000, null, false],
  [3, "gaming", "Meta Quest 3 VR Headset", "Standalone VR headset. Fitness, games and creative apps preinstalled.", "128GB, mixed reality", "new", 550, 2800, 8000, 48000, 40000, false],
];

const REVIEWS = [
  [0, 5, "Guitar was in great shape and the capo was a nice touch. Bought my own after 3 weeks."],
  [4, 5, "Perfect for my first photography class. Owner explained all the settings."],
  [8, 4, "Rackets were good, shuttles slightly used. Delivery on time."],
  [14, 5, "Weekend PS5 rental for a hostel tournament. Smooth pickup and return."],
];

function newUser(id, username, first, last, email, role, extra = {}) {
  return {
    id, username, first_name: first, last_name: last, email, role,
    phone: "", city: "Ahmedabad", college_email: "", is_student_verified: false,
    completed_rentals: 0, reward_points: 0, is_staff: false, password_hash: null,
    consent_given_at: null, ...extra,
  };
}

function seed() {
  const now = new Date().toISOString();
  const db = {
    seq: { user: 0, equipment: 0, booking: 0, payment: 0, review: 0, purchase: 0 },
    users: [], categories: [], equipment: [], bookings: [], payments: [], reviews: [], purchaseRequests: [],
  };
  const next = (k) => ++db.seq[k];

  CATEGORIES.forEach(([name, slug, icon, description], i) => db.categories.push({ id: i + 1, name, slug, icon, description }));
  const catId = (slug) => db.categories.find((c) => c.slug === slug).id;

  // Demo accounts have no password: they are opened with the one-click buttons on the login page.
  const owners = OWNERS.map(([u, f, l, e]) => {
    const user = newUser(next("user"), u, f, l, e, "owner");
    db.users.push(user);
    return user;
  });
  db.users.push(newUser(next("user"), "demo_student", "Aarav", "Patel", "student@hobbynest.test", "renter",
    { college_email: "aarav@ldce.ac.in", is_student_verified: true }));
  const reviewers = ["Diya", "Kabir", "Meera", "Rohan"].map((n, i) => {
    const user = newUser(next("user"), `renter${i + 1}`, n, "", `renter${i + 1}@hobbynest.test`, "renter");
    db.users.push(user);
    return user;
  });

  const items = ITEMS.map(([o, cat, title, description, specs, condition, d, w, m, value, sale, beginner]) => {
    const item = {
      id: next("equipment"), owner: owners[o].id, category: catId(cat), title, description, specs, condition,
      daily_rate: d, weekly_rate: w, monthly_rate: m, market_value: value, rent_to_buy: sale !== null,
      sale_price: sale, beginner_friendly: beginner, image_url: "", location: "Ahmedabad", is_active: true,
      created_at: now,
    };
    db.equipment.push(item);
    return item;
  });

  // Past completed rentals, so ratings show on the catalogue.
  REVIEWS.forEach(([idx, rating, comment], i) => {
    const item = items[idx];
    const reviewer = reviewers[i];
    const start = new Date();
    start.setDate(start.getDate() - 40);
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    // Quote as of the start date (validatePeriod rejects past dates, so compute directly).
    const q = pastQuote(item, localISO(start), localISO(end));
    const booking = {
      id: next("booking"), renter: reviewer.id, equipment: item.id, start_date: localISO(start), end_date: localISO(end),
      status: "completed", delivery_address: "Demo address, Navrangpura, Ahmedabad", deposit_refunded: true,
      created_at: start.toISOString(), ...q,
    };
    db.bookings.push(booking);
    db.reviews.push({ id: next("review"), booking: booking.id, equipment: item.id, author: reviewer.id, rating, comment, created_at: now });
    reviewer.completed_rentals += 1;
  });
  return db;
}

function pastQuote(item, start, end) {
  // Reuse the real quote by temporarily shifting dates into the future, then copy the amounts.
  const offset = 60;
  const shift = (iso) => {
    const d = new Date(iso + "T00:00:00");
    d.setDate(d.getDate() + offset);
    return localISO(d);
  };
  const { deposit_label: _ignored, ...q } = quote(item, shift(start), shift(end), null);
  return q;
}

let cache = null;

export function load() {
  if (cache) return cache;
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      cache = JSON.parse(raw);
      return cache;
    }
  } catch {
    /* storage blocked or corrupt: fall back to a fresh seed */
  }
  cache = seed();
  save();
  return cache;
}

export function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(cache));
  } catch {
    /* private mode or quota: keep working in memory */
  }
}

export function nextId(kind) {
  const db = load();
  db.seq[kind] += 1;
  return db.seq[kind];
}

export function resetDemo() {
  try {
    localStorage.removeItem(KEY);
    localStorage.removeItem("hn_access");
    localStorage.removeItem("hn_refresh");
  } catch {
    /* ignore */
  }
  cache = null;
}
