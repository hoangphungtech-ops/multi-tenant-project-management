package com.cloudsaas.tv3;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** No external framework; tests compile/run on Java 21. */
public final class ExportWorkerSelfTest {
    static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-00000000001a");
    static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-00000000001b");
    static final UUID JOB_A = UUID.fromString("00000000-0000-0000-0000-00000000002a");
    static final UUID JOB_B = UUID.fromString("00000000-0000-0000-0000-00000000002b");
    static final UUID JOB_C = UUID.fromString("00000000-0000-0000-0000-00000000002c");

    static class Memory implements ExportWorker.JobRepository, ExportWorker.MembershipRepository,
            ExportWorker.TaskRepository, ExportWorker.PrivateBlobStore, ExportWorker.SecurityMetrics {
        Map<UUID, ExportWorker.Job> jobs = new HashMap<>();
        Map<UUID, List<ExportWorker.Task>> tasks = new HashMap<>();
        Map<String, byte[]> blobs = new HashMap<>();
        Set<String> active = new HashSet<>();
        int mismatches = 0, uploads = 0;
        boolean failUpload;
        Memory() {
            jobs.put(JOB_A, new ExportWorker.Job(JOB_A, TENANT_A, USER_A, ExportWorker.Status.PENDING));
            jobs.put(JOB_B, new ExportWorker.Job(JOB_B, TENANT_B, USER_B, ExportWorker.Status.PENDING));
            jobs.put(JOB_C, new ExportWorker.Job(JOB_C, TENANT_A, USER_A, ExportWorker.Status.PENDING));
            active.add(TENANT_A + ":" + USER_A);
            active.add(TENANT_B + ":" + USER_B);
            tasks.put(TENANT_A, List.of(new ExportWorker.Task(UUID.randomUUID(), "Alpha, task", "OPEN")));
            tasks.put(TENANT_B, List.of(new ExportWorker.Task(UUID.randomUUID(), "SECRET_ORG_B", "DONE")));
        }
        public Optional<ExportWorker.Job> findById(UUID id) { return Optional.ofNullable(jobs.get(id)); }
        public boolean claimForProcessing(UUID id, UUID tenant) {
            ExportWorker.Job j = jobs.get(id);
            if (!j.tenantId().equals(tenant) || j.status() != ExportWorker.Status.PENDING) return false;
            jobs.put(id, new ExportWorker.Job(id,tenant,j.requestedBy(),ExportWorker.Status.PROCESSING));
            return true;
        }
        public void markCompleted(UUID id, UUID tenant, String path) {
            ExportWorker.Job j = jobs.get(id);
            if (!j.tenantId().equals(tenant)) throw new SecurityException();
            jobs.put(id, new ExportWorker.Job(id,tenant,j.requestedBy(),ExportWorker.Status.COMPLETED));
        }
        public void releaseForRetry(UUID id, UUID tenant) {
            ExportWorker.Job j = jobs.get(id);
            if (!j.tenantId().equals(tenant)) throw new SecurityException();
            jobs.put(id, new ExportWorker.Job(id,tenant,j.requestedBy(),ExportWorker.Status.PENDING));
        }
        public void rejectRevokedMembership(UUID id, UUID tenant) {
            ExportWorker.Job j = jobs.get(id);
            if (!j.tenantId().equals(tenant)) throw new SecurityException();
            jobs.put(id, new ExportWorker.Job(id,tenant,j.requestedBy(),ExportWorker.Status.REJECTED));
        }
        public boolean isActiveMember(UUID tenant, UUID user) { return active.contains(tenant + ":" + user); }
        public List<ExportWorker.Task> findByTenant(UUID tenant) { return tasks.getOrDefault(tenant, List.of()); }
        public void upload(String path, byte[] bytes, String mime) {
            if (failUpload) throw new IllegalStateException("simulated blob error");
            if (!mime.startsWith("text/csv")) throw new IllegalArgumentException();
            blobs.put(path, bytes);
            uploads++;
        }
        public void tenantMismatchDetected() { mismatches++; }
        ExportWorker worker() { return new ExportWorker(this,this,this,this,this); }
    }
    static void require(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }
    static void run(String name, Runnable test) { test.run(); System.out.println("PASS " + name); }

    public static void main(String[] args) {
        run("Forged tenant ID rejected without leaking Blob", () -> {
            Memory m = new Memory();
            ExportWorker.Result result = m.worker().process(new ExportWorker.Message(JOB_A, TENANT_B,"req-1"));
            require(result == ExportWorker.Result.DROP_FORGED && m.blobs.isEmpty() && m.mismatches == 1,
                    "forged message must be dropped and counted");
            require(m.jobs.get(JOB_A).status() == ExportWorker.Status.PENDING,"legitimate job must survive attack");
        });
        run("Valid export restricted to verified tenant", () -> {
            Memory m = new Memory();
            ExportWorker.Result result = m.worker().process(new ExportWorker.Message(JOB_A, TENANT_A,"req-2"));
            require(result == ExportWorker.Result.COMPLETED && m.blobs.size() == 1,"must export exactly once");
            String path = "exports/" + TENANT_A + "/" + JOB_A + ".csv";
            String csv = new String(m.blobs.get(path), StandardCharsets.UTF_8);
            require(csv.contains("Alpha, task") && !csv.contains("SECRET_ORG_B"),"cross tenant content found");
        });
        run("Idempotent queue redelivery", () -> {
            Memory m = new Memory();
            ExportWorker.Message msg = new ExportWorker.Message(JOB_A, TENANT_A,"req-3");
            require(m.worker().process(msg) == ExportWorker.Result.COMPLETED,"first must complete");
            require(m.worker().process(msg) == ExportWorker.Result.ALREADY_COMPLETED && m.uploads == 1,
                "duplicate delivery must not upload again");
        });
        run("Membership revoked between enqueue and consume", () -> {
            Memory m = new Memory(); m.active.remove(TENANT_B + ":" + USER_B);
            ExportWorker.Result r = m.worker().process(new ExportWorker.Message(JOB_B, TENANT_B,"req-4"));
            require(r == ExportWorker.Result.DROP_REVOKED && m.blobs.isEmpty(),"revoked user's export must be denied");
        });
        run("Transient blob error supports retry and secure CSV", () -> {
            Memory m = new Memory(); m.failUpload = true;
            ExportWorker.Message msg = new ExportWorker.Message(JOB_C, TENANT_A,"req-5");
            require(m.worker().process(msg) == ExportWorker.Result.RETRY,"failed upload should retry");
            require(m.jobs.get(JOB_C).status() == ExportWorker.Status.PENDING,"lease should be released");
            m.failUpload = false;
            require(m.worker().process(msg) == ExportWorker.Result.COMPLETED,"retry must succeed");
            String csv = CsvBuilder.create(List.of(new ExportWorker.Task(JOB_C, "=2+3", "OPEN")));
            require(csv.contains("'=2+3"),"CSV formula should be neutralized");
        });
        System.out.println("5/5 TV3 security & reliability tests passed");
    }
}
