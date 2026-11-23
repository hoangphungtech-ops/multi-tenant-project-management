# Hợp đồng TV2 đã đối chiếu

Nguồn là `Backend_Core_API_Source_Code.zip` trên `origin/main` tại commit `99dec5c`. Đã kiểm tra Flyway V1/V2, `ExportQueueService`, `ExportController`, `ExportJob`, `TenantMembershipService` và API workspace.

| Chủ đề | Hợp đồng hiện có |
| --- | --- |
| Queue | TV2 gửi JSON `{"jobId":"<uuid>","tenantId":"<uuid>"}`. Worker giữ hỗ trợ định dạng proposal v1 với `schemaVersion=1` và `claimedTenantId`. Cả hai dạng đều coi claim là dữ liệu không đáng tin. |
| Job | `export_jobs(id, tenant_id, requested_by, status, queue_message_id, blob_name, error_message, created_at, completed_at)`. Trạng thái `PENDING/PROCESSING/COMPLETED/FAILED`. |
| Membership | `tenant_memberships` có `tenant_id, user_id, role, status`. Export API yêu cầu `OWNER` hoặc `MEMBER`. Worker xác minh lại hai role này và trạng thái `ACTIVE`. |
| Dữ liệu | Migrations có `workspaces` và RLS theo `app.tenant_id`, chưa có bảng `tasks`. CSV demo mang các cột `workspace_id,name,description`. |
| API | `POST /api/v1/tenants/{tenantId}/exports` trả 202. GET theo job trả `id,status`. Endpoint tải file chưa xuất hiện trong controller. |

## Luồng demo

Worker dùng tài khoản chủ schema chỉ trong PostgreSQL local, tìm job bằng UUID rồi dùng kết quả làm nguồn tenant có thẩm quyền. Claim sai đi poison với mã `TENANT_MISMATCH` và để job nguyên trạng. Sau khi so khớp, Worker thiết lập context transaction-local, kiểm tra membership và dùng tenant lấy từ job trong điều kiện SQL workspace. Job thiếu requester hoặc membership hợp lệ chuyển sang FAILED. Job chưa thấy trong DB được retry vì producer và Worker có thể chạy đồng thời.

Khoá hàng job tuần tự hóa các lần nhận trùng và tự nhả khi transaction/connection kết thúc. Khoá chia sẻ membership giữ quyền nhất quán trong lúc ghi file. CSV dùng cursor JDBC và file tạm, upload vào private container `exports` với đường dẫn ổn định `{tenantId}/{jobId}.csv`. Worker chỉ commit COMPLETED sau upload thành công. Nếu mất kết nối sau upload nhưng trước commit, lượt sau có thể ghi lại cùng đường dẫn. Trạng thái hoàn tất đã commit cho phép ACK lại mà không ghi lại blob. Nếu poison send thành công và ACK lỗi, Azure Queue có thể nhận trùng envelope. Dùng `sourceMessageId` để nhận diện trùng.

## Điều kiện tích hợp production cần TV2/TV1 xác nhận

- TV2 cần cung cấp dữ liệu `requested_by` có thẩm quyền. `ExportController` hiện tạo `new ExportJob(tenantId)`; constructor chỉ gán tenant, status và thời gian. Worker demo đánh FAILED với job không có requester.
- TV2 cần chốt bảng task, phạm vi người dùng được export và hợp đồng download.
- TV1/TV2 cần chốt cách lookup job theo ID dưới RLS với worker role có quyền tối thiểu. Owner ở localhost có thể bypass RLS, vì vậy demo kiểm tra cô lập bằng điều kiện SQL và không dùng kết quả đó để chứng nhận role production. TV3 không triển khai quyền bypass RLS trên Azure.
- TV1/TV2 cần chốt trạng thái job khi Queue hết retry, quyền Blob và cách phối hợp transaction producer với Queue. Demo giữ job chưa hoàn thành nếu lỗi upload khiến message đi poison.
- Nếu có triển khai Worker Azure, cấu hình `worker,azure` cần một `ExportMessageProcessor` production trước khi có thể khởi động.
