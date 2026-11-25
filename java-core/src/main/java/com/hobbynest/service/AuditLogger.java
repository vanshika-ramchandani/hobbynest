package com.hobbynest.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Producer/consumer audit log. Request threads call {@link #log} (non-blocking enqueue) and a single
 * background thread appends entries to audit.log, so slow disk writes never delay a booking.
 */
public class AuditLogger implements AutoCloseable {

    private static final String POISON_PILL = "\u0000STOP";

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Path file;
    private final Clock clock;
    private final Thread worker;
    private final AtomicLong written = new AtomicLong();

    public AuditLogger(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
        this.worker = new Thread(this::drain, "audit-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void log(String event) {
        queue.offer(Instant.now(clock) + " [" + Thread.currentThread().getName() + "] " + event);
    }

    public long entriesWritten() {
        return written.get();
    }

    private void drain() {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                while (true) {
                    String line = queue.take();
                    if (POISON_PILL.equals(line)) {
                        break;
                    }
                    out.write(line);
                    out.newLine();
                    written.incrementAndGet();
                    if (queue.isEmpty()) {
                        out.flush();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Audit log unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Writes everything still queued, then stops the worker thread. */
    @Override
    public void close() {
        queue.offer(POISON_PILL);
        try {
            worker.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
