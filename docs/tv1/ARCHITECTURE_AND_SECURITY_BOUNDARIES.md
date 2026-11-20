# Kiến Trúc Thực Tế, Ranh Giới Bảo Mật & Phản Biện Thiết Kế (TV1)

Tài liệu này phục vụ mục **Architecture and justification (25%)** và **Deployment and automation (20%)** trong báo cáo cuối kỳ.

---

## 1. Giải thích Ranh giới Mạng & Danh tính (Identity & Network Boundaries)

> *"A cloud boundary is not automatically a private network.Application identities control service access. Databases are not browser endpoints."*

Hệ thống được thiết kế tách biệt thành **3 vùng tin cậy (Trust Boundaries)** rõ rệt trên Microsoft Azure:

1. **Vùng Public Ingress (Chỉ mở HTTPS 443):**
   * **Frontend:** `Azure Static Web Apps` (`swa-mtpm-saas-...`) phục vụ giao diện React SPA qua HTTPS mặc định.
   * **Backend API:** `Azure App Service` (`app-mtpm-saas-api-...`) bật cứng `https_only = true` (mọi request HTTP thường tự động bị từ chối/chuyển hướng sang HTTPS TLS 1.2+).
2. **Vùng Mạng Nội Bộ Ứng Dụng (`snet-app` — `10.0.1.0/24`):**
   * Cả 2 ứng dụng `api` và `worker` được gắn vào subnet `snet-app` thông qua **App Service Regional VNet Integration** (`Microsoft.Web/serverFarms` delegation).
   * Điều này cho phép `api` và `worker` đi theo đường mạng nội bộ của Azure sang subnet cơ sở dữ liệu mà không đi qua Internet công cộng.
3. **Vùng Dữ Liệu Riêng Tư (`snet-db` — `10.0.2.0/24` & Private Storage):**
   * **PostgreSQL Flexible Server (`psql-mtpm-saas-...`):** Đặt hoàn toàn trong `snet-db` với cấu hình **`public_network_access_enabled = false`** và phân giải qua `Private DNS Zone` (`*.private.postgres.database.azure.com`). Không một máy tính nào từ bên ngoài Internet hay trình duyệt có thể kết nối trực tiếp tới cổng `5432` của Database.
   * **Azure Storage Account (`stmtpmsaas...`):** Bật cứng **`allow_nested_items_to_be_public = false`** và container `exports` ở chế độ **`private`**. Người dùng cuối chỉ tải được file CSV của đúng tổ chức mình thông qua **SAS Token ngắn hạn (5 phút)** do API cấp sau khi đã kiểm tra `Membership`.
4. **Workload Identities (Không dùng Access Key tĩnh trong code):**
   * Cả `api` và `worker` đều bật **System-Assigned Managed Identity**.
   * Quyền truy cập được cấp theo nguyên tắc tối thiểu (*Least Privilege*) bằng `azurerm_role_assignment`:
     * `Key Vault Secrets User` → Đọc mật khẩu DB từ Azure Key Vault qua cơ chế `@Microsoft.KeyVault(SecretUri=...)`.
     * `Storage Blob Data Contributor` & `Storage Blob Delegator` → Ghi file CSV và ký User Delegation SAS Token.
     * `Storage Queue Data Contributor` → Đẩy và tiêu thụ message trên `export-jobs` & `export-jobs-poison`.

---

## 2. Vùng Lỗi (Failure Domains) & Khả năng Sống sót khi Sự cố

* **Tách biệt Runtime giữa `api` và `worker`:**
  * Dù dùng chung 1 codebase Spring Boot để đồng nhất model/RLS, khi triển khai lên Azure hệ thống tách thành **2 Web Apps độc lập**:
    * `app-...-api` chạy với `SPRING_PROFILES_ACTIVE=api,azure`.
    * `app-...-worker` chạy với `SPRING_PROFILES_ACTIVE=worker,azure` (`web-application-type: none`).
  * **Điều gì vẫn hoạt động khi xảy ra Required Failure (Forged Tenant ID / Queue Backlog)?**
    * Khi `worker` nhận một message có `claimedTenantId` giả mạo hoặc khi hàng đợi `export-jobs` bị ùn ứ, `worker` từ chối ngay tại bước đối chiếu bảng `export_jobs` mà không chạm vào bảng `tasks`.
    * Toàn bộ luồng người dùng trên `api` (Đăng nhập, Xem/Tạo/Sửa Project & Task Board, Quản lý Membership, Xem Audit Log) **vẫn hoạt động 100% bình thường**, không bị treo hay ảnh hưởng bởi sự cố ở tầng Worker.

---

## 3. Bảo vệ Quyết định Thiết kế (Design Decisions to Defend)

### 3.1. Tại sao chọn Azure App Service (Linux) + PostgreSQL Flexible Server? Phương án thay thế là gì?
* **Lý do chọn:**
  * **Azure App Service (`B1` Linux):** Cung cấp sẵn HTTPS chuẩn (`*.azurewebsites.net`), tích hợp sẵn VNet Integration, Managed Identity và Key Vault Reference mà không cần trả phí cố định cho Load Balancer riêng hay NAT Gateway đắt đỏ.
  * **PostgreSQL Flexible Server (`B1ms`):** Hỗ trợ đầy đủ **Row-Level Security (RLS)** ở tầng engine để làm lớp phòng thủ thứ hai (*Defense-in-Depth*) bên dưới tầng kiểm tra của Spring Boot API.
* **Phương án thay thế (Alternative):**
  * Có thể dùng **Azure Container Apps (ACA)** hoặc **Azure Kubernetes Service (AKS)**. Tuy nhiên, AKS quá nặng về chi phí quản lý node cho đồ án 6–8 tuần, còn ACA khi gắn vào VNet riêng (Internal/External VNet environment) phức tạp hơn và tốn thêm phí hạ tầng mạng so với App Service `B1` chạy chung 2 apps (`api` + `worker`) trên cùng 1 Service Plan.

### 3.2. Tác động của việc Scale (Mở rộng ngang) lên Session, Cache và Database Connections
1. **Sessions:** Ứng dụng thiết kế hoàn toàn **Stateless** (xác thực bằng OIDC JWT trên mỗi request và kiểm tra lại bảng `memberships` trong DB). Do đó khi tăng số instance `api` từ 1 lên N, người dùng không bao giờ bị mất phiên đăng nhập (không cần Sticky Session).
2. **Caches:** Mọi khóa cache đều bắt buộc gắn tiền tố định danh tổ chức: `tenant:{tenant_id}:projects:...`. Nếu scale nhiều instance, dùng cache phân tán (hoặc инвалиdation tức thì) để đảm bảo khi xóa membership (**Test 2**) hoặc tạo trùng tên project giữa 2 tổ chức (**Test 3**) không bao giờ xảy ra xung đột.
3. **Database Connections:** Gói PostgreSQL `B_Standard_B1ms` có giới hạn kết nối tối đa (`max_connections` ~ 50). Nếu mỗi instance Spring Boot để mặc định `HikariCP maximum-pool-size = 10`, khi scale lên nhiều instance cho cả `api` và `worker` sẽ dễ làm cạn kết nối. **Giải pháp:** Giới hạn `spring.datasource.hikari.maximum-pool-size = 5` trên mỗi instance (hoặc bật PgBouncer tích hợp sẵn trên Azure PostgreSQL Flexible Server khi cần scale lớn).

### 3.3. Chiến lược CI/CD Rollback trên gói tiết kiệm `B1`
* Gói `Basic B1` không hỗ trợ tính năng *Deployment Slots Swap* (vốn chỉ có từ gói `Standard S1` ~$70/tháng).
* Để vừa tiết kiệm ngân sách vừa đảm bảo khả năng **Rollback tức thì**, pipeline [deploy-and-rollback.yml](../../.github/workflows/deploy-and-rollback.yml) lưu trữ mỗi bản build `.jar` ổn định thành một **Immutable GitHub Release Artifact** gắn với `Run ID` và `Commit SHA`. Khi phát hiện bản mới lỗi, kỹ sư trực chỉ cần chạy workflow ở chế độ `rollback` với `rollback_run_id` của bản ổn định trước đó để khôi phục dịch vụ trong ~1 phút và tự động kiểm tra lại `/actuator/health`.
