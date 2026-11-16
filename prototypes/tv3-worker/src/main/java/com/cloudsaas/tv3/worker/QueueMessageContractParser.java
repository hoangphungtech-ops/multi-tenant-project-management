package com.cloudsaas.tv3.worker;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class QueueMessageContractParser {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_BODY_LENGTH = 16 * 1024;
    private static final Set<String> ALLOWED_FIELDS =
            Set.of("schemaVersion", "jobId", "claimedTenantId", "requestId");

    private final ObjectMapper objectMapper;

    public QueueMessageContractParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExportQueueMessage parse(String body) {
        if (body == null || body.isBlank() || body.length() > MAX_BODY_LENGTH) {
            throw new QueueMessageContractException("INVALID_MESSAGE_SIZE");
        }

        JsonNode root;
        try (JsonParser jsonParser = objectMapper.getFactory().createParser(body)) {
            jsonParser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            root = objectMapper.readTree(jsonParser);
        } catch (IOException ex) {
            throw new QueueMessageContractException("INVALID_JSON");
        }
        if (root == null || !root.isObject()) {
            throw new QueueMessageContractException("INVALID_MESSAGE_SHAPE");
        }

        var fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            if (!ALLOWED_FIELDS.contains(fieldNames.next())) {
                throw new QueueMessageContractException("UNKNOWN_MESSAGE_FIELD");
            }
        }

        JsonNode version = root.get("schemaVersion");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                || version.intValue() != SUPPORTED_SCHEMA_VERSION) {
            throw new QueueMessageContractException("UNSUPPORTED_SCHEMA_VERSION");
        }

        UUID jobId = requiredUuid(root.get("jobId"), "MISSING_JOB_ID", "INVALID_JOB_ID");
        UUID claimedTenantId = optionalUuid(root.get("claimedTenantId"));
        String requestId = optionalRequestId(root.get("requestId"));
        return new ExportQueueMessage(SUPPORTED_SCHEMA_VERSION, jobId, claimedTenantId, requestId);
    }

    private static UUID requiredUuid(JsonNode value, String missingCode, String invalidCode) {
        if (value == null || value.isNull()) {
            throw new QueueMessageContractException(missingCode);
        }
        return parseUuid(value, invalidCode);
    }

    private static UUID optionalUuid(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (value.isNull()) {
            throw new QueueMessageContractException("INVALID_TENANT_ID");
        }
        return parseUuid(value, "INVALID_TENANT_ID");
    }

    private static UUID parseUuid(JsonNode value, String errorCode) {
        if (!value.isTextual()) {
            throw new QueueMessageContractException(errorCode);
        }
        try {
            UUID parsed = UUID.fromString(value.textValue());
            if (!parsed.toString().equalsIgnoreCase(value.textValue())) {
                throw new QueueMessageContractException(errorCode);
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw new QueueMessageContractException(errorCode);
        }
    }

    private static String optionalRequestId(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 128) {
            throw new QueueMessageContractException("INVALID_REQUEST_ID");
        }
        return value.textValue();
    }
}
