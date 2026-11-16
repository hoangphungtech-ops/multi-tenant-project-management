# Frontend — React + Vite + TypeScript (TV4)

Giao diện gọn nhẹ cho Multi-Tenant Project Management SaaS. Host trên **Azure Static Web Apps**.

| Chức năng | Trang | Ghi chú bảo mật |
|---|---|---|
| Đăng nhập Entra External ID (OIDC + PKCE) | `/` | MSAL, token ở `sessionStorage`, app không tự lưu/log token |
| Chuyển Org | thanh trên cùng | Danh sách org lấy từ `GET /me` (bảng memberships), không từ JWT |
| Dự án | `/projects` | Tên duy nhất trong tổ chức (`409` khi trùng) |
| Task Board | `/projects/:id` | 3 cột TODO / IN_PROGRESS / DONE, giao việc, cập nhật lạc quan rồi rollback nếu API trả lỗi |
| Quản lý thành viên | `/members` | Mời / đổi vai trò / thu hồi — chỉ Owner |
| Audit History | `/audit` | Hiển thị `request_id` để truy vết sang App Insights |
| Export CSV | `/exports` | Tạo job → poll trạng thái → tải qua SAS ngắn hạn do API cấp |

UI ẩn nút ghi với Guest **chỉ để dễ dùng**. Quyền thật luôn do API kiểm tra; bằng chứng nằm ở `tests/security/`.
Mọi lỗi hiển thị kèm `request_id` để tra log.

## Chạy local với mock API (không cần backend, $0)

```powershell
# Terminal 1 — mock API (Python stdlib, không cần cài gì)
python tests/mock_api/server.py

# Terminal 2
cd frontend
npm ci
npm run dev:mock        # http://localhost:5173 → chọn user mẫu để đăng nhập
```

User mẫu: `owner/member1/member2/member3/guest` × Org A/B/C. `Member1 (A)` thuộc 2 org (MEMBER ở A, GUEST ở B) để demo Chuyển Org.

## Chạy với backend thật (local Docker của TV2 hoặc Azure)

```powershell
cd frontend
Copy-Item .env.example .env.local   # .env.local đã được .gitignore
# sửa VITE_API_BASE_URL, VITE_ENTRA_CLIENT_ID, VITE_ENTRA_AUTHORITY, VITE_API_SCOPE
npm run dev
```

### Cấu hình Entra External ID (một lần)
1. Trong tenant External ID: **App registrations → New** `mtpm-spa`, platform **Single-page application**,
   Redirect URI: `http://localhost:5173` và `https://<static-web-app>.azurestaticapps.net`.
2. App registration của API (`mtpm-api`, TV2): **Expose an API** → scope `access_as_user`.
3. Cấp quyền cho `mtpm-spa` → `mtpm-api/access_as_user` (Grant admin consent).
4. Tạo **User flow** (Sign up & sign in) và gắn app `mtpm-spa`.
5. `VITE_ENTRA_AUTHORITY=https://<subdomain>.ciamlogin.com/`.

Client ID và scope **không phải secret** (SPA là public client, dùng PKCE, không có client secret).

## Build & deploy

```powershell
npm run build    # tsc type-check + vite build → dist/
```

`public/staticwebapp.config.json` được copy vào `dist/`: SPA fallback và security headers (CSP, `X-Frame-Options: DENY`,
`nosniff`, HSTS, `Referrer-Policy: no-referrer` để SAS URL không lọt qua Referer).

Deploy (TV1 gắn vào GitHub Actions): `Azure/static-web-apps-deploy@v1` với `app_location: frontend`,
`output_location: dist`, và các biến `VITE_*` truyền vào lúc build.

**Backend cần bật CORS** cho origin của Static Web App (Spring `CorsConfiguration` hoặc App Service CORS), gồm header
`Authorization, Content-Type, X-Request-Id` và expose `X-Request-Id`.

## Khi TV2 chốt OpenAPI
Chỉ cần sửa `src/api/client.ts` (đường dẫn) và `src/api/types.ts` (kiểu dữ liệu). Xem `docs/tv4/API_CONTRACT_PROPOSAL.ipynb`.
