# Báo cáo kiểm thử TV3 — 23/11/2026

Môi trường Windows, JDK 25.0.5, Maven 3.9.16, Docker Engine 28.5.1, PostgreSQL 16 và Azurite 3.33.0. Maven biên dịch với release 21.

| Hạng mục | Trạng thái | Bằng chứng |
| --- | --- | --- |
| Maven clean verify | PASS | 17 JUnit tests, 0 failure, 0 error, 0 skipped với `demo.ps1 -Action Verify`. |
| Unit và Spring smoke | PASS | 9 queue-poller, 4 parser, 2 lifecycle, 1 Spring context. |
| Prototype security/reliability | PASS | `scripts/run-tests.ps1` cho 5/5 case. |
| Tích hợp và failure drill local | PASS | 1 test chạy Spring Boot với PostgreSQL và Azurite thật. Forged tenant đi poison, job gốc vẫn PENDING và chưa có Blob. Membership INACTIVE hoặc requester NULL làm job FAILED. |
| Blob lỗi và redelivery | PASS | Container không tồn tại gây lỗi upload và giữ job PENDING. Lượt gửi lại hợp lệ tạo CSV. Delivery trùng sau COMPLETED giữ nguyên ETag. |
| CSV thực tế | PASS | Tải nội dung từ Blob, so khớp toàn bộ tệp với expected UTF-8/CRLF. Không có workspace tenant B. |
| Build nguyên bản mã nguồn TV2 | FAIL | `BlobSasService.java:23` thiếu import `com.azure.storage.common.sas.SasProtocol`. Bản tạm dùng để đo local chỉ bổ sung import trong thư mục giải nén ngoài repo. |
| Launcher Health v? RAM | PASS | Hai container healthy, Worker ??ng g?i kh?i ??ng v?i c?u h?nh heap demo. M?u t?i l?c ki?m tra: PostgreSQL 54,17 MiB, Azurite 35,88 MiB, Worker working set 191.397.888 byte (182,5 MiB). ??y l? m?t m?u th?i ?i?m, kh?ng ph?i m?c ??nh. |
| Java 21 runtime | SKIPPED | Lần chạy sử dụng JDK 25.0.5. |
| Export task qua API và production RLS | SKIPPED | TV2 chưa có bảng tasks, job do API tạo thiếu requester và chưa có quyền lookup worker production đã thống nhất. |
| Azure Application Insights live, Workbook, Metric Alert, email | SKIPPED | Chưa có resource/credentials Azure để kiểm chứng ingestion, trạng thái Fired và email. |

CSV lấy trực tiếp từ lần test lúc 15:50 (giờ Bangkok). Test có thể tái tạo bản CSV tại `prototypes/tv3-worker/target/verified-workspace-export.csv`.

```csv
workspace_id,name,description
a982c114-8c69-402e-9956-f761765bee9b,'=formula,"one,two"
```

## Chạy k6

Từ thư mục Worker, dùng môi trường stage được phép với HTTPS và JWT ngắn hạn được cấp qua biến `ACCESS_TOKEN`.

```powershell
$env:BASE_URL = 'https://<authorized-test-host>'
$env:API_PATH = '/api/v1/tenants/<tenant-uuid>/workspaces'
$env:VUS = '5'
$env:DURATION = '2m'
k6 run --summary-export target/k6-summary.json k6/api-load.js
```

Đối với TV2 demo-mode, đặt `LOCAL_DEMO=true`, `BASE_URL=http://127.0.0.1:18080`, `DEMO_USER=demo-owner` và `DEMO_TENANT=11111111-1111-1111-1111-111111111111`. Demo-mode chỉ được script chấp nhận trên loopback. Ngưỡng đo là p95 < 800 ms, HTTP failure < 1%, tỉ lệ check HTTP 200 > 99%.
