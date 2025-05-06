package com.gesund.demo.invoiceaggregator.util;

import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A wrapper for ExecutorService that automatically propagates OpenTelemetry context
 * across thread boundaries. This ensures that distributed tracing works correctly
 * when tasks are executed in separate threads.
 */
@Slf4j
public class ContextPropagatingExecutorService {
    
    private final ExecutorService executorService;
    
    public ContextPropagatingExecutorService(int threadPoolSize) {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }
    
    /**
     * Executes the given command in a worker thread with the current OpenTelemetry context.
     * 
     * @param task The runnable task to execute
     */
    public void execute(Runnable task) {
        // Capture the current context before submitting to the executor
        Context context = Context.current();
        log.debug("Capturing context for task execution: {}", context);
                
        executorService.execute(() -> {
            // Make the captured context current in the worker thread
            try (io.opentelemetry.context.Scope scope = context.makeCurrent()) {
                log.debug("Restored context in worker thread: {}", Context.current());
                task.run();
            }
        });
    }
    
    /**
     * Submits the given task for execution in a worker thread with the current OpenTelemetry context.
     * 
     * @param <V> The result type of the callable
     * @param task The callable task to execute
     * @return A Future representing the result of the task
     */
    public <V> Future<V> submit(Callable<V> task) {
        // Capture the current context before submitting to the executor
        Context context = Context.current();
        log.debug("Capturing context for task submission: {}", context);
                
        return executorService.submit(() -> {
            // Make the captured context current in the worker thread
            try (io.opentelemetry.context.Scope scope = context.makeCurrent()) {
                log.debug("Restored context in worker thread: {}", Context.current());
                return task.call();
            }
        });
    }
    
    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
