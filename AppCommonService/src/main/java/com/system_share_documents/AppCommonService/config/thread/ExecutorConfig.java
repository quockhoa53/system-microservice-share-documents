package com.system_share_documents.AppCommonService.config.thread;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class ExecutorConfig {

    @Bean(name = "commonExecutor")
    public ExecutorService commonExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                50, 50,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new NamedThreadFactory("CommonExecutor-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor; // Kiểu là ExecutorService
    }


    /** Thread factory đặt tên đẹp cho dễ debug */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        private final AtomicInteger counter = new AtomicInteger(0);

        public NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = defaultFactory.newThread(r);
            t.setName(baseName + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
