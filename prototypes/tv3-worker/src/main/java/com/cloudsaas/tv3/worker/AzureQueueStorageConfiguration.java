package com.cloudsaas.tv3.worker;

import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(QueueStorageProperties.class)
public class AzureQueueStorageConfiguration {
    @Bean(name = "exportQueueClient", destroyMethod = "close")
    @Profile("worker & azurite")
    QueueClient azuriteExportQueueClient(QueueStorageProperties properties) {
        return createAzuriteClient(properties, properties.queueName());
    }

    @Bean(name = "poisonQueueClient", destroyMethod = "close")
    @Profile("worker & azurite")
    QueueClient azuritePoisonQueueClient(QueueStorageProperties properties) {
        return createAzuriteClient(properties, properties.poisonQueueName());
    }

    @Bean(name = "exportQueueClient", destroyMethod = "close")
    @Profile("worker & azure")
    QueueClient azureExportQueueClient(QueueStorageProperties properties) {
        return createManagedIdentityClient(properties, properties.queueName());
    }

    @Bean(name = "poisonQueueClient", destroyMethod = "close")
    @Profile("worker & azure")
    QueueClient azurePoisonQueueClient(QueueStorageProperties properties) {
        return createManagedIdentityClient(properties, properties.poisonQueueName());
    }

    @Bean
    @Profile({"worker & azurite", "worker & azure"})
    QueueStorageOperations queueStorageOperations(
            @Qualifier("exportQueueClient") QueueClient exportQueue,
            @Qualifier("poisonQueueClient") QueueClient poisonQueue) {
        return new AzureQueueStorageOperations(exportQueue, poisonQueue);
    }

    @Bean
    @Profile({"worker & azurite", "worker & azure"})
    AzureStorageQueuePoller azureStorageQueuePoller(
            QueueStorageOperations queueStorageOperations,
            QueueStorageProperties properties,
            QueueMessageContractParser parser,
            ExportMessageProcessor processor) {
        return new AzureStorageQueuePoller(queueStorageOperations, properties, parser, processor);
    }

    private static QueueClient createAzuriteClient(QueueStorageProperties properties, String queueName) {
        if (properties.connectionString() == null || properties.connectionString().isBlank()) {
            throw new BeanCreationException(
                    "queueClient", "AZURE_STORAGE_CONNECTION_STRING is required for the azurite profile");
        }
        QueueClient client = new QueueClientBuilder()
                .connectionString(properties.connectionString())
                .queueName(queueName)
                .buildClient();
        client.createIfNotExists();
        return client;
    }

    private static QueueClient createManagedIdentityClient(QueueStorageProperties properties, String queueName) {
        if (properties.endpoint() == null || properties.endpoint().isBlank()) {
            throw new BeanCreationException(
                    "queueClient", "AZURE_STORAGE_QUEUE_ENDPOINT is required for the azure profile");
        }
        return new QueueClientBuilder()
                .endpoint(properties.endpoint())
                .credential(new ManagedIdentityCredentialBuilder().build())
                .queueName(queueName)
                .buildClient();
    }
}
