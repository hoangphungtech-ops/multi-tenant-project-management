# Automated API Security Tests — Test 1, 2, 3 (TV4)

Bộ test gọi **thẳng vào API** (không qua UI) bằng `pytest` + `requests`. Mỗi request đều được ghi lại thành bằng chứng
(input, timestamp gửi/nhận, raw HTTP request/response) và **tự che** JWT, SAS signature, mật khẩu trước khi ghi ra đĩa.

| File | Kịch bản | Kỳ vọng |
|---|---|---|
| `test_1_cross_tenant_idor.py` | JWT Org A đọc/sửa/xóa/export Project, Task, Export job, Members, Audit của Org B — cả kiểu đổi `tenantId` trong path lẫn kiểu giữ tenant A và đổi ID tài nguyên. Thêm: không token, JWT giả `alg=none`, Guest ghi dữ liệu, **giả danh bằng header `X-Demo-User`/`X-Demo-Tenant`** (có hoặc không kèm JWT) | 100% là 403/404 (401 khi không có token, token giả, hoặc chỉ có header demo), body không chứa dữ liệu Org B, dữ liệu Org B không đổi (kiểm lại bằng token của B) |
| `test_2_membership_revocation.py` | Owner xóa User X khỏi Org A → ngay lập tức dùng lại **đúng JWT cũ** của X | Request kế tiếp trả 403; `/me` vẫn 200 (chứng minh chặn là do DB, không phải do token hết hạn); có Audit `MEMBER_REMOVED`; hạ role xuống GUEST cũng có hiệu lực ngay |
| `test_3_duplicate_name_cache.py` | Org A và Org B cùng tạo `"Cloud Project"`, đọc danh sách 2 lần (cache ấm), cập nhật/xóa ở A | Cả hai 201, khác ID; trùng trong cùng tenant → 409; danh sách mỗi org không lẫn org kia trước và sau khi invalidate |
| `test_0_evidence_redaction.py` | Unit test cho bộ che dữ liệu mật | JWT/SAS/password/AccountKey không xuất hiện trong bằng chứng |

## Cài đặt

```powershell
python -m pip install -r tests/security/requirements.txt
```

## Chạy

### 1. Với mock API (kiểm tra chính bộ test, $0)
```powershell
.\tests\security\run.ps1                 # Windows
bash tests/security/run.sh               # macOS/Linux/Git Bash
python tests/security/selfcheck.py       # test-the-tests: mock an toàn phải PASS, mock có lỗ hổng phải FAIL
```
Kết quả selfcheck hiện tại:
```
[OK ] secure mock        -> tests must PASS : 36 passed
[OK ] --vuln idor             -> test_1 must FAIL : 15 failed, 14 passed
[OK ] --vuln stale-membership -> test_2 must FAIL : 1 failed, 1 passed
[OK ] --vuln global-unique    -> test_3 must FAIL : 1 failed
[OK ] --vuln shared-cache     -> test_3 must FAIL : 1 failed
[OK ] --vuln demo-header      -> test_1 must FAIL : 2 failed
```

> Mock API **không phải** backend thật. PASS trên mock chỉ chứng minh logic test đúng.
> Bằng chứng nộp báo cáo phải chạy trên API thật trên Azure (Đợt 2, Tuần 6).

### 2. Với API thật (Docker local của TV2 hoặc Azure)

Cần 4 JWT thật (từ seed data, user thật trong Entra External ID):

| Biến | User |
|---|---|
| `TOKEN_A_OWNER` | Owner của Org A |
| `TOKEN_A_GUEST` | Guest của Org A |
| `TOKEN_X` | Một MEMBER của Org A (sẽ bị thu hồi rồi khôi phục) |
| `TOKEN_B_OWNER` | Owner của Org B |

Cách lấy token: đăng nhập web app bằng từng user → DevTools → Application → Session Storage → khóa
`...accesstoken...` → trường `secret`. Lưu vào `tests/security/tokens.local.json` (**đã .gitignore**):

```json
{ "TOKEN_A_OWNER": "eyJ...", "TOKEN_A_GUEST": "eyJ...", "TOKEN_X": "eyJ...", "TOKEN_B_OWNER": "eyJ..." }
```

```powershell
.\tests\security\run.ps1 -Target azure -BaseUrl https://app-mtpm-saas-dev-api-xxxx.azurewebsites.net
```

Access token của Entra có hạn khoảng 60–90 phút, nên hãy lấy token ngay trước khi chạy.
Test 2 cần JWT của X **còn hạn**: điều đó chứng minh API chặn dựa trên bảng memberships, không phải nhờ token hết hạn.

### Lưu ý về chế độ demo của backend
Backend TV2 có chế độ demo (`APP_SECURITY_DEMO_MODE`, mặc định `true`) nhận danh tính từ header `X-Demo-User`.
Hai test `test_demo_headers_*` coi đây là **lỗ hổng** trên môi trường được kiểm thử (đặc biệt là Azure).
Chỉ khi cố ý chạy với backend local đang bật demo mode mới đặt `ALLOW_DEMO_AUTH=1` để bỏ qua hai test này.

## Bằng chứng (Evidence)

Mỗi lần chạy tạo `tests/security/evidence/<UTC-timestamp>-<target>/` (đã .gitignore):

| File | Nội dung |
|---|---|
| `run-info.json` | Target, base URL, thời điểm bắt đầu |
| `<test>.http.txt` | Raw HTTP dễ đọc (dán vào báo cáo): request, header, body, status, timestamp gửi/nhận, thời gian phản hồi |
| `<test>.jsonl` | Cùng dữ liệu, dạng máy đọc được |
| `summary.json`, `SUMMARY.md` | Bảng PASS/FAIL |

Mỗi request có `X-Request-Id: tv4-<user>-<ms>-<n>`. Dùng giá trị này để tìm log tương ứng trong Application Insights:
```kusto
union traces, requests | where customDimensions.request_id == "tv4-A-owner-..." or operation_Id == "..."
```

**Trước khi đính kèm vào báo cáo:**
```powershell
python tests/security/scan_secrets.py tests/security/evidence     # exit 1 nếu phát hiện JWT/SAS/key
```
Sau đó copy bản đã rà soát vào `docs/tv4/evidence/` rồi mới commit. Ảnh chụp màn hình vẫn phải tự kiểm tra bằng mắt.

## Khi TV2 chốt OpenAPI
Chỉ cần sửa `api_paths.py`. Nếu tenant đi qua header thay vì path, thêm header trong `evidence.ApiClient.request`.
