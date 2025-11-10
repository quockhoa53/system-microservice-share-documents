package com.system_share_documents.WatermarkWorkerService.concurrent;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StripedExecutor implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(StripedExecutor.class);

    private final ThreadPoolExecutor[] executors;
    private final int stripes;

    public StripedExecutor(int stripes, MeterRegistry registry) {
        this.stripes = stripes;
        this.executors = new ThreadPoolExecutor[stripes];

        for (int i = 0; i < stripes; i++) {
            // Tạo thread pool mỗi stripe: 1 thread + queue giới hạn
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(200), // Giới hạn queue → tránh OutOfMemory
                    new NamedThreadFactory("stripe-" + i),
                    new ThreadPoolExecutor.AbortPolicy() // Từ chối khi quá tải
            );

            // Export metric tới Prometheus
            registry.gauge("watermark_executor_queue_size", executor.getQueue(), Queue::size);
            this.executors[i] = executor;
        }
        log.info("Initialized StripedExecutor with {} stripes", stripes);
    }

    public void execute(String key, Runnable task) {
        int idx = Math.abs(key.hashCode() % stripes);
        try {
            executors[idx].execute(task);
        } catch (RejectedExecutionException ex) {
            log.warn("Task rejected in stripe-{} (queue full)", idx);
        }
    }

    @Override
    public void destroy() {
        log.info("Shutting down StripedExecutor gracefully...");
        for (ExecutorService ex : executors) {
            ex.shutdown();
            try {
                if (!ex.awaitTermination(10, TimeUnit.SECONDS)) {
                    ex.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("StripedExecutor shut down complete");
    }

    /** Thread factory có tên đẹp và đánh số */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger count = new AtomicInteger(0);

        public NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, baseName + "-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
