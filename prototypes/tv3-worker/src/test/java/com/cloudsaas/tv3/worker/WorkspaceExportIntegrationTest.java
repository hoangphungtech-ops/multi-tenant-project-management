package com.cloudsaas.tv3.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.queue.QueueClientBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfSystemProperty(named = "tv3.demo.it", matches = "true")
@SpringBootTest(properties = {
        "spring.profiles.active=worker,azurite,workspace-demo",
        "tv3.worker.empty-poll-interval=100ms"
})
class WorkspaceExportIntegrationTest {
    private static final String SOURCE_QUEUE = "tv3-it-" + UUID.randomUUID();
    private static final String POISON_QUEUE = SOURCE_QUEUE + "-poison";

    @DynamicPropertySource
    static void queueNames(DynamicPropertyRegistry registry) {
        registry.add("tv3.queue.queue-name", () -> SOURCE_QUEUE);
        registry.add("tv3.queue.poison-queue-name", () -> POISON_QUEUE);
    }

    private static final String STORAGE = "UseDevelopmentStorage=true";
    private final UUID tenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID revoked = UUID.randomUUID();
    private final UUID workspace = UUID.randomUUID();

    @Test
    void checksTenantMembershipPoisonCsvAndRedelivery() throws Exception {
        UUID validJob = UUID.randomUUID();
        UUID revokedJob = UUID.randomUUID();
        UUID anonymousJob = UUID.randomUUID();
        try (var connection = connect(); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO tenants (id,name) VALUES ('" + tenant + "','A'),('" + otherTenant + "','B')");
            sql.execute("INSERT INTO users (id,entra_object_id) VALUES ('" + user + "','" + user + "'),('"
                    + revoked + "','" + revoked + "')");
            sql.execute("INSERT INTO tenant_memberships (tenant_id,user_id,role,status) VALUES ('"
                    + tenant + "','" + user + "','MEMBER','ACTIVE'),('" + tenant + "','" + revoked
                    + "','MEMBER','INACTIVE')");
            sql.execute("INSERT INTO workspaces (id,tenant_id,name,description) VALUES ('" + workspace
                    + "','" + tenant + "','=formula','one,two'),('" + UUID.randomUUID() + "','" + otherTenant
                    + "','PRIVATE-B',null)");
            sql.execute("INSERT INTO export_jobs (id,tenant_id,requested_by) VALUES ('" + validJob + "','"
                    + tenant + "','" + user + "'),('" + revokedJob + "','" + tenant + "','" + revoked
                    + "'),('" + anonymousJob + "','" + tenant + "',null)");
        }
        var queue = new QueueClientBuilder().connectionString(STORAGE).queueName(SOURCE_QUEUE).buildClient();
        var poison = new QueueClientBuilder().connectionString(STORAGE)
                .queueName(POISON_QUEUE).buildClient();
        var blob = new BlobServiceClientBuilder().connectionString(STORAGE).buildClient()
                .getBlobContainerClient("exports").getBlobClient(tenant + "/" + validJob + ".csv");

        String forgedMessageId = queue.sendMessage(payload(validJob, otherTenant)).getMessageId();
        await(() -> poison.peekMessages(32, Duration.ofSeconds(5), null).stream()
                .anyMatch(message -> message.getBody().toString().contains(forgedMessageId)
                        && message.getBody().toString().contains("TENANT_MISMATCH")));
        assertThat(status(validJob)).isEqualTo("PENDING");
        assertThat(blob.exists()).isFalse();

        var absentContainer = new BlobServiceClientBuilder().connectionString(STORAGE).buildClient()
                .getBlobContainerClient("absent-" + UUID.randomUUID());
        var metrics = new SimpleMeterRegistry();
        try {
            var failing = new LocalWorkspaceExportProcessor(
                    jdbcUrl(), "tv3demo", System.getenv("TV3_DEMO_DB_PASSWORD"),
                    absentContainer, metrics.counter("worker_tenant_mismatch_total"));
            assertThat(failing.process(new ExportQueueMessage(0, validJob, tenant, null)))
                    .isEqualTo(MessageProcessingDecision.RETRYABLE_FAILURE);
        } finally {
            metrics.close();
        }
        assertThat(status(validJob)).isEqualTo("PENDING");
        assertThat(blob.exists()).isFalse();

        queue.sendMessage(payload(revokedJob, tenant));
        queue.sendMessage(payload(anonymousJob, tenant));
        queue.sendMessage(payload(validJob, tenant));
        await(() -> "COMPLETED".equals(status(validJob)) && "FAILED".equals(status(revokedJob))
                && "FAILED".equals(status(anonymousJob)));
        var downloaded = blob.downloadContent();
        assertThat(downloaded.toString()).isEqualTo(
                "workspace_id,name,description\r\n" + workspace + ",'=formula,\"one,two\"\r\n");
        Files.write(Path.of("target", "verified-workspace-export.csv"), downloaded.toBytes());
        String etag = blob.getProperties().getETag();
        queue.sendMessage(payload(validJob, tenant));
        await(() -> queue.getProperties().getApproximateMessagesCount() == 0);
        assertThat(blob.getProperties().getETag()).isEqualTo(etag);
    }

    private static String payload(UUID job, UUID tenant) {
        return "{\"jobId\":\"" + job + "\",\"tenantId\":\"" + tenant + "\"}";
    }

    private static java.sql.Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(jdbcUrl(), "tv3demo", System.getenv("TV3_DEMO_DB_PASSWORD"));
    }

    private static String jdbcUrl() {
        String port = System.getenv().getOrDefault("TV3_DEMO_DB_PORT", "55432");
        return "jdbc:postgresql://127.0.0.1:" + port + "/tv3demo?connectTimeout=5";
    }

    private static String status(UUID job) throws Exception {
        try (var connection = connect();
                var query = connection.prepareStatement("SELECT status FROM export_jobs WHERE id = ?")) {
            query.setObject(1, job);
            try (var row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getString(1);
            }
        }
    }

    private static void await(Condition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.met()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for export integration condition");
            }
            Thread.sleep(200);
        }
    }

    @FunctionalInterface
    private interface Condition {
        boolean met() throws Exception;
    }
}
