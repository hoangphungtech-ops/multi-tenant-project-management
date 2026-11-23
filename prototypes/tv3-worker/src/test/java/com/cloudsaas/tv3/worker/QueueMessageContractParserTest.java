package com.cloudsaas.tv3.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QueueMessageContractParserTest {
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-00000000002a");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final QueueMessageContractParser parser = new QueueMessageContractParser(new ObjectMapper());

    @Test
    void parsesVersionedMessageWithoutTrustingOrRequiringClaimedTenant() {
        ExportQueueMessage message = parser.parse("""
                {"schemaVersion":1,"jobId":"%s","claimedTenantId":"%s","requestId":"req-123"}
                """.formatted(JOB_ID, TENANT_ID));

        assertThat(message.schemaVersion()).isEqualTo(1);
        assertThat(message.jobId()).isEqualTo(JOB_ID);
        assertThat(message.claimedTenantId()).isEqualTo(TENANT_ID);
        assertThat(message.requestId()).isEqualTo("req-123");
    }

    @Test
    void rejectsMalformedJsonWithSanitizedErrorCode() {
        assertThatThrownBy(() -> parser.parse("{not-json"))
                .isInstanceOf(QueueMessageContractException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_JSON");
    }

    @Test
    void rejectsMessageMissingJobId() {
        assertThatThrownBy(() -> parser.parse("{\"schemaVersion\":1}"))
                .isInstanceOf(QueueMessageContractException.class)
                .extracting("errorCode")
                .isEqualTo("MISSING_JOB_ID");
    }

    @Test
    void rejectsNonIntegerSchemaVersionAndDuplicateFields() {
        assertThatThrownBy(() -> parser.parse("""
                {"schemaVersion":1.5,"jobId":"%s"}
                """.formatted(JOB_ID)))
                .isInstanceOf(QueueMessageContractException.class)
                .extracting("errorCode")
                .isEqualTo("UNSUPPORTED_SCHEMA_VERSION");
        assertThatThrownBy(() -> parser.parse("""
                {"schemaVersion":1,"jobId":"%s","jobId":"%s"}
                """.formatted(JOB_ID, JOB_ID)))
                .isInstanceOf(QueueMessageContractException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_JSON");
    }
}
