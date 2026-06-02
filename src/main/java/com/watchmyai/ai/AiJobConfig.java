package com.watchmyai.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
        // Keep servlet threads fast: overload is handled in AiService with quota refund
        // and a terminal failed job instead of running the OpenAI call inline.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
