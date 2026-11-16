# Kịch bản Demo 10–15 phút

**Mục tiêu:** chứng minh theo đúng trọng số chấm: Kiến trúc 25%, Tự động hóa 20%, Bảo mật 15%, Giám sát 15%,
Ứng dụng 15%, Chi phí & Trình bày 10%.

## Chuẩn bị (T−30 phút, Đợt 3 — chỉ TV1 chạy `terraform apply`)
- [ ] `terraform apply` xong, ghi lại `api_https_url`, URL Static Web App
- [ ] Chạy seed script (TV2): 3 org × 5 user × 20 task
- [ ] Mở sẵn các tab: Web App (cửa sổ thường: Owner A; cửa sổ ẩn danh: User X), Azure Portal (Resource Group,
      PostgreSQL Networking, Storage container, Dashboard, Alert), hộp thư nhận Alert, GitHub Actions
- [ ] Lấy token mới cho `tests/security/tokens.local.json` (token hết hạn sau ~60–90 phút)
- [ ] Terminal mở sẵn ở thư mục repo, cỡ chữ to
- [ ] Phương án dự phòng: video Rollback, ảnh email Alert, thư mục evidence của Đợt 2

## Dòng thời gian

| Phút | Người nói | Nội dung | Thao tác | Bằng chứng trên màn hình |
|---|---|---|---|---|
| 0:00–1:00 | TV4 | Bài toán & nguyên tắc sống còn "không rò rỉ giữa tenant" | Slide 1 | — |
| 1:00–3:30 | TV1 | **Kiến trúc & ranh giới mạng**: VNet 2 subnet, DB private, Blob private, Key Vault + Managed Identity | Sơ đồ thực tế → Portal: PostgreSQL *Networking* (Public access: Disabled), container `exports` (Private), App Service *Identity* | Ảnh trên Portal |
| 3:30–6:00 | TV4 | **Ứng dụng**: đăng nhập Entra → Chuyển Org → tạo task, kéo cột → Members: mời, đổi role → Audit có `request_id` → Export CSV → tải qua SAS | Web App | Link SAS hết hạn sau 5 phút. Thử mở lại link sau khi hết hạn → 403 (nếu kịp) |
| 6:00–8:30 | TV4 | **Bảo mật (Test 1–3) chạy trực tiếp** | `.\tests\security\run.ps1 -Target azure -BaseUrl <api>` → mở `SUMMARY.md` → mở 1 file `.http.txt`: request Org A gọi task Org B → 404 | PASS x/y, raw HTTP có timestamp, token đã che |
| | | **Test 2 bằng UI** (dễ hiểu với người chấm) | Owner A thu hồi User X → cửa sổ ẩn danh của X bấm F5 → thông báo `403 — Không phải thành viên… (request_id …)` | JWT của X vẫn còn hạn nhưng bị chặn |
| 8:30–10:30 | TV3 | **Failure Drill**: đẩy job giả mạo `claimedTenantId` | Script drill → log JSON có `jobId` trong App Insights (KQL) → metric tăng → email Alert đến | Email, Dashboard 4 biểu đồ. Song song: web app vẫn hoạt động |
| 10:30–12:00 | TV1 | **CI/CD & Rollback** | Commit nhỏ → Actions chạy → (dự phòng: video) → rollback về bản trước | Lịch sử workflow, version trên `/health` |
| 12:00–13:00 | TV3 | **Load test k6**: p95 & error rate | Ảnh kết quả k6 + biểu đồ latency | p95 = ⟦…⟧ ms |
| 13:00–14:00 | TV1 | **Chi phí & Cleanup** | Bảng cost, Cost Management thực tế, Cost Alert $25/$50/$75 | Số dư credit |
| 14:00–15:00 | TV2 | Data model & RLS, connection pool, scaling | Slide ERD + policy RLS | — |

`terraform destroy` **ngay sau khi chấm xong** (TV1), rồi chụp Resource Group trống.

## Câu hỏi phản biện thường gặp — người trả lời
| Câu hỏi | Ai | Ý chính (xem DESIGN_REPORT §7) |
|---|---|---|
| Nếu sửa ID trên URL thì sao? | TV4 | 3 lớp: interceptor membership, truy vấn có `tenant_id`, RLS. Demo bằng Test 1 |
| Sao thu hồi có hiệu lực ngay dù JWT còn hạn? | TV2 | JWT chỉ là danh tính. Quyền đọc từ bảng `memberships` mỗi request |
| Message trong queue bị sửa? | TV3 | Worker coi message là không đáng tin, đối chiếu `export_jobs` |
| Tải tăng 10×? | TV1/TV2 | API stateless scale ngang. DB B1ms là nút cổ chai, xử lý bằng pool và PgBouncer |
| Message bị giao 2 lần? | TV3 | Claim nguyên tử, blob path xác định theo jobId, chỉ xóa message sau khi commit |
| Sao không dùng Functions/Container Apps? | TV1 | Bảng Alternative Compute |
| Có lọt token trong log không? | TV3/TV4 | Logback redact + `scan_secrets.py` trước khi nộp |
