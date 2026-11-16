# API Contract đề xuất cho Frontend & Security Tests (TV4 → TV2)

> **Trạng thái: ĐỀ XUẤT, chưa chốt.** TV2 sở hữu API. Tài liệu này ghi lại những gì Frontend
> (`frontend/`) và bộ test bảo mật (`tests/security/`) đang giả định. Khi TV2 chốt Swagger/OpenAPI,
> chỉ cần sửa 2 file:
> - `frontend/src/api/client.ts` (đường dẫn endpoint phía UI)
> - `tests/security/api_paths.py` (đường dẫn endpoint phía test)

## Nguyên tắc chung

| Quy ước | Giá trị đề xuất | Lý do |
|---|---|---|
| Base path | `/api/v1` | Có version để thay đổi không phá client |
| Tenant trong URL | `/api/v1/orgs/{tenantId}/...` | `TenantMembershipInterceptor` đọc `tenantId` từ path rồi kiểm tra bảng `memberships` **mỗi request** |
| Xác thực | `Authorization: Bearer <JWT Entra External ID>` | JWT chỉ chứng minh danh tính; **quyền theo tenant luôn đọc từ DB** (Test 2) |
| Request ID | Client có thể gửi `X-Request-Id`; API luôn trả lại `X-Request-Id` | Truy vết log theo `request_id` |
| ID | UUID | Không đoán được, nhưng **không** được coi là biện pháp bảo mật |

### Mã lỗi

| Mã | Khi nào |
|---|---|
| `401` | Thiếu/sai/hết hạn JWT |
| `403` | Có JWT hợp lệ nhưng **không phải thành viên** `tenantId` trong path, hoặc role không đủ (Guest ghi dữ liệu, Member quản lý thành viên) |
| `404` | Tài nguyên không tồn tại **trong tenant này** (kể cả khi nó tồn tại ở tenant khác — không tiết lộ sự tồn tại) |
| `409` | Trùng `UNIQUE(tenant_id, name)` của project |
| `422`/`400` | Dữ liệu đầu vào sai |

Body lỗi đề xuất: `{"error":"FORBIDDEN","message":"...","requestId":"..."}` — **không** trả stack trace, không trả dữ liệu tenant khác.

## Endpoint

### Danh tính
| Method | Path | Role | Response |
|---|---|---|---|
| GET | `/api/v1/me` | đã đăng nhập | `{userId, email, displayName, memberships:[{tenantId, tenantName, role}]}` |

`role` ∈ `OWNER` · `MEMBER` · `GUEST`.

### Thành viên (Member Management)
| Method | Path | Role | Body / Response |
|---|---|---|---|
| GET | `/orgs/{tenantId}/members` | mọi role | `[{userId, email, displayName, role, joinedAt}]` |
| POST | `/orgs/{tenantId}/members` | OWNER | `{email, role}` → `201 {userId, email, role}` (mời / thêm lại) |
| PATCH | `/orgs/{tenantId}/members/{userId}` | OWNER | `{role}` → `200` |
| DELETE | `/orgs/{tenantId}/members/{userId}` | OWNER | `204` — có hiệu lực **ngay** với request tiếp theo |

### Project
| Method | Path | Role | Ghi chú |
|---|---|---|---|
| GET | `/orgs/{tenantId}/projects` | mọi role | Cache key phía server: `tenant:{tenantId}:projects` |
| POST | `/orgs/{tenantId}/projects` | OWNER, MEMBER | `{name, description?}` → `201`; trùng tên trong cùng tenant → `409` |
| GET | `/orgs/{tenantId}/projects/{projectId}` | mọi role | |
| PATCH | `/orgs/{tenantId}/projects/{projectId}` | OWNER, MEMBER | |
| DELETE | `/orgs/{tenantId}/projects/{projectId}` | OWNER, MEMBER | `204` |

### Task
| Method | Path | Role | Ghi chú |
|---|---|---|---|
| GET | `/orgs/{tenantId}/projects/{projectId}/tasks` | mọi role | |
| POST | `/orgs/{tenantId}/projects/{projectId}/tasks` | OWNER, MEMBER | `{title, description?, status?, assigneeUserId?}` |
| GET | `/orgs/{tenantId}/tasks/{taskId}` | mọi role | |
| PATCH | `/orgs/{tenantId}/tasks/{taskId}` | OWNER, MEMBER | `{title?, description?, status?, assigneeUserId?}` |
| DELETE | `/orgs/{tenantId}/tasks/{taskId}` | OWNER, MEMBER | `204` |

`status` ∈ `TODO` · `IN_PROGRESS` · `DONE`.

Task object: `{id, tenantId, projectId, title, description, status, assigneeUserId, createdAt, updatedAt}`.

### Audit
| Method | Path | Role | Response |
|---|---|---|---|
| GET | `/orgs/{tenantId}/audit-events?limit=50` | mọi role | `[{id, actorUserId, action, entityType, entityId, requestId, createdAt, details}]` (mới nhất trước) |

### Export
| Method | Path | Role | Response |
|---|---|---|---|
| POST | `/orgs/{tenantId}/exports` | OWNER, MEMBER | `{projectId?}` → `202 {jobId, status:"QUEUED"}` |
| GET | `/orgs/{tenantId}/exports` | mọi role | `[{jobId, status, requestedBy, createdAt, completedAt, errorCode}]` |
| GET | `/orgs/{tenantId}/exports/{jobId}` | mọi role | 1 job |
| GET | `/orgs/{tenantId}/exports/{jobId}/download` | OWNER, MEMBER | `{url, expiresAt}` — SAS **read-only, ngắn hạn (≤ 5 phút)**, chỉ khi `COMPLETED` (ngược lại `409`) |

`status` ∈ `QUEUED` · `PROCESSING` · `COMPLETED` · `FAILED` · `REJECTED`.

Frontend **không bao giờ** nhận storage account key; không lưu SAS URL vào localStorage và không log SAS URL.

## Điểm cần TV2 xác nhận

1. Tenant trong path (`/orgs/{tenantId}/...`) hay header (`X-Tenant-Id`)? Bộ test đang hỗ trợ path; header chỉ cần sửa `api_paths.py`.
2. Truy cập tài nguyên tenant khác qua đường dẫn tenant của mình → `404` (đề xuất) hay `403`? Test 1 chấp nhận cả hai, miễn **không phải 2xx** và body không chứa dữ liệu của Org B.
3. Guest có được gọi `GET .../download` không? Đề xuất: không (Guest chỉ xem).
4. Mời thành viên: tạo user ngay hay tạo lời mời chờ chấp nhận? Test 2 cần một cách **thêm lại** User X để chạy lặp lại được.
5. Tên CSV columns & tên header request ID.
