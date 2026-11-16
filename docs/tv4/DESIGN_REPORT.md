# Short Design Report — Multi-Tenant Project Management SaaS trên Microsoft Azure

**Đề tài 06 (Advanced) · Môn Điện toán đám mây · Nhóm 4 thành viên**
Stack: Azure · Java 21 / Spring Boot 3 · PostgreSQL 16 · React (Vite + TS) · Terraform azurerm · GitHub Actions

> **Quy ước của bản nháp:** mọi chỗ ghi `⟦ĐIỀN: …⟧` là **số liệu đo thật** hoặc nội dung do thành viên phụ trách cung cấp
> sau đợt chạy Azure (Tuần 6). Không điền số ước đoán vào những chỗ này.
> Người tổng hợp: TV4. Người cung cấp: TV1 (hạ tầng, chi phí, CI/CD), TV2 (API, DB, RLS), TV3 (Worker, giám sát, k6).

---

## 1. Bài toán & tiêu chí thành công

Một website quản lý dự án/công việc phục vụ nhiều tổ chức (tenant) cùng lúc. Mỗi tenant có thành viên, dự án, task board,
audit log và export CSV riêng. **Yêu cầu sống còn:** không rò rỉ dữ liệu giữa các tenant ở bất kỳ tầng nào (API, DB, cache,
blob, worker, log), kể cả khi người dùng sửa ID trên API hoặc giả mạo job trong hàng đợi.

| Vai trò | Quyền |
|---|---|
| Owner | Toàn quyền org: mời/xóa thành viên, đổi vai trò, CRUD dự án/task, export |
| Member | CRUD dự án/task, xem lịch sử, export |
| Guest | Chỉ đọc. Mọi request ghi bị API trả **403** |

## 2. Kiến trúc

```
 Trình duyệt ──HTTPS──► Azure Static Web Apps (React SPA)
     │  OIDC + PKCE
     ├────────────────► Microsoft Entra External ID (CIAM) ──► JWT access token
     │  Bearer JWT, X-Request-Id
     ▼
 App Service (Linux B1, Java 21) ── api profile ──┐          ┌── worker profile (cùng Plan B1)
     │ VNet Integration (snet-app 10.0.1.0/24)    │          │
     ├──► PostgreSQL Flexible B1ms (snet-db 10.0.2.0/24, private DNS, public access = OFF)
     ├──► Storage Account: Queue `export-jobs` (+ `export-jobs-poison`) ──► Worker
     │                     Blob private `exports/{tenant_id}/{job_id}.csv` ◄── Worker
     ├──► Key Vault (RBAC) ◄── Managed Identity (Key Vault reference cho DB password)
     └──► Application Insights / Log Analytics ──► Dashboard 4 biểu đồ, Metric Alert ──► Action Group (Email)
```

⟦ĐIỀN: chèn **Actual Architecture Diagram** của TV1 (ảnh từ Azure Portal / draw.io sau `terraform apply`)⟧

### 2.1 Ranh giới mạng
- **DB private hoàn toàn:** `public_network_access_enabled = false`, server nằm trong subnet được delegate, phân giải tên qua
  Private DNS Zone liên kết VNet. Từ Internet không có đường tới DB. Chỉ App Service (qua VNet Integration) kết nối được.
- **Blob không public:** `allow_nested_items_to_be_public = false`, container `exports` để `private`, TLS ≥ 1.2.
  Người dùng chỉ tải được qua **User Delegation SAS** (ký bằng Managed Identity, có role *Storage Blob Delegator*),
  read-only, hết hạn ≤ 5 phút, phạm vi 1 blob.
- **Không có secret trong code/repo:** API/Worker dùng System-Assigned Managed Identity. DB password chỉ nằm trong Key Vault
  (`@Microsoft.KeyVault(SecretUri=…)`). Truy cập Storage bằng RBAC, không dùng account key. Frontend là public client
  (PKCE), không có client secret.
- **HTTPS ở mọi điểm vào:** `https_only = true` (`*.azurewebsites.net`), Static Web Apps mặc định HTTPS,
  cộng các security header (CSP, HSTS, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` để SAS không lọt qua Referer).

## 3. Thiết kế Multi-Tenant (phòng thủ nhiều lớp)

Mô hình **shared database, shared schema**, mọi bảng nghiệp vụ có `tenant_id`. Đây là lựa chọn rẻ nhất cho B1ms, đổi lại
isolation phải được bảo đảm bằng nhiều lớp độc lập:

| Lớp | Cơ chế | Chặn được |
|---|---|---|
| 1. Xác thực | Spring Security verify JWT Entra (issuer, audience, chữ ký, hạn) | Không token / token giả (`alg=none`) → 401 |
| 2. Ủy quyền theo tenant | `TenantMembershipInterceptor`: đọc `tenantId` từ path `/orgs/{tenantId}/…`, tra bảng **memberships mỗi request**, kiểm role | Truy cập org không thuộc về mình → 403; Guest ghi → 403; quyền bị thu hồi có hiệu lực **ngay**, không chờ JWT hết hạn |
| 3. Truy vấn có phạm vi | Repository luôn lọc `WHERE tenant_id = :verifiedTenant AND id = :id` | IDOR đổi ID tài nguyên → 404 (không tiết lộ tài nguyên tồn tại ở tenant khác) |
| 4. PostgreSQL RLS | `SET LOCAL app.tenant_id` trong transaction + policy `tenant_id = current_setting('app.tenant_id')::uuid`; reset trên connection trả về pool | Lỗi lập trình quên điều kiện tenant ở lớp 3 vẫn bị DB chặn |
| 5. Ràng buộc dữ liệu | `UNIQUE(tenant_id, name)` trên `projects` | Hai tenant cùng tên project không xung đột (Test 3) |
| 6. Cache | Khóa luôn có tiền tố `tenant:{id}:…`, invalidate theo tenant | Lẫn dữ liệu qua cache (Test 3) |
| 7. Blob | Đường dẫn `exports/{tenant_id}/{job_id}.csv` lấy từ bản ghi DB, SAS scope 1 blob | Đoán/tải file tenant khác |
| 8. Worker | Message trong queue **không đáng tin**: Worker đọc `export_jobs` theo `jobId`, so `claimedTenantId` với `tenant_id` thật, kiểm lại membership của người yêu cầu | Job giả mạo tenant → từ chối, ghi log `jobId`, tăng metric, bắn Alert (Failure Drill) |
| 9. Log | JSON log với MDC `requestId/jobId/tenantId`; redact JWT, password, SAS | Rò rỉ bí mật qua log/bằng chứng |

⟦ĐIỀN (TV2): đoạn SQL policy RLS thật, tên role DB của API/Worker, cách reset tenant context trên HikariCP⟧

### 3.1 Mô hình dữ liệu (6 bảng)
`tenants`, `memberships(tenant_id, user_id, role)`, `projects(tenant_id, name, … UNIQUE(tenant_id,name))`,
`tasks(tenant_id, project_id, …)`, `audit_events(tenant_id, actor, action, entity, request_id, details JSONB)`,
`export_jobs(tenant_id, requested_by, status, blob_path, …)`. `export_jobs` là **nguồn sự thật** cho Worker.
⟦ĐIỀN (TV2): ERD + migration Flyway⟧

### 3.2 Luồng Export bất đồng bộ
1. `POST /orgs/{t}/exports` (Owner/Member) → API ghi `export_jobs(QUEUED)` + audit → gửi message
   `{schemaVersion, jobId, claimedTenantId, requestId}` → trả `202 {jobId}`.
2. Worker nhận message (visibility timeout), tra `export_jobs` theo `jobId`, so tenant, kiểm membership, claim job nguyên tử.
3. Query tasks bằng tenant **đã xác minh từ DB**, ghi CSV (chống CSV formula injection) lên Blob private, đánh dấu `COMPLETED`.
4. UI poll `GET /exports/{jobId}`. Khi xong, gọi `GET …/download` → API cấp SAS read-only 5 phút → trình duyệt tải trực tiếp từ Blob.

## 4. Kết quả kiểm chứng bảo mật

Bộ test tự động: `tests/security/` (pytest, gọi thẳng API, ghi raw HTTP + timestamp, tự che token).
Trước khi dùng, bộ test được **tự kiểm chứng** bằng mock API cố tình có lỗ hổng (`selfcheck.py`): bản mock an toàn
cho 34/34 PASS, còn mỗi lỗ hổng cố ý (IDOR, membership cũ, unique toàn cục, cache dùng chung) đều làm test tương ứng FAIL.
Như vậy bộ test không phải loại "luôn xanh".

| Test | Kịch bản | Kết quả trên Azure | Bằng chứng |
|---|---|---|---|
| Test 1 — IDOR chéo tenant | 13 kiểu đọc + 11 kiểu ghi/xóa/export bằng JWT Org A lên ID của Org B, thêm token giả, Guest ghi | ⟦ĐIỀN: PASS x/y, thời điểm chạy⟧ | `docs/tv4/evidence/⟦run⟧/test_org_a_cannot_*.http.txt` |
| Test 2 — Thu hồi membership | Owner xóa X → dùng lại JWT cũ của X | ⟦ĐIỀN: status request kế tiếp = 403, khoảng cách ms⟧ | `…/test_revoked_member_is_blocked_on_next_request.http.txt` |
| Test 3 — Trùng tên & cache | A và B cùng tạo "Cloud Project", đọc 2 lần, sửa/xóa ở A | ⟦ĐIỀN⟧ | `…/test_same_project_name_in_two_tenants.http.txt` |
| Failure Drill — Worker giả mạo tenant (TV3) | Message có `claimedTenantId` của org khác | ⟦ĐIỀN: log JSON có jobId, metric tăng, email Alert lúc hh:mm (độ trễ ~x phút), API vẫn 200⟧ | ⟦ĐIỀN: ảnh email + ảnh metric⟧ |

## 5. Deployment & Automation
- **IaC:** toàn bộ hạ tầng trong `infra/` (Terraform azurerm ~> 4.0). `terraform apply` dựng từ đầu, `terraform destroy` xóa sạch.
- **CI/CD:** GitHub Actions xác thực Azure bằng **OIDC Federated Credentials** (không lưu client secret trong GitHub).
  ⟦ĐIỀN (TV1): pipeline build → test → deploy API/Worker/SPA⟧
- **Rollback:** ⟦ĐIỀN (TV1): Deployment Slot swap hoặc redeploy artifact/tag trước, thời gian rollback đo được, link video⟧

## 6. Reliability & Observability
- Log JSON có `request_id` (API) và `job_id` (Worker). Mỗi lỗi trên UI hiển thị `request_id` để tra ngược.
- **Dashboard 4 biểu đồ:** ⟦ĐIỀN (TV3): ví dụ request rate & 5xx của API, p95 latency, độ sâu queue/poison queue, số tenant-mismatch⟧
- **Alert:** Metric Alert → Action Group Email.
  ⟦ĐIỀN (TV3/TV1): alert bám vào metric tenant-mismatch của Worker. Terraform hiện có alert `Http5xx` của API, cần thêm alert cho Failure Drill⟧
- **Load test k6** (2–3 phút, có warmup): ⟦ĐIỀN (TV3): p95 = … ms, error rate = … %, số VU, endpoint⟧

## 7. Câu hỏi phản biện

### 7.1 Scaling — chuyện gì xảy ra khi tải tăng 10×?
- **API stateless** (JWT + membership trong DB, không session server), nên scale-out App Service Plan theo chiều ngang được ngay.
  Cache in-process theo tenant chấp nhận được vì mỗi instance tự invalidate khi ghi và TTL ngắn. Khi có nhiều instance,
  chuyển sang Azure Cache for Redis với cùng quy ước khóa `tenant:{id}:…` để invalidate dùng chung.
- **Worker** scale độc lập với API (tách Web App, hoặc tách Plan riêng khi cần). Queue là bộ đệm hấp thụ đột biến:
  nhiều job export đổ vào không làm chậm API.
- **Nút cổ chai đầu tiên là PostgreSQL B1ms** (1 vCPU burstable, giới hạn kết nối thấp). Thứ tự xử lý: index `(tenant_id, …)`,
  phân trang, rồi nâng lên General Purpose, read replica cho truy vấn đọc/export, PgBouncer (built-in của Flexible Server).
- **Noisy neighbor:** một tenant export lớn không được làm nghẽn tenant khác. Cách làm: giới hạn số job đồng thời mỗi tenant,
  rate limit theo tenant ở API, và stream CSV thay vì nạp toàn bộ vào RAM.
- **Khi lên rất lớn** (tenant doanh nghiệp đòi tách biệt vật lý): chuyển các tenant đó sang database riêng (silo). Lớp 2 và 3
  giữ nguyên, chỉ thêm định tuyến connection theo tenant.

### 7.2 Connection Pool
- Số kết nối tối đa ≈ `instances × (HikariCP maximumPoolSize của API + của Worker)` phải nhỏ hơn `max_connections`
  của B1ms (trừ phần dự trữ cho hệ thống). ⟦ĐIỀN (TV2): giá trị `maximumPoolSize`, `max_connections` thực tế⟧
- Pool nhỏ cộng với transaction ngắn tốt hơn pool lớn: CPU 1 vCPU không xử lý song song nhiều truy vấn hơn số core.
- **Rủi ro riêng của multi-tenant:** connection dùng lại có thể mang theo `app.tenant_id` của request trước. Biện pháp:
  `SET LOCAL` (chỉ sống trong transaction), hoặc reset khi trả connection về pool. Test 1 và 3 chạy liên tiếp giữa hai tenant
  trên cùng pool để phát hiện lỗi này.
- Scale-out nhiều instance sẽ nhân số kết nối lên, nên phải giảm pool size mỗi instance hoặc bật PgBouncer.

### 7.3 Retry Safety / Idempotency
- Azure Queue giao hàng **at-least-once**, nên một message có thể đến 2 lần hoặc đến sau khi Worker chết giữa chừng.
- Worker an toàn khi retry vì: (1) claim job nguyên tử trên `export_jobs` (`UPDATE … WHERE status IN ('QUEUED', lease hết hạn)`);
  (2) job đã `COMPLETED` thì bỏ qua; (3) đường dẫn blob xác định theo `jobId`, ghi lại thì ghi đè đúng file, không sinh file rác;
  (4) chỉ xóa message sau khi đã commit kết quả cuối; (5) lỗi tạm thời dùng backoff + jitter; quá số lần thì chuyển
  `export-jobs-poison` với metadata đã làm sạch.
- Phía client: `POST /exports` không idempotent theo mặc định. Bấm 2 lần tạo 2 job, vô hại vì mỗi job độc lập.
  Có thể bổ sung header `Idempotency-Key`.
- Chi tiết thiết kế: `prototypes/tv3-worker/docs/CONTRACT_TO_CONFIRM.md`. ⟦ĐIỀN (TV3): kết quả test retry/poison⟧

### 7.4 Alternative Compute
| Lựa chọn | Ưu | Nhược | Kết luận |
|---|---|---|---|
| **App Service B1 (đã chọn)** | HTTPS sẵn, VNet Integration, Deployment Slot, Java 21 native, chi phí cố định thấp, API và Worker chung 1 Plan | Không scale về 0, tốn tiền khi bật | Phù hợp nhất với ngân sách $100 và chiến lược bật theo đợt |
| Azure Container Apps | Scale về 0, scale Worker theo độ dài queue (KEDA), revision để rollback | Cold start của Spring Boot, cấu hình VNet phức tạp hơn | Ứng viên tốt nhất cho Worker khi tải thật |
| Azure Functions (Queue trigger) | Trả theo lần chạy, tự scale theo queue | Spring Boot nặng cho Functions, giới hạn thời gian chạy, VNet cần gói Premium/Flex | Hợp với Worker nhỏ, viết lại không theo Spring |
| AKS | Linh hoạt tối đa | Chi phí và vận hành vượt quy mô đồ án | Không chọn |

## 8. Chi phí & Cleanup
Ước tính theo workload (Region Southeast Asia): ⟦ĐIỀN (TV1): bảng Cost Estimate từ Azure Pricing Calculator⟧

| Tài nguyên | SKU | Nếu bật 24/7 (USD/tháng) | Theo 3 đợt (~6 giờ + ngày bảo vệ) |
|---|---|---|---|
| App Service Plan | B1 Linux | ⟦ĐIỀN⟧ (~13 theo tài liệu nhóm) | ⟦ĐIỀN⟧ |
| PostgreSQL Flexible | B1ms + 32 GB | ⟦ĐIỀN⟧ | ⟦ĐIỀN⟧ |
| Storage | Standard LRS | ⟦ĐIỀN⟧ | ⟦ĐIỀN⟧ |
| Key Vault, App Insights, Log Analytics | Standard / PerGB | ⟦ĐIỀN⟧ | ⟦ĐIỀN⟧ |
| Static Web Apps, Entra External ID | Free (≤ 50.000 MAU) | 0 | 0 |
| **Tổng** | | ⟦ĐIỀN⟧ | ⟦ĐIỀN: chi phí thực tế từ Cost Management⟧ |

Kiểm soát: Cost Alert ở $25 / $50 / $75. 85% thời gian phát triển chạy local bằng Docker (PostgreSQL + Azurite) hoặc mock, $0.
**Cleanup:** `terraform destroy` → kiểm tra Resource Group trống trên Portal → xóa app registration test nếu không dùng nữa →
Key Vault soft-delete 7 ngày (purge nếu cần tạo lại cùng tên). ⟦ĐIỀN (TV1): checklist + ảnh RG trống⟧

## 9. Giới hạn & hướng phát triển
- Cache in-process chưa dùng chung giữa nhiều instance (xem 7.1).
- Lời mời thành viên đang thêm trực tiếp theo email. Bản production cần luồng lời mời có xác nhận và hết hạn.
- Chưa có WAF/Front Door hay rate limit theo tenant.
- ⟦ĐIỀN: các giới hạn thực tế phát hiện khi chạy Azure⟧
