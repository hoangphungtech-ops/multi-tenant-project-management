# Backend Core API – Thành viên 2

Starter project gồm Spring Boot 3.4, Java 21, PostgreSQL 16, Flyway, Spring Security JWT, RLS, CRUD Workspace, AuditEvent và tích hợp Azure Queue/Blob.

> Đây là bộ source khởi tạo để học tập và tích hợp nhóm. Cần điền cấu hình Entra ID thật, hoàn thiện chính sách nghiệp vụ/API Contract và chạy kiểm thử bảo mật trước khi triển khai production.

## 1. Yêu cầu
- JDK 21
- Maven 3.9+
- Docker Desktop
- Git (nếu cần push lên GitHub)

## 2. Khởi chạy PostgreSQL và Azurite
```bash
docker compose up -d
docker compose ps
```
PostgreSQL: `localhost:5432`; Azurite Blob: `localhost:10000`; Queue: `localhost:10001`.

Tạo file `.env` từ `.env.example` nếu dùng Docker Compose với biến môi trường:
```bash
cp .env.example .env
```
Trên Windows PowerShell có thể dùng:
```powershell
Copy-Item .env.example .env
```

## 3. Chạy Backend local
Flyway tự chạy migration khi ứng dụng khởi động.
```bash
mvn spring-boot:run
```
API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`  
Health: `http://localhost:8080/actuator/health`

Profile `api` được khai báo sẵn. Chạy:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=api
```

## 4. Demo authentication
Mặc định `APP_SECURITY_DEMO_MODE=true` cho phép chạy local không cần Entra ID. Trong chế độ này, header `X-Demo-User` và `X-Demo-Tenant` mô phỏng danh tính. Chỉ dùng để demo local; tuyệt đối tắt ở môi trường thật.

Ví dụ:
```bash
curl -H "X-Demo-User: demo-owner" -H "X-Demo-Tenant: 11111111-1111-1111-1111-111111111111" http://localhost:8080/api/v1/tenants/11111111-1111-1111-1111-111111111111/workspaces
```
Seed data tạo tenant và membership mẫu; xem `R__seed_data.sql`.

Khi dùng Entra ID:
- Đặt `APP_SECURITY_DEMO_MODE=false`.
- Cấu hình `ENTRA_ISSUER_URI` và `ENTRA_AUDIENCE`.
- Frontend gửi `Authorization: Bearer <access_token>`.
- Token phải có issuer, audience và claim định danh phù hợp. Mặc định code đọc `oid`, sau đó `sub`.

## 5. API chính
| Method | Endpoint | Ý nghĩa |
|---|---|---|
| POST | `/api/v1/tenants/{tenantId}/workspaces` | Tạo workspace |
| GET | `/api/v1/tenants/{tenantId}/workspaces` | Danh sách workspace |
| GET | `/api/v1/tenants/{tenantId}/workspaces/{id}` | Chi tiết |
| PUT | `/api/v1/tenants/{tenantId}/workspaces/{id}` | Cập nhật |
| DELETE | `/api/v1/tenants/{tenantId}/workspaces/{id}` | Xóa |
| POST | `/api/v1/tenants/{tenantId}/exports` | Tạo Export Job |
| GET | `/api/v1/tenants/{tenantId}/exports/{jobId}/sas` | Lấy SAS download URL |

## 6. RLS và connection pooling
Mỗi request đặt tenant context bằng transaction-scoped `set_config(..., true)` (tương đương SET LOCAL). Điều này quan trọng vì HikariCP tái sử dụng connection. Tài khoản ứng dụng không nên là superuser, table owner hoặc có BYPASSRLS. Với triển khai thật, hãy tạo riêng migration/role runtime không sở hữu bảng và kiểm tra RLS bằng integration test với đúng runtime role.

## 7. Azure/Azurite
Azurite cần storage connection string hợp lệ. `UseDevelopmentStorage=true` chỉ hoạt động nếu SDK có thể truy cập endpoint chuẩn; nếu dùng Docker và host khác nhau, hãy cấu hình endpoint/connection string theo môi trường của bạn. `AzureStorageConfig` dùng connection string. SAS trong Azurite có thể cần account key chuẩn của Azurite; không dùng chuỗi key giả trong `.env.example`.

## 8. GitHub
Không commit `.env`, token, certificate, client secret hoặc account key thật. Commit `.env.example` thay cho `.env`.

## 9. Các điểm cần nhóm xác nhận
- API Contract và quy tắc tên endpoint.
- Entra claim định danh người dùng (`oid`/`sub`) và tenant mapping.
- Ma trận quyền Owner/Member/Guest cho từng thao tác.
- Ai được tạo export và tải file.
- Chính sách lưu trữ/xóa AuditEvent.
