package com.project.backendapi.export;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.UUID;

@Service
public class ExportQueueService {
    private final String connectionString;
    private final String queueName;

    public ExportQueueService(@Value("${app.azure.connection-string:}") String connectionString,
                              @Value("${app.azure.queue-name:export-jobs}") String queueName) {
        this.connectionString=connectionString; this.queueName=queueName;
    }

    public String enqueue(UUID jobId, UUID tenantId) {
        if (!StringUtils.hasText(connectionString)) {
            // Local no-credential mode: return a traceable placeholder; configure Azurite connection string to send real messages.
            return "local-not-sent-" + jobId;
        }
        QueueClient client = new QueueClientBuilder().connectionString(connectionString).queueName(queueName).buildClient();
        client.createIfNotExists();
        var response = client.sendMessage("{\"jobId\":\""+jobId+"\",\"tenantId\":\""+tenantId+"\"}");
        return response.getMessageId();
    }
}
