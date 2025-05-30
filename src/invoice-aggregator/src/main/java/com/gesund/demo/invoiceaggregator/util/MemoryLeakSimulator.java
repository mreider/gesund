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
    private static final int INITIAL_DELAY_SECONDS = 60; // Start first leak after 1 minute
    private static final int LEAK_INTERVAL_SECONDS = 900; // Trigger leak every 15 minutes
    private static final int LEAK_DURATION_SECONDS = 30; // Leak for 30 seconds (should be enough to trigger OOM)
    private static final int CHUNK_SIZE_MB = 150; // Allocate in 150MB chunks (container limit is 512Mi)
    private static final int CHUNK_INTERVAL_MS = 20; // Add a chunk every 20ms (very aggressive)
    
    @PostConstruct
    public void init() {
        log.info("Initializing MemoryLeakSimulator - will trigger OOM every ~15 minutes");
        
        // Start first leak after a short delay to allow application to initialize
        int initialDelay = INITIAL_DELAY_SECONDS;
        log.info("First memory leak will start in {} seconds", initialDelay);
        
        // Schedule deterministic memory leaks every 15 minutes
        // scheduler.scheduleAtFixedRate(() -> {
        //     if (!currentlyLeaking) {
        //         log.info("Scheduled memory leak triggered - starting aggressive memory allocation");
        //         startMemoryLeak();
        //     }
        // }, initialDelay, LEAK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Start leaking memory by allocating large byte arrays
     */
    private void startMemoryLeak() {
        currentlyLeaking = true;
        
        // Log the start of the memory leak with distinctive pattern for easy log searching
        log.error("!!!!! STARTING MEMORY LEAK SIMULATION - ALLOCATING MEMORY TO FORCE OOM KILL !!!!!");
        log.error("!!!!! THIS WILL CAUSE THE CONTAINER TO BE KILLED BY KUBERNETES !!!!!");
        
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
                        
                        // Try to force GC to clear any temporary objects and make OOM more likely
                        System.gc();
                        
                        // Create additional temporary objects to increase memory pressure
                        byte[][] tempArrays = new byte[20][];
                        for (int i = 0; i < tempArrays.length; i++) {
                            tempArrays[i] = new byte[20 * 1024 * 1024]; // 20MB each
                            random.nextBytes(tempArrays[i]);
                        }
                        
                        // Log memory usage
                        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                        
                        log.error("!!!!! MEMORY LEAK PROGRESS: Allocated {}MB chunk. Total used: {}MB / {}MB ({}%) !!!!!", 
                                CHUNK_SIZE_MB, 
                                usedMemory, 
                                maxMemory,
                                (usedMemory * 100) / maxMemory);
                        
                        // Sleep briefly before allocating more
                        Thread.sleep(CHUNK_INTERVAL_MS);
                    } catch (OutOfMemoryError e) {
                        // Log the OOM error with distinctive pattern
                        log.error("!!!!! OUT OF MEMORY ERROR DETECTED: {} !!!!!", e.getMessage(), e);
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
