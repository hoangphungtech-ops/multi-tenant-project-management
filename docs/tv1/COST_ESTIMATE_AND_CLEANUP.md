# Dự Toán Chi Phí Theo Workload & Hướng Dẫn Dọn Dẹp Tài Nguyên (TV1)

Tài liệu này đáp ứng mục **Cost and cleanup (10%)** trong đề bài `06_multi_tenant_project_management_saas.pdf`: nêu rõ Region, Service Tiers, Runtime Hours, Storage, Requests, Backups, Data Egress, cảnh báo ngân sách và quy trình dọn dẹp sạch tài nguyên tính phí.

---

## 1. Thông số Workload giả định cho dự toán (Classroom Reference Workload)

* **Tài khoản sử dụng:** Microsoft Azure (`Azure for Students` — $100.00 USD Credit miễn phí 12 tháng, không liên kết thẻ tín dụng).
* **Region chính:** `Southeast Asia` (Singapore) cho Compute, Database, Storage, Key Vault, Monitor; `East Asia` (Hong Kong) cho control-plane của `Static Web Apps` (Gói Free).
* **Quy mô dữ liệu & lưu lượng (Workload):**
  * 3 Organizations (Tenants), 5 Users/Org, 20 Tasks/Org (kèm kịch bản Load Test `k6` chạy ngắn 2.5–3 phút).
  * Tổng lượng HTTP API Requests ước tính: ~150,000 requests/tháng.
  * Tổng số lượng Background Export Jobs: ~1,000 jobs/tháng (mỗi file CSV ~10–50 KB).
  * Dung lượng Database: 32 GiB SSD + 7 ngày Automated Backup.
  * Dung lượng Object Storage (Blob) & Queue: < 1 GiB Standard LRS.
  * Dung lượng Log Analytics & Application Insights: < 1 GiB/tháng (nằm trong hạn mức 5 GiB/tháng miễn phí).
  * Băng thông ra Internet (Data Egress): < 5 GiB/tháng (100 GiB đầu tiên mỗi tháng trên Azure miễn phí).

---

## 2. Bảng dự toán chi phí hàng tháng (So sánh 2 chế độ vận hành)

| Dịch vụ Azure (Actual Service) | Region | Service Tier / SKU | Thông số Storage / Requests / Backup / Egress | Chi phí nếu bật 24/7 (730 giờ/tháng) | Chi phí thực tế của nhóm (On-Demand ~15 giờ test & demo) |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **Azure App Service Plan** (chạy chung cả `api` & `worker`) | Southeast Asia | `Basic B1` (Linux, 1 vCPU, 1.75 GB RAM) | 10 GB đi kèm, 150k HTTPS requests | ~$13.14 | **~$0.27** |
| **Azure Database for PostgreSQL Flexible Server** | Southeast Asia | Burstable `B_Standard_B1ms` (1 vCore, 2 GiB RAM) | **Storage:** 32 GiB SSD (~$3.68/tháng)<br>**Backup:** 7 ngày miễn phí (≤ 32 GiB) | ~$16.06 | **~$0.50** |
| **Azure Storage Account** (Blob `exports` + Queue `export-jobs`) | Southeast Asia | `Standard LRS` (Hot tier) | **Storage:** < 1 GB<br>**Requests:** ~10,000 PUT/GET/Queue ops | ~$0.05 | **~$0.01** |
| **Azure Key Vault** | Southeast Asia | `Standard` (RBAC) | 1 Secret (`db-admin-password`), < 1,000 operations | ~$0.03 | **~$0.01** |
| **Azure Static Web Apps** (Frontend React) | East Asia | `Free` Tier | 100 GB bandwidth miễn phí, SSL miễn phí | **$0.00** | **$0.00** |
| **Azure Virtual Network (VNet) & Private DNS Zone** | Southeast Asia | Standard VNet + 1 Private DNS Zone | 2 Subnets (`snet-app`, `snet-db`), 1 DNS Zone (~$0.50/tháng) | ~$0.50 | **~$0.02** |
| **Log Analytics, App Insights & Metric Alerts** | Southeast Asia | Pay-as-you-go (`PerGB2018`) | **Logs:** < 1 GB (Free 5 GB đầu)<br>**Alerts:** 2 Metric Alerts ($0.10/rule) | ~$0.20 | **~$0.01** |
| **Microsoft Entra External ID / OIDC** | Global | Free Tier (50,000 MAUs) | 15 tài khoản test + tokens | **$0.00** | **$0.00** |
| **Outbound Data Transfer (Egress)** | Southeast Asia | Standard Internet Egress | < 5 GB/tháng (Free 100 GB đầu/tháng) | **$0.00** | **$0.00** |
| **TỔNG CỘNG ƯỚC TÍNH** | | | | **~$29.98 / tháng** | **~$0.82 / đợt test** |

---

## 3. Lưu ý quan trọng về Quản lý Ngân sách & Giới hạn tải

1. **Budget Alerts không phải là khóa cắt tự động (Not Hard Spending Caps):**
   * Cảnh báo ngân sách trên Azure Cost Management (đã cài ở các mốc `$25`, `$50`, `$75`) **chỉ gửi email thông báo**, không tự động tắt App Service hay PostgreSQL.
2. **Giới hạn quy mô và thời lượng kiểm thử (Bound Scale & Test Duration):**
   * Không bật Auto-scaling tự do không giới hạn; giữ cố định `1 instance` gói `B1`.
   * Bài kiểm thử hiệu năng `k6` được giới hạn thời lượng chạy **2.5 – 3 phút** (30s warmup + 2m steady state) để đo chính xác `p95` mà không làm tốn băng thông hay quota.
3. **Loại bỏ tài nguyên nhàn rỗi (Remove Idle Compute & DB):**
   * Trong giai đoạn phát triển tính năng (85% thời gian), nhóm chạy bằng `Docker Compose` ở máy cá nhân ($0).
   * Chỉ chạy `terraform apply` trong 3 đợt ngắn (Test E2E Tuần 4, Thu thập bằng chứng Tuần 6, và Buổi bảo vệ cuối kỳ). Kết thúc mỗi đợt đều chạy `terraform destroy`.

---

## 4. Hướng dẫn Dọn dẹp Tài nguyên (Cleanup Instructions)

Sau khi kết thúc mỗi đợt kiểm thử hoặc sau buổi bảo vệ cuối kỳ, **Thành viên 1** thực hiện đúng 3 bước sau để xóa sạch toàn bộ tài nguyên tính phí:

### Bước 1: Hủy toàn bộ hạ tầng bằng Terraform
```powershell
cd infra
terraform destroy -auto-approve
```
*(Nhờ cấu hình `purge_soft_delete_on_destroy = true` cho Key Vault và quản lý chung trong 1 Resource Group, Terraform sẽ xóa toàn bộ App Service, PostgreSQL, Storage, VNet, DNS Zone, Key Vault và Log Analytics).*

### Bước 2: Xác minh không còn bất kỳ Resource Group hay tài nguyên tính phí nào sót lại
Chạy các lệnh kiểm tra sau trên PowerShell:

```powershell
# 1. Kiểm tra danh sách Resource Group (kết quả phải trống hoặc không còn rg-mtpm-saas-*)
az group list --query "[?starts_with(name, 'rg-mtpm-saas')].{Name:name, State:properties.provisioningState}" -o table

# 2. Kiểm tra toàn bộ tài nguyên còn tồn tại trong Subscription
az resource list --query "[].{Name:name, Type:type, Location:location}" -o table

# 3. Kiểm tra không còn PostgreSQL Flexible Server hay App Service Plan nào chạy ngầm
az postgres flexible-server list -o table
az appservice plan list -o table
```

### Bước 3: Kiểm tra Key Vault Soft-Delete (đảm bảo không giữ trạng thái ẩn)
```powershell
az keyvault list-deleted -o table
```
Nếu tất cả các lệnh ở Bước 2 và Bước 3 đều trả về danh sách trống (`[]`), tài khoản Azure của nhóm đã được dọn dẹp **100% không còn tài nguyên phát sinh chi phí**.
