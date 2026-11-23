# Bằng Chứng Triển Khai Hạ Tầng Thực Tế Trên Microsoft Azure (TV1)

**Ngày thực hiện:** 23/11/2026  
**Subscription:** `Azure for Students` (`12c98aeb-3bb3-4fcf-a8ea-516f7f2c888f`)  
**Region:** `East Asia` (Hong Kong — nằm trong danh sách *Allowed resource deployment regions* của Microsoft Azure Policy)  
**Resource Group:** `rg-mtpm-saas-dev-pco47` (32/32 Terraform resources deployed)

---

## 1. Thông số Outputs thực tế từ `terraform apply`

```text
api_https_url             = "https://app-mtpm-saas-dev-api-pco47.azurewebsites.net"
worker_https_url          = "https://app-mtpm-saas-dev-worker-pco47.azurewebsites.net"
frontend_https_url        = "https://mango-beach-01fa8d100.3.azurestaticapps.net"
postgres_fqdn             = "psql-mtpm-saas-dev-pco47.postgres.database.azure.com"
key_vault_uri             = "https://kv-mtpm-saas-dev-pco47.vault.azure.net/"
resource_group_name       = "rg-mtpm-saas-dev-pco47"
storage_account_name      = "stmtpmsaasdevpco47"
storage_blob_endpoint     = "https://stmtpmsaasdevpco47.blob.core.windows.net/"
storage_queue_endpoint    = "https://stmtpmsaasdevpco47.queue.core.windows.net/"
exports_container_name    = "exports"
export_queue_name         = "export-jobs"
export_poison_queue_name  = "export-jobs-poison"
```

---

## 2. Danh mục Ảnh chụp Bằng chứng trên Azure Portal

1. **[`01_terraform_apply_outputs.jpg`](01_terraform_apply_outputs.jpg):** Kết quả `terraform apply` hoàn tất 32/32 tài nguyên và xuất toàn bộ Outputs thực tế.
2. **[`02_postgresql_flexible_server_overview.jpg`](02_postgresql_flexible_server_overview.jpg):** Máy chủ `psql-mtpm-saas-dev-pco47` (PostgreSQL 16.15, gói Burstable `B1ms`, 1 vCore, 2 GiB RAM, 32 GiB Storage, trạng thái `Ready`).
3. **[`03_postgresql_private_vnet_networking.jpg`](03_postgresql_private_vnet_networking.jpg):** Bằng chứng bảo mật mạng Database — bắt buộc TLS/SSL, chế độ **`Private access (VNet Integration)`** (tắt Public IP), đặt trong `vnet-mtpm-saas-dev` / `snet-db` và phân giải qua Private DNS Zone `mtpm-saas-dev-pco47.private.postgres.database.azure.com`.
4. **[`04_alert_worker_forged_tenant_mismatch.png`](04_alert_worker_forged_tenant_mismatch.png):** Bằng chứng Actionable Alert `alert-worker-forged-tenant-mismatch` (`Severity 1 - Error`, theo dõi metric `worker_tenant_mismatch_total` trên `appi-mtpm-saas-dev-pco47`, kích hoạt Action Group `ag-mtpm-saas-dev` gửi Email cảnh báo).
5. **[`05_resource_group_overview.jpg`](05_resource_group_overview.jpg):** Tổng quan Resource Group `rg-mtpm-saas-dev-pco47` hiển thị đầy đủ 15 tài nguyên cấp cao nhất được gắn tag `ManagedBy: Terraform`.
