package com.hobbynest.demo;

import com.hobbynest.exception.ItemUnavailableException;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.Renter;
import com.hobbynest.service.RentalService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Console demos for the viva: a booking race and a small HTTP load test. */
public final class ConcurrencyDemo {

    private ConcurrencyDemo() {
    }

    /** N renters try to book the same item for the same dates at the same instant. Exactly one must win. */
    public static int[] bookingRace(RentalService service, int renters) throws Exception {
        Equipment item = service.listEquipment().get(0);
        LocalDate start = service.today().plusDays(30);
        LocalDate end = start.plusDays(3);
        List<Renter> people = new ArrayList<>();
        for (int i = 0; i < renters; i++) {
            people.add(service.addRenter("race_" + System.nanoTime() + "_" + i, "race" + i + "@hobbynest.test", false));
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(renters, 32));
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger otherErrors = new AtomicInteger();
        for (Renter r : people) {
            pool.submit(() -> {
                go.await();
                try {
                    service.book(r.getId(), item.getId(), start, end, "Race test address, Ahmedabad");
                    won.incrementAndGet();
                } catch (ItemUnavailableException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                }
                return null;
            });
        }
        go.countDown(); // release every thread at once
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.printf("Booking race on '%s': %d renters -> %d booked, %d rejected (item_unavailable), %d other%n",
                item.getTitle(), renters, won.get(), rejected.get(), otherErrors.get());
        return new int[]{won.get(), rejected.get(), otherErrors.get()};
    }

    /** Fires concurrent GET requests at a running server and reports latency percentiles. */
    public static void loadTest(String baseUrl, int totalRequests, int concurrency) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<long[]>> futures = new ArrayList<>();
        String[] paths = {"/api/health", "/api/equipment",
                "/api/equipment/1/quote?start=" + LocalDate.now().plusDays(5) + "&end=" + LocalDate.now().plusDays(11)};
        long t0 = System.nanoTime();
        for (int i = 0; i < totalRequests; i++) {
            String path = paths[i % paths.length];
            futures.add(pool.submit(() -> {
                long s = System.nanoTime();
                HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
                return new long[]{System.nanoTime() - s, res.statusCode()};
            }));
        }
        List<Long> times = new ArrayList<>();
        int failures = 0;
        for (Future<long[]> f : futures) {
            long[] r = f.get();
            times.add(r[0]);
            if (r[1] >= 400) {
                failures++;
            }
        }
        double totalMs = (System.nanoTime() - t0) / 1e6;
        pool.shutdown();
        Collections.sort(times);
        System.out.printf("Load test: %d requests, %d concurrent, %d failed, %.0f req/s, p50 %.1f ms, p95 %.1f ms, max %.1f ms%n",
                totalRequests, concurrency, failures, totalRequests / (totalMs / 1000),
                times.get(times.size() / 2) / 1e6, times.get((int) (times.size() * 0.95)) / 1e6,
                times.get(times.size() - 1) / 1e6);
    }
}
