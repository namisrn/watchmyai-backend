package com.watchmyai.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded worker pool that runs the blocking OpenAI call off the servlet thread. The HTTP
 * request returns immediately with a job id; the worker updates the job log when finished.
 */
@Configuration
public class AiJobConfig {

    @Bean(name = "aiJobExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor aiJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("ai-job-");
        // Under sustained overload, run the job on the calling thread as backpressure
        // instead of dropping it.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
