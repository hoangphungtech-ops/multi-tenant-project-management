package com.project.backendapi.azure;

import com.azure.storage.blob.*;
import com.azure.storage.queue.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.util.StringUtils;

@Configuration
public class AzureStorageConfig {
    @Bean
    BlobServiceClient blobServiceClient(@Value("${app.azure.connection-string:}") String cs) {
        if (!StringUtils.hasText(cs)) return null;
        return new BlobServiceClientBuilder().connectionString(cs).buildClient();
    }
    @Bean
    QueueServiceClient queueServiceClient(@Value("${app.azure.connection-string:}") String cs) {
        if (!StringUtils.hasText(cs)) return null;
        return new QueueServiceClientBuilder().connectionString(cs).buildClient();
    }
}
