package com.gesund.demo.invoiceaggregator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class simulates memory leaks in the invoice-aggregator service.
 * It randomly decides to start leaking memory approximately every 15 minutes.
 */
@Slf4j
@Component
public class MemoryLeakSimulator {

    // Static collection that will grow and never be garbage collected
    private static final List<byte[]> MEMORY_LEAK_COLLECTION = new ArrayList<>();
    
    // Random number generator for determining when to leak
    private final Random random = new Random();
    
    // Scheduler for checking if we should leak memory
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Flag to track if we're currently leaking memory
    private volatile boolean currentlyLeaking = false;
    
    // Constants for controlling the leak behavior
    private static final int BASE_CHECK_INTERVAL_SECONDS = 60; // Check every minute
    private static final int LEAK_CHANCE_PERCENT = 15; // 15% chance each check
    private static final int LEAK_DURATION_SECONDS = 120; // Leak for 2 minutes
    private static final int CHUNK_SIZE_MB = 10; // Allocate in 10MB chunks
    private static final int CHUNK_INTERVAL_MS = 500; // Add a chunk every 500ms
    
    @PostConstruct
    public void init() {
        log.info("Initializing MemoryLeakSimulator");
        
        // Schedule periodic checks to potentially start a memory leak
        scheduler.scheduleAtFixedRate(this::checkForLeak, 
                random.nextInt(BASE_CHECK_INTERVAL_SECONDS), 
                BASE_CHECK_INTERVAL_SECONDS, 
                TimeUnit.SECONDS);
    }
    
    /**
     * Periodically check if we should start leaking memory
     */
    private void checkForLeak() {
        // If we're already leaking, don't start another leak
        if (currentlyLeaking) {
            return;
        }
        
        // Approximately every 15 minutes (15 checks at 1 minute intervals with 15% chance each)
        if (random.nextInt(100) < LEAK_CHANCE_PERCENT) {
            log.info("Memory leak check triggered - starting controlled memory leak");
            startMemoryLeak();
        }
    }
    
    /**
     * Start leaking memory by allocating large byte arrays
     */
    private void startMemoryLeak() {
        currentlyLeaking = true;
        
        // Log the start of the memory leak
        log.warn("STARTING MEMORY LEAK SIMULATION - Allocating memory aggressively for {} seconds", LEAK_DURATION_SECONDS);
        
        // Schedule the end of the leak
        scheduler.schedule(() -> {
            log.warn("Memory leak simulation complete - waiting for OOM kill");
            currentlyLeaking = false;
        }, LEAK_DURATION_SECONDS, TimeUnit.SECONDS);
        
        // Start a thread to allocate memory
        Thread leakThread = new Thread(() -> {
            try {
                // Continue allocating memory until we're told to stop or until OOM
                while (currentlyLeaking) {
                    try {
                        // Allocate a chunk of memory (10MB)
                        byte[] chunk = new byte[CHUNK_SIZE_MB * 1024 * 1024];
                        
                        // Fill with random data to ensure it's actually allocated
                        random.nextBytes(chunk);
                        
                        // Add to our leak collection so it's not garbage collected
                        MEMORY_LEAK_COLLECTION.add(chunk);
                        
                        // Log memory usage
                        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                        
                        log.warn("Memory leak progress: Allocated {}MB chunk. Total used: {}MB / {}MB ({}%)", 
                                CHUNK_SIZE_MB, 
                                usedMemory, 
                                maxMemory,
                                (usedMemory * 100) / maxMemory);
                        
                        // Sleep briefly before allocating more
                        Thread.sleep(CHUNK_INTERVAL_MS);
                    } catch (OutOfMemoryError e) {
                        // Log the OOM error
                        log.error("OUT OF MEMORY ERROR DETECTED: {}", e.getMessage(), e);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                log.info("Memory leak thread interrupted");
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.error("Unexpected error in memory leak thread: {}", t.getMessage(), t);
            }
        });
        
        // Set as daemon so it doesn't prevent JVM shutdown
        leakThread.setDaemon(true);
        leakThread.setName("memory-leak-simulator");
        leakThread.start();
    }
}
