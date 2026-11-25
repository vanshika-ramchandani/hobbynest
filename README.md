# HobbyNest

Rent it. Try it. Love it.

HobbyNest is a rental marketplace for hobby and sports equipment, piloting in Ahmedabad. Students and beginners rent guitars, cameras, sports kits and creative tools from individual owners, studios and academies. On Rent-to-Buy items they can then buy the exact item they tried, with part of the rent credited.

This repository is the working MVP for the B.Tech 5th semester integrated assignment.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, React Router 7, Tailwind CSS 4, Vite |
| Backend | Python 3.12, Django 6, Django REST Framework, SimpleJWT |
| Core engine | Java 21 (no runtime libraries), JUnit 6, JaCoCo. See [java-core/README.md](java-core/README.md) |
| Database | PostgreSQL (via `DATABASE_URL`), SQLite fallback for local dev |
| Packaging | Docker, docker compose, nginx |
| CI | GitHub Actions (tests, build, Docker images) |

## Features in the MVP

- Register (with an explicit, unticked consent checkbox) and log in (JWT)
- Renter and owner roles
- Browse with search, category, price, Rent-to-Buy and beginner filters
- Listing detail with a live price breakdown. The cheapest mix of daily, weekly and monthly rates is chosen automatically
- Transparent pricing: rent, discount, delivery fee and refundable deposit are all shown before payment. The deposit tier depends on item value
- Booking with double-booking prevention (row lock plus overlap check). Unpaid bookings hold the dates for 15 minutes. Note: `select_for_update` is a no-op on SQLite, so the concurrency guarantee holds on PostgreSQL only; run load tests against Postgres
- Mock payment gateway in test mode (UPI or card) with deterministic failure triggers
- Booking lifecycle: pending payment, confirmed, delivered, returned, completed (deposit refunded), cancelled
- Rewards: Bronze, Silver and Gold tiers (3, 7 and 15 completed rentals) give 5%, 10% and 15% off. Student verification via college email gives 5%. Total discount is capped at 20%
- Reviews only from renters with a completed rental (no fake reviews)
- Rent-to-Buy requests with 50% rental credit, plus owner accept or decline
- Owner dashboard: stats, add listing, mark delivered, confirm return condition, handle buy requests
- Policies page (privacy, terms, refunds, damage, Rent-to-Buy)
- `/api/health/` endpoint with DB check, uptime and request/error counters, plus structured request logs

## Run locally (no Docker)

Backend:

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate          # Windows  (source .venv/bin/activate on macOS/Linux)
pip install -r requirements.txt
copy .env.example .env          # then edit values (cp on macOS/Linux)
python manage.py migrate
python manage.py seed           # demo data; uses DEMO_PASSWORD from .env
python manage.py runserver
```

Frontend (in a second terminal):

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to Django on port 8000.

Demo accounts (password is whatever you set as `DEMO_PASSWORD`):

| Username | Role |
|---|---|
| `demo_student` | Renter, verified student |
| `kavya_owner` | Owner (gaming, art) |
| `rhythm_studio` | Owner (music) |
| `lensbox` | Owner (photography) |
| `smash_academy` | Owner (sports) |
| `admin` | Django admin at `/admin/` |

### Test payments

No real money moves. Any UPI ID like `name@okaxis` succeeds. `fail@test` is declined. Any 12 to 19 digit card succeeds unless it ends in `0002`.

## Run with Docker (PostgreSQL)

Create a `.env` file next to `docker-compose.yml`:

```
POSTGRES_PASSWORD=choose-one
DJANGO_SECRET_KEY=a-long-random-string
DEMO_PASSWORD=choose-one
```

Then:

```bash
docker compose up --build
docker compose exec backend python manage.py seed
```

Open http://localhost:8080.

## Tests

```bash
cd backend && python manage.py test      # Django: 21 tests
cd java-core && mvn verify               # Java: 85 JUnit tests + JaCoCo coverage report
```

These cover pricing (equivalence classes and boundaries for deposit tiers and rental length), overlap rejection, cancellation releasing dates, payment hold expiry, failed then successful payment, the full lifecycle with rewards and reviews, discount stacking and the cap, the no-self-rental rule, and registration consent.

## API overview

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register/` | Create account (requires `consent: true`) |
| POST | `/api/auth/login/` | Get JWT tokens |
| GET/PATCH | `/api/auth/me/` | Current user, reward tier |
| POST | `/api/auth/verify-student/` | Verify college email |
| GET | `/api/categories/` | Categories with item counts |
| GET/POST | `/api/equipment/` | List (filters: `q`, `category`, `max_daily`, `rent_to_buy`, `beginner`, `sort`) or create |
| GET | `/api/equipment/{id}/quote/?start=&end=` | Price breakdown and availability |
| GET | `/api/equipment/{id}/booked/` | Booked date ranges |
| GET/POST | `/api/bookings/` | List or create bookings |
| POST | `/api/bookings/{id}/pay/` | Mock payment |
| POST | `/api/bookings/{id}/{cancel,deliver,return,complete}/` | Status transitions |
| POST | `/api/bookings/{id}/review/` | Review after completion |
| POST | `/api/bookings/{id}/buy/` | Rent-to-Buy request |
| POST | `/api/purchase-requests/{id}/{accept,decline}/` | Owner response |
| GET | `/api/owner/stats/` | Owner dashboard numbers |
| GET | `/api/health/` | Health and usage metrics |

## Security notes

- No secrets in the repo. Everything comes from environment variables (`.env` is git-ignored; see `.env.example`)
- Passwords are hashed by Django (PBKDF2). Authentication uses short-lived JWT access tokens
- Object-level permission checks: only the renter can pay, cancel or return; only the owner can deliver, complete or answer buy requests
- The ORM prevents SQL injection. Inputs are validated server-side (dates, address, phone, rates, rating range)
- With `DJANGO_DEBUG=0`: secure cookies, HSTS and proxy HTTPS headers are enabled
- Card numbers are never stored. Only a test reference is saved

## Project structure

```
backend/
  accounts/    custom User (role, student verification, rewards)
  rentals/     models, services (pricing, booking, payments), views, tests, seed command
  config/      settings, URLs, request logging middleware
java-core/
  src/main/java/com/hobbynest/   pricing, booking, payments, file storage, HTTP API (Java 21)
  src/test/java/                 85 JUnit tests
  docs/UML.md                    class and state diagrams
frontend/
  src/pages/   Home, Browse, Detail, Checkout, Bookings, Profile, OwnerDashboard, Auth, Policies
  src/components/
docker-compose.yml
.github/workflows/ci.yml
```

## Credits

Icons: [Lucide](https://lucide.dev) (ISC licence). Font: Plus Jakarta Sans (Google Fonts, OFL). Equipment artwork is generated in CSS, with no third-party photos.
