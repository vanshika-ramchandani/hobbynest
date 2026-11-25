package com.hobbynest;

import com.hobbynest.api.ApiServer;
import com.hobbynest.demo.ConcurrencyDemo;
import com.hobbynest.demo.DemoData;
import com.hobbynest.payment.MockPaymentGateway;
import com.hobbynest.pricing.PricingEngine;
import com.hobbynest.service.AuditLogger;
import com.hobbynest.service.OverdueMonitor;
import com.hobbynest.service.RentalService;
import com.hobbynest.storage.FileDataStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point.
 *   java -jar rental-engine.jar          start the HTTP API (PORT, default 8081; DATA_DIR, default ./data)
 *   java -jar rental-engine.jar demo     run the booking race and load test, then exit
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "serve";
        int port = Integer.parseInt(env("PORT", "8081"));
        // Demo mode never touches DATA_DIR: it always works in a fresh temporary folder,
        // so running "demo" against a real data volume cannot wipe it.
        Path dataDir = mode.equals("demo")
                ? Files.createTempDirectory("hobbynest-demo-")
                : Path.of(env("DATA_DIR", "data"));
        Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

        AuditLogger audit = new AuditLogger(dataDir.resolve("audit.log"), clock);
        RentalService service = new RentalService(new FileDataStore(dataDir), new PricingEngine(),
                new MockPaymentGateway(), audit, clock);
        DemoData.seedIfEmpty(service);
        OverdueMonitor monitor = new OverdueMonitor(service, audit);
        monitor.start(60);
        ApiServer api = new ApiServer(service, monitor, mode.equals("demo") ? 0 : port, 16);
        api.start();

        Runnable shutdown = () -> {
            api.close();
            monitor.close();
            audit.close();
        };

        if (mode.equals("demo")) {
            api.setLogRequests(false);
            System.out.println("== HobbyNest Java engine demo ==");
            ConcurrencyDemo.bookingRace(service, 100);
            ConcurrencyDemo.loadTest("http://localhost:" + api.getPort(), 300, 25);
            shutdown.run();
            System.out.println("Audit entries written: " + audit.entriesWritten() + " (" + dataDir.resolve("audit.log") + ")");
            return;
        }

        System.out.println("HobbyNest Java engine listening on http://localhost:" + api.getPort() + "/api/health");
        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown.run();
            stop.countDown();
        }));
        stop.await();
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
