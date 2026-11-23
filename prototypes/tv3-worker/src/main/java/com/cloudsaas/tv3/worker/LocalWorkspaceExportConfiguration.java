package com.cloudsaas.tv3.worker;

import com.azure.storage.blob.BlobServiceClientBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("worker & azurite & workspace-demo & !azure")
class LocalWorkspaceExportConfiguration {
    @Bean
    ExportMessageProcessor localWorkspaceExportProcessor(
            @Value("${TV3_DEMO_DB_PORT:55432}") int dbPort,
            @Value("${TV3_DEMO_DB_PASSWORD}") String password,
            QueueStorageProperties queues, MeterRegistry metrics) {
        if (!"UseDevelopmentStorage=true".equals(queues.connectionString())) {
            throw new IllegalArgumentException("Workspace demo requires local Azurite development storage");
        }
        var container = new BlobServiceClientBuilder().connectionString(queues.connectionString())
                .buildClient().getBlobContainerClient("exports");
        container.createIfNotExists();
        return new LocalWorkspaceExportProcessor(
                "jdbc:postgresql://127.0.0.1:" + dbPort + "/tv3demo?connectTimeout=5&socketTimeout=30",
                "tv3demo", password, container, metrics.counter("worker_tenant_mismatch_total"));
    }
}
