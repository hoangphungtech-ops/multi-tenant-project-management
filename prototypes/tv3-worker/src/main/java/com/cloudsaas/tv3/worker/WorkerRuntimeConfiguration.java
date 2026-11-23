package com.cloudsaas.tv3.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("worker")
public class WorkerRuntimeConfiguration {
    @Bean
    QueuePollingLifecycle queuePollingLifecycle(ExportQueuePoller poller, WorkerProperties properties) {
        return new QueuePollingLifecycle(poller, properties);
    }
}
