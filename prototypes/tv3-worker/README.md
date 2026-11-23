# TV3 Export Worker

The local Spring Boot demo exports workspaces from the TV2 PostgreSQL schema. It consumes both the TV2 producer's `jobId,tenantId` payload and the previously proposed versioned payload. The Azure profile supplies a queue transport and requires an approved production processor before it can start.

## Chạy trên VS Code Windows

Mở terminal PowerShell tại `prototypes/tv3-worker`. Cần JDK 21 trở lên, Maven 3.9+, Docker Desktop ở Linux container mode và hai cổng Azurite local 10000/10001 còn trống. k6 chỉ cần cho đo tải API.

Lấy `Backend_Core_API_Source_Code.zip` từ [main tại commit đã kiểm tra](https://github.com/hoangphungtech-ops/multi-tenant-project-management/tree/99dec5cdd79015ff190be66a67de0e1065fea93d), giải nén rồi trỏ đến thư mục `backend-core-api/src/main/resources/db/migration`. Demo chạy nguyên V1/V2 của TV2 trên hai volume riêng của Compose project `phung-tv3-demo`.

```powershell
$pw = Read-Host 'Mật khẩu riêng cho PostgreSQL demo' -AsSecureString
$env:TV3_DEMO_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $pw).Password
$migrations = 'E:\path\backend-core-api\src\main\resources\db\migration'
.\scripts\demo.ps1 -Action Verify -MigrationDirectory $migrations
Get-Content .\target\verified-workspace-export.csv
.\scripts\demo.ps1 -Action Start -MigrationDirectory $migrations
```

`Verify` chạy `mvn clean verify` với Spring Boot, PostgreSQL, Azurite Queue và Blob, sau đó xuất bản sao CSV đã tải từ Blob vào `target`. `Start` chạy Worker ở foreground với heap 64–256 MiB và giới hạn direct memory 128 MiB. Compose cấp tối đa 512 MiB cho PostgreSQL và 256 MiB cho Azurite. Nếu cổng PostgreSQL bận, đặt cùng `TV3_DEMO_DB_PORT` cho mọi terminal. Dùng lại mật khẩu của volume PostgreSQL đã khởi tạo.

Ở terminal thứ hai, đặt lại biến mật khẩu demo rồi chạy `.\scripts\demo.ps1 -Action Health -RequireWorker` để kiểm tra Docker, PostgreSQL, Azurite và tiến trình Worker. Đây là kiểm tra tiến trình và hạ tầng. `Verify` kiểm tra khả năng xử lý job thực tế. Dừng Worker bằng Ctrl+C ở terminal đang chạy, sau đó chạy `.\scripts\demo.ps1 -Action Stop` để dừng hai container và giữ dữ liệu.

## Đo tải và theo dõi

- `k6/api-load.js` kiểm tra read-only API workspace theo các ngưỡng p95 < 800 ms, lỗi HTTP < 1% và check HTTP 200 > 99%. Xem lệnh và kết quả trong [báo cáo kiểm thử](../../docs/tv3/test-report.md).
- [Observability](docs/OBSERVABILITY.md) hướng dẫn import Workbook, chọn metric và thực hiện failure drill.
- [Hợp đồng TV2 và giới hạn demo](docs/CONTRACT_TO_CONFIRM.md) ghi các blocker production.
- [Nội dung báo cáo cuối kỳ](../../docs/tv3/final-report.md).
