# Observability và failure drill TV3

Worker phát JSON theo Logstash format. Poller gắn `correlationId` local cho từng lượt nhận và `jobId` dạng UUID. Khi DB tra thành công, log kiểm tra scope hoặc từ chối ghi `tenantId` có thẩm quyền. Giá trị `requestId` gửi từ Queue không được ghi trực tiếp vì nó có thể chứa dữ liệu nhạy cảm. Log lỗi xử lý chỉ dùng tên loại lỗi. Payload Queue, JWT, mật khẩu và SAS không được đưa vào các log sự kiện TV3.

## Application Insights

Dùng Application Insights Java 3.x agent của App Service hoặc chạy JVM với `-javaagent:<path-to-agent-jar>`. Đặt `APPLICATIONINSIGHTS_CONNECTION_STRING` qua cấu hình đã được TV1 cấp, `APPLICATIONINSIGHTS_CONFIGURATION_FILE` trỏ đến `observability/applicationinsights.json`. Cấu hình dùng role `tv3-export-worker` và bật thu log INFO. [Cấu hình Java agent của Microsoft](https://learn.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config)

Import `observability/workbook.json` qua Azure Monitor Workbooks Advanced Editor và chọn Log Analytics workspace liên kết với Application Insights. Workbook chứa bốn biểu đồ 24 giờ theo cửa sổ 5 phút: kết quả xử lý delivery, p95 của lượt xử lý thành công (có cả ACK lại), số lần phát hiện tenant sai, và lỗi polling. Các số liệu này là của Worker. Ngưỡng k6 dùng cho HTTP API. [Biểu đồ trong Azure Workbooks](https://learn.microsoft.com/en-us/azure/azure-monitor/platform/workbooks-chart-visualizations)

`infra/monitoring.tf` tại main đã khai báo Metric Alert `alert-worker-forged-tenant-mismatch` và Action Group email. Alert dùng `worker_tenant_mismatch_total`, Total > 0 trong 5 phút, kiểm tra mỗi phút. Worker demo tăng counter cùng tên. Sau khi môi trường Azure sẵn sàng, xác minh metric thực sự có mặt trong Metrics Explorer và đúng namespace/aggregation trước khi kết luận rule hoạt động. Địa chỉ email do TV1 cấu hình từ `alert_email`.

## Drill

1. Chạy `demo.ps1 -Action Verify`. Test tạo hai tenant, job hợp lệ của A, rồi gửi Queue message có job của A nhưng claim B.
2. Xác minh poison chứa `TENANT_MISMATCH` và source message ID, job còn PENDING và Blob chưa tồn tại. Sau đó gửi claim đúng và kiểm tra CSV chỉ chứa workspace của A.
3. Khi có môi trường Azure được phép, dùng fixture riêng của môi trường đó. Đối chiếu JSON log, custom metric, lịch sử Fired của rule và email Action Group. Lưu timestamp/correlation ID của lần đo, không đưa connection string, token hoặc SAS vào bằng chứng.

Local drill chạy qua PostgreSQL/Azurite trong test tích hợp. Ingestion Azure, Workbook đã deploy, trạng thái Fired và email cần kiểm tra trên tenant Azure đã cấp quyền.
