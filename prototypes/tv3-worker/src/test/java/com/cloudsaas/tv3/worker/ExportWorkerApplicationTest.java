package com.cloudsaas.tv3.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@SpringBootTest(classes = {ExportWorkerApplication.class, ExportWorkerApplicationTest.TestPollerConfiguration.class})
class ExportWorkerApplicationTest {
    @Autowired
    private Environment environment;

    @Autowired
    private WorkerProperties properties;

    @Autowired
    private QueuePollingLifecycle lifecycle;

    @Test
    void startsWithTheNonWebWorkerProfileAndValidatedSettings() {
        assertThat(environment.acceptsProfiles(Profiles.of("worker"))).isTrue();
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(properties.emptyPollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.maxErrorBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPollerConfiguration {
        @Bean
        ExportQueuePoller testPoller() {
            return () -> ExportQueuePoller.PollResult.EMPTY;
        }
    }
}
