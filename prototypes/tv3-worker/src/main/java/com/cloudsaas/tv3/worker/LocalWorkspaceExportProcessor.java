package com.cloudsaas.tv3.worker;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import com.cloudsaas.tv3.CsvBuilder;
import io.micrometer.core.instrument.Counter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

final class LocalWorkspaceExportProcessor implements ExportMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(LocalWorkspaceExportProcessor.class);
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final BlobContainerClient container;
    private final Counter tenantMismatch;

    LocalWorkspaceExportProcessor(String jdbcUrl, String username, String password,
            BlobContainerClient container, Counter tenantMismatch) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.container = container;
        this.tenantMismatch = tenantMismatch;
    }

    @Override
    public MessageProcessingDecision process(ExportQueueMessage message) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.setAutoCommit(false);
            try {
                MessageProcessingDecision decision = processLocked(connection, message);
                connection.commit();
                return decision;
            } catch (SQLException | IOException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException | IOException | RuntimeException failure) {
            log.warn("export_retryable_error type={}", failure.getClass().getSimpleName());
            return MessageProcessingDecision.RETRYABLE_FAILURE;
        }
    }

    private MessageProcessingDecision processLocked(Connection connection, ExportQueueMessage message)
            throws SQLException, IOException {
        UUID job = message.jobId();
        try (var statement = connection.prepareStatement("""
                SELECT tenant_id, requested_by, status FROM export_jobs WHERE id = ? FOR UPDATE
                """)) {
            statement.setObject(1, job);
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    return MessageProcessingDecision.RETRYABLE_FAILURE;
                }
                UUID tenant = row.getObject("tenant_id", UUID.class);
                if (message.claimedTenantId() != null && !tenant.equals(message.claimedTenantId())) {
                    tenantMismatch.increment();
                    log.atWarn().addKeyValue("tenantId", tenant).log("export_tenant_mismatch");
                    return MessageProcessingDecision.POISON_TENANT_MISMATCH;
                }

                try (var tenantId = MDC.putCloseable("tenantId", tenant.toString())) {
                    return export(connection, job, tenant, row.getObject("requested_by", UUID.class),
                            row.getString("status"));
                }
            }
        }
    }

    private MessageProcessingDecision export(Connection connection, UUID job, UUID tenant, UUID requester,
            String status) throws SQLException, IOException {
        if ("COMPLETED".equals(status)) {
            return MessageProcessingDecision.DURABLY_COMPLETED;
        }
        if ("FAILED".equals(status)) {
            return MessageProcessingDecision.DURABLY_REJECTED;
        }

        try (var context = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            context.setString(1, tenant.toString());
            context.execute();
        }
        if (!lockMembership(connection, tenant, requester)) {
            finish(connection, job, tenant, "FAILED", null, "EXPORT_MEMBERSHIP_REQUIRED");
            log.atWarn().addKeyValue("tenantId", tenant).log("export_membership_rejected");
            return MessageProcessingDecision.DURABLY_REJECTED;
        }

        log.info("export_workspace_scope_verified");
        String blob = tenant + "/" + job + ".csv";
        Path csv = Files.createTempFile("tv3-export-", ".csv");
        try {
            writeCsv(connection, tenant, csv);
            container.getBlobClient(blob).uploadFromFileWithResponse(
                    new BlobUploadFromFileOptions(csv.toString())
                            .setHeaders(new BlobHttpHeaders().setContentType("text/csv; charset=utf-8")),
                    Duration.ofMinutes(2), Context.NONE);
            finish(connection, job, tenant, "COMPLETED", blob, null);
        } finally {
            try {
                Files.deleteIfExists(csv);
            } catch (IOException failure) {
                log.warn("export_temp_cleanup_failed type={}", failure.getClass().getSimpleName());
            }
        }
        return MessageProcessingDecision.DURABLY_COMPLETED;
    }

    private static boolean lockMembership(Connection connection, UUID tenant, UUID requester) throws SQLException {
        if (requester == null) {
            return false;
        }
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM tenant_memberships
                WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE' AND role IN ('OWNER', 'MEMBER')
                FOR SHARE
                """)) {
            statement.setObject(1, tenant);
            statement.setObject(2, requester);
            try (var row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    private static void writeCsv(Connection connection, UUID tenant, Path csv) throws SQLException, IOException {
        try (var statement = connection.prepareStatement("""
                SELECT id, name, description FROM workspaces WHERE tenant_id = ? ORDER BY id
                """); var writer = Files.newBufferedWriter(csv)) {
            statement.setObject(1, tenant);
            statement.setFetchSize(256);
            writer.write("workspace_id,name,description\r\n");
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    writer.write(rows.getString("id"));
                    writer.write(',');
                    writer.write(CsvBuilder.cell(rows.getString("name")));
                    writer.write(',');
                    writer.write(CsvBuilder.cell(rows.getString("description")));
                    writer.write("\r\n");
                }
            }
        }
    }

    private static void finish(Connection connection, UUID job, UUID tenant, String status, String blob,
            String error) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE export_jobs SET status = ?, blob_name = ?, error_message = ?, completed_at = now()
                WHERE id = ? AND tenant_id = ?
                """)) {
            statement.setString(1, status);
            statement.setString(2, blob);
            statement.setString(3, error);
            statement.setObject(4, job);
            statement.setObject(5, tenant);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Export job update did not affect one row");
            }
        }
    }
}
