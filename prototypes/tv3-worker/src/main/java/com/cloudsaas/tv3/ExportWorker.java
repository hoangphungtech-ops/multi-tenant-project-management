package com.cloudsaas.tv3;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Pure domain core: replace the ports with PostgreSQL + Azure adapters.
 * No queue-supplied tenant ID is trusted for selecting data.
 */
public final class ExportWorker {
    private static final Logger LOG = Logger.getLogger(ExportWorker.class.getName());

    public enum Status { PENDING, PROCESSING, COMPLETED, REJECTED }
    public enum Result { COMPLETED, ALREADY_COMPLETED, DROP_FORGED, DROP_UNKNOWN, DROP_REVOKED, RETRY }

    public record Message(UUID jobId, UUID claimedTenantId, String requestId) {
        public Message {
            if (jobId == null || claimedTenantId == null) throw new IllegalArgumentException("Missing IDs");
        }
    }
    public record Job(UUID id, UUID tenantId, UUID requestedBy, Status status) {
        public Job {
            if (id == null || tenantId == null || requestedBy == null || status == null) {
                throw new IllegalArgumentException("Missing job data");
            }
        }
    }
    public record Task(UUID id, String title, String state) {}

    public interface JobRepository {
        Optional<Job> findById(UUID jobId); // DB-authoritative job record, not tenant from message
        boolean claimForProcessing(UUID jobId, UUID authoritativeTenantId); // atomic CAS, supports stale lease recovery
        void markCompleted(UUID jobId, UUID authoritativeTenantId, String blobPath);
        void releaseForRetry(UUID jobId, UUID authoritativeTenantId);
        void rejectRevokedMembership(UUID jobId, UUID authoritativeTenantId);
    }
    public interface MembershipRepository {
        boolean isActiveMember(UUID authoritativeTenantId, UUID userId);
    }
    public interface TaskRepository {
        /** Must enforce tenant_id = :verifiedTenantId AND PostgreSQL RLS; never use message tenant. */
        List<Task> findByTenant(UUID verifiedTenantId);
    }
    public interface PrivateBlobStore {
        /** Upload to PRIVATE container; retrying a deterministic path must be idempotent. */
        void upload(String blobPath, byte[] content, String mimeType);
    }
    public interface SecurityMetrics {
        void tenantMismatchDetected();
    }

    private final JobRepository jobs;
    private final MembershipRepository memberships;
    private final TaskRepository tasks;
    private final PrivateBlobStore blobs;
    private final SecurityMetrics metrics;

    public ExportWorker(JobRepository jobs, MembershipRepository memberships, TaskRepository tasks,
                        PrivateBlobStore blobs, SecurityMetrics metrics) {
        this.jobs = jobs;
        this.memberships = memberships;
        this.tasks = tasks;
        this.blobs = blobs;
        this.metrics = metrics;
    }

    public Result process(Message message) {
        Optional<Job> authoritativeJob = jobs.findById(message.jobId());
        if (authoritativeJob.isEmpty()) {
            LOG.warning("export_job_unknown jobId=" + message.jobId());
            return Result.DROP_UNKNOWN;
        }
        Job job = authoritativeJob.get();

        // Failure drill: the claimed tenant is UNTRUSTED. Never query tasks until compared.
        if (!job.tenantId().equals(message.claimedTenantId())) {
            metrics.tenantMismatchDetected();
            LOG.warning("export_tenant_mismatch jobId=" + job.id() + " authoritativeTenantId=" + job.tenantId());
            return Result.DROP_FORGED; // no mutation of legitimate job state
        }
        if (job.status() == Status.COMPLETED) return Result.ALREADY_COMPLETED;

        // Check membership *at processing time* to catch immediate revocation.
        if (!memberships.isActiveMember(job.tenantId(), job.requestedBy())) {
            jobs.rejectRevokedMembership(job.id(), job.tenantId());
            LOG.warning("export_membership_revoked jobId=" + job.id());
            return Result.DROP_REVOKED;
        }
        if (!jobs.claimForProcessing(job.id(), job.tenantId())) {
            return Result.RETRY; // another worker owns lease; queue adapter must renew/delay appropriately
        }
        String blobPath = "exports/" + job.tenantId() + "/" + job.id() + ".csv";
        try {
            byte[] csv = CsvBuilder.create(tasks.findByTenant(job.tenantId())).getBytes(StandardCharsets.UTF_8);
            blobs.upload(blobPath, csv, "text/csv; charset=utf-8");
            jobs.markCompleted(job.id(), job.tenantId(), blobPath);
            LOG.info("export_completed jobId=" + job.id() + " tenantId=" + job.tenantId());
            return Result.COMPLETED;
        } catch (RuntimeException ex) {
            // Never log exception messages: Azure/JDBC SDK error strings can contain sensitive data.
            LOG.warning("export_retryable_error jobId=" + job.id() + " errorType=" + ex.getClass().getSimpleName());
            jobs.releaseForRetry(job.id(), job.tenantId());
            return Result.RETRY;
        }
    }
}
