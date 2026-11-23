# Thành viên 3 — Export Worker, Observability và Load Test

## Thiết kế và đóng góp

Worker Spring Boot chạy nền, nhận từng delivery từ Azure Storage Queue. Poller kiểm tra JSON, gia hạn visibility trong khi xử lý, ACK theo pop receipt mới nhất và dùng retry có exponential backoff cùng jitter. Payload lỗi hoặc hết retry đi poison queue; envelope chỉ mang mã lỗi và metadata kỹ thuật. Failure Drill kiểm tra tình huống message mang job có thật nhưng khai tenant khác.

Trong demo, PostgreSQL là nguồn có thẩm quyền cho tenant và người yêu cầu. Worker xác minh claim rồi kiểm tra lại membership hiện hành. Lệnh đọc workspace dùng tenant từ hàng job. Khoá transaction trên job ngăn hai lượt nhận cùng upload đồng thời. CSV được ghi theo cursor, escape ký tự CSV và vô hiệu hoá công thức bảng tính. Blob có tên xác định theo tenant/job. Worker chỉ ghi COMPLETED sau khi upload thành công. Test xác nhận delivery lặp giữ nguyên ETag sau khi job hoàn tất.

Demo dùng hai container, PostgreSQL và Azurite, với Worker chạy JVM trên Windows. Launcher khởi tạo schema từ migrations TV2 trên volume riêng, kiểm tra health và dừng giữ dữ liệu. Log JSON có job, correlation và tenant đã xác minh. Workbook cung cấp bốn biểu đồ. TV3 dùng tên metric đã khai báo trên alert của TV1 và bổ sung quy trình đối chiếu Action Group email.

## Phạm vi đã kiểm tra

- Spring Boot, PostgreSQL và Azurite chạy thật. Xác nhận CSV workspace từ Blob, lỗi upload, redelivery, membership bị thu hồi, thiếu requester và claim sai tenant.
- Đo tải HTTP sử dụng `k6/api-load.js`. Kết quả, cấu hình, phiên bản và giới hạn diễn giải nằm trong [báo cáo kiểm thử](test-report.md).
- Nhánh TV3 giữ nguyên API và schema TV2. Kho lưu trữ TV2 hiện thiếu bảng tasks và API tạo job chưa gán `requested_by`. Tích hợp production cần hai hợp đồng này, quyền lookup job dưới RLS và môi trường Azure có quyền xác minh telemetry, alert và email. Chi tiết ở [hợp đồng TV2](../../prototypes/tv3-worker/docs/CONTRACT_TO_CONFIRM.md).
