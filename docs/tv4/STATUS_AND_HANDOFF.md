# TV4 — Trạng thái & Bàn giao (Frontend, Security QA, Báo cáo)

## Đã xong và đã kiểm chứng trên máy local
| Hạng mục | Vị trí | Cách kiểm chứng |
|---|---|---|
| Web App: Đăng nhập Entra/OIDC (MSAL, PKCE), Chuyển Org, Dự án, Task Board, Quản lý Member, Audit History, Export CSV + tải qua SAS | `frontend/` | `npm run build` (tsc strict + vite) thành công. Chạy `npm run dev:mock` trên mock API; luồng Export → SAS → tải CSV đã kiểm bằng HTTP |
| Bộ Automated API Security Tests 1–3 (pytest) | `tests/security/` | `python tests/security/selfcheck.py` → 5/5: mock an toàn 34/34 PASS; 4 lỗ hổng cố ý đều bị bắt |
| Bộ ghi bằng chứng: input, timestamp, raw HTTP, tự che JWT/SAS/password | `tests/security/evidence.py` | `test_0_evidence_redaction.py` |
| Máy quét rò rỉ bí mật trước khi nộp | `tests/security/scan_secrets.py` | Đã thử với file chứa JWT/SAS giả → phát hiện, exit 1 |
| Mock API theo contract (chỉ để dev/QA) | `tests/mock_api/server.py` | — |
| Bản nháp Short Design Report | `docs/tv4/DESIGN_REPORT.md` | Có chỗ `⟦ĐIỀN⟧` chờ số liệu thật |
| Kịch bản Demo 10–15 phút | `docs/tv4/DEMO_SCRIPT.md` | — |
| API Contract đề xuất | `docs/tv4/API_CONTRACT_PROPOSAL.md` | **Chờ TV2 xác nhận** |

## Chưa làm được (phụ thuộc thành viên khác) — không trình bày như đã xong
- **Chưa chạy Test 1–3 trên backend thật:** repo chưa có API Spring Boot của TV2. Mọi kết quả PASS hiện tại là trên **mock**.
- **Chưa thử đăng nhập Entra thật:** cần tenant External ID và 2 app registration (SPA + API).
- **Chưa có bằng chứng Azure** (`docs/tv4/evidence/`): sẽ thu thập ở Đợt 2 (Tuần 6).
- **Chưa kiểm tra UI bằng mắt trên trình duyệt** trong phiên phát triển này (chỉ kiểm qua build, type-check và gọi HTTP).

## Cần thành viên khác xử lý
### TV2 (Backend)
1. Xác nhận hoặc sửa `docs/tv4/API_CONTRACT_PROPOSAL.md` (5 điểm ở cuối file). Khi đổi, TV4 chỉ sửa
   `frontend/src/api/client.ts` và `tests/security/api_paths.py`.
2. Bật CORS cho origin Static Web App và `http://localhost:5173`. Allow headers `Authorization, Content-Type, X-Request-Id`;
   expose `X-Request-Id`.
3. Nhận header `X-Request-Id` từ client (nếu hợp lệ) và đưa vào MDC + `audit_events.request_id`, để bằng chứng test nối được với log.
4. Seed script cần có: Org A với ít nhất 1 OWNER, 1 GUEST, 1 MEMBER "User X"; Org B với 1 OWNER, có project và task.
   Nên có 1 user thuộc 2 org (để demo Chuyển Org).

### TV1 (Infra/CI-CD)
1. Terraform **chưa có `azurerm_static_web_app`** cho frontend (tài liệu nhóm yêu cầu SWA). Đề xuất: SKU Free,
   output `default_host_name`, và thêm hostname đó vào CORS của API.
2. Workflow build frontend: `npm ci && npm run build` trong `frontend/`, truyền `VITE_API_BASE_URL`, `VITE_ENTRA_CLIENT_ID`,
   `VITE_ENTRA_AUTHORITY`, `VITE_API_SCOPE` (không phải secret, có thể để trong GitHub Variables).
3. Job CI chạy `python tests/security/selfcheck.py` (không cần Azure, ~10 giây) để chặn PR làm hỏng bộ test.
4. Storage Account hiện chưa đặt `public_network_access_enabled = false` / network rules. Cân nhắc khi chấm tiêu chí "Blob không public"
   (hiện đã chặn anonymous access, nhưng endpoint vẫn mở ra Internet, chỉ truy cập được bằng RBAC/SAS).

### TV3 (Worker/Observability)
1. Alert trong `infra/monitoring.tf` hiện là `Http5xx` của API. Failure Drill cần alert trên **metric tenant-mismatch** của Worker.
2. Gửi TV4 ảnh Dashboard, email Alert, KQL truy vết `jobId`, p95 k6 để điền vào Design Report §4 và §6.

## Kế hoạch Đợt 2 (Tuần 6) của TV4
1. Lấy token mới cho 4 user (xem `tests/security/README.md`).
2. `.\tests\security\run.ps1 -Target azure -BaseUrl <api_https_url>`
3. `python tests/security/scan_secrets.py tests/security/evidence` → copy bản sạch vào `docs/tv4/evidence/<run>/` → PR.
4. Chụp UI: Guest bị chặn, User X bị 403 sau khi thu hồi, Export tải qua SAS.
5. Điền các chỗ `⟦ĐIỀN⟧` trong `DESIGN_REPORT.md`.
