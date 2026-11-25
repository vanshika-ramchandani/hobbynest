# HobbyNest Rental Engine (Java core)

The Java component of HobbyNest. It implements the core rental rules: pricing, deposits, discounts, booking with double-booking protection, a test-mode payment gateway, the booking lifecycle and Rent-to-Buy offers. It stores data in files and exposes everything through a small HTTP API, so it can run in a container on the cloud.

It uses no runtime libraries, only the JDK: `com.sun.net.httpserver`, `java.net.http`, `java.nio.file` and `java.util.concurrent`. JUnit 6 and JaCoCo are used for testing only.

The pricing rules match the Django backend exactly, including the rounding, and tests check them against the same reference values (for example, a 7-day guitar rental at 100/500/1800 with a ₹9,000 market value costs ₹1,499 in total).

## How the syllabus requirements map to the code

| Requirement | Where |
|---|---|
| Class design | `model`, `pricing`, `payment`, `storage`, `service`, `api` packages. See [docs/UML.md](docs/UML.md) |
| Inheritance | `Equipment` (abstract) with `MusicalInstrument`, `CameraGear`, `SportsKit`, `ArtTool` and `GamingDevice`. `User` (abstract) with `Renter` and `Owner`. `HobbyNestException` with six subclasses |
| Polymorphism | Subclasses override `categorySpecificChecks()`. `CameraGear` lowers the ID-check threshold to ₹10,000. `SportsKit` caps rentals at 30 days |
| Interfaces | `Rentable`, `Inspectable`, `PricingStrategy` (`BestRatePricing`, `DailyOnlyPricing`), `PaymentGateway` (`MockPaymentGateway`), `DataStore` (`FileDataStore`) |
| Custom exceptions | `InvalidRentalPeriodException` (400), `PaymentFailedException` (402), `NotFoundException` (404), `ItemUnavailableException` (409), `InvalidStatusTransitionException` (409), `DataStoreException` (500). All are checked exceptions, and each carries its HTTP status and error code |
| Multithreading | 1. `ApiServer` serves requests on a fixed thread pool. 2. `RentalService` holds a `ReentrantLock` per item, so simultaneous bookings for the same item cannot double-book while different items book in parallel. 3. `AuditLogger` is a producer/consumer pair: a `BlockingQueue` feeds a background writer thread. 4. `OverdueMonitor` runs on a `ScheduledExecutorService`. `Renter` uses `AtomicInteger` and `volatile` fields |
| File management | `FileDataStore` saves `users.csv`, `equipment.csv` and `bookings.csv`. Writes go to a temp file first and are then moved into place. `CsvCodec` quotes fields that contain commas or quotes. `audit.log` is append-only. Data loads again on restart |

## Build, test, run

You need JDK 21 and Maven 3.9.

```bash
mvn verify                         # compile, run 85 JUnit tests, write coverage to target/site/jacoco/index.html
java -jar target/rental-engine.jar # start the API on http://localhost:8081 (seeds demo data on first run)
java -jar target/rental-engine.jar demo   # booking race + load test, then exits
```

Environment variables: `PORT` (default `8081`) and `DATA_DIR` (default `./data`).

Docker:

```bash
docker build -t hobbynest-java-engine .
docker run -p 8081:8081 -v engine-data:/data hobbynest-java-engine
```

## API

Send parameters as a query string or as a form body (`application/x-www-form-urlencoded`). Responses are JSON with the same snake_case field names as the Django API.

| Method | Path | Parameters |
|---|---|---|
| GET | `/api/health` | |
| GET | `/api/equipment` | |
| GET | `/api/equipment/{id}` | |
| GET | `/api/equipment/{id}/quote` | `start`, `end`, optional `renter` |
| GET | `/api/bookings` | `user` |
| POST | `/api/bookings` | `renter`, `equipment`, `start`, `end`, `address` |
| POST | `/api/bookings/{id}/pay` | `user`, `method` (`upi` or `card`), `details` |
| POST | `/api/bookings/{id}/cancel`, `deliver`, `return`, `complete` | `user` |
| POST | `/api/bookings/{id}/buy` | `user` (returns the Rent-to-Buy offer) |

Example:

```bash
curl "http://localhost:8081/api/equipment/1/quote?start=2026-12-01&end=2026-12-07&renter=5"
curl -X POST http://localhost:8081/api/bookings -d "renter=5&equipment=1&start=2026-12-01&end=2026-12-07&address=14 CG Road, Navrangpura, Ahmedabad"
curl -X POST http://localhost:8081/api/bookings/1/pay -d "user=5&method=upi&details=fail@test"   # 402, test decline
```

In the demo data, users 1 to 4 are owners and user 5 is `demo_student`. The API has no login because it is an internal service. In production it would sit behind the Django API, or behind an API gateway that handles authentication.

## Results at the time of writing

- 85 tests, 0 failures
- JaCoCo: 82.9% line coverage and 80.0% branch coverage overall. Each package (model, pricing, payment, storage, service, api) is at 92% or higher. The uncovered code is `Main` and the console `demo` package, which are exercised by running `demo` mode rather than by unit tests
- Booking race: 100 renters booking the same item at the same instant gives 1 booked and 99 rejected with `item_unavailable`
- Load test on a laptop: 300 requests with 25 concurrent gave 0 failures, about 400 requests per second, a p50 of 22 ms and a p95 of 443 ms. The p95 includes JVM warm-up on the first requests

## Defects found while building (for the defect log)

These are real defects found during development and testing of the whole project, not just the Java part.

| ID | Defect | Fix |
|---|---|---|
| D-01 | Django: booking an item that did not exist or had been hidden caused a server crash (500) | `create_booking` now returns 409 `item_unavailable`. Test: `test_missing_or_hidden_item_is_unavailable_not_500` |
| D-02 | Django: a renter who went back from checkout to change dates was blocked by their own unpaid booking ("already booked") | The renter's own overlapping unpaid booking is cancelled when they rebook. Test: `test_renter_can_rebook_over_own_unpaid_booking` |
| D-03 | Django: an owner could hide a listing but not make it live again (404), because hidden items were filtered out of every lookup | Owners can still see and edit their own hidden listings. Test: `test_owner_can_hide_and_relist` |
| D-04 | Frontend: the "How it works" nav link was highlighted as the current page on the home page | Hash links are never shown as active. Checked manually in the browser |
| D-05 | The Django backend used Python `round()`, which rounds halves to even (28.5 becomes 28), while Java rounds half up. A ₹570 rental with 5% student discount gave a ₹28 discount in Django and ₹29 in Java. A ₹3,250 item's deposit was ₹300 in Django and ₹350 in Java | Both services now use whole-number percentages with explicit half-up rounding (`Money.divHalfUp`, `services.div_half_up`). Regression tests were added on both sides |
| D-06 | The auto-cancel from D-02 also cancelled the renter's unpaid bookings for other, non-overlapping dates on the same item | Only overlapping unpaid bookings are cancelled. Tests: Django `test_renter_can_rebook_over_own_unpaid_booking`, Java `renterCanRebookOverOwnUnpaidAttempt` |
| D-07 | Regression: the first D-06 fix was never actually applied in Django (a scripted edit did not match). The new test assertion caught it | Applied the overlap filter. All 21 Django tests pass |
| D-08 | Java: if a rebook failed because someone else held the dates, the renter's earlier unpaid booking had already been cancelled (Django rolls this back inside a transaction) | Check availability first, cancel only once the new booking will succeed. Tests: Django `test_failed_rebook_keeps_original_unpaid_booking`, Java `failedRebookLeavesRenterOriginalAttemptIntact` |
| D-09 | Java: `demo` mode deleted every file in `DATA_DIR`, so running it in Docker against the real data volume would wipe it | Demo mode always uses a fresh temp folder and ignores `DATA_DIR` |
