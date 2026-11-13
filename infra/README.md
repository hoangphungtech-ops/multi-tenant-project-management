# Hạ tầng Azure (Terraform IaC) — Thành viên 1

Thư mục này quản lý toàn bộ hạ tầng Microsoft Azure cho hệ thống **Multi-Tenant Project Management SaaS** bằng Terraform (`azurerm` ~> 4.0).

## Cấu trúc file
* `providers.tf`: Cấu hình Terraform & AzureRM provider.
* `variables.tf`: Các biến cấu hình đầu vào.
* `network.tf`: Resource Group, VNet (`10.0.0.0/16`), Subnets (`snet-app`, `snet-db`), Private DNS Zone cho PostgreSQL.
* `postgresql.tf`: Azure Database for PostgreSQL Flexible Server (`B_Standard_B1ms`, Private Subnet).
* `storage_queue.tf`: Azure Storage Account (Private Blob `exports`, `export-jobs` Queue & Poison Queue).
* `keyvault.tf`: Azure Key Vault (RBAC) lưu mật khẩu DB.
* `app_service.tf`: App Service Plan (`B1`), 2 Web Apps (`api` & `worker`) dùng Java 21, HTTPS Only, VNet Integration và Managed Identity RBAC.
* `monitoring.tf`: Log Analytics Workspace, Application Insights, Action Group (Email Alert) & Alert Rule.
* `outputs.tf`: Xuất các URL/Endpoint bàn giao cho Thành viên 2, 3, 4.

## Hướng dẫn chạy kiểm tra (Chưa tốn tiền)
1. Sao chép file biến mẫu:
   ```powershell
   cd infra
   Copy-Item terraform.tfvars.example terraform.tfvars
   ```
2. Mở `terraform.tfvars` và đổi `db_admin_password` thành mật khẩu mạnh của riêng bạn.
3. Khởi tạo Terraform và kiểm tra cú pháp:
   ```powershell
   terraform init
   terraform validate
   terraform plan
   ```
   *(Lệnh `terraform plan` chỉ chạy mô phỏng để xem trước danh sách tài nguyên sẽ tạo — chưa tốn $0 nào!)*

## Quy tắc Zero-Burn khi triển khai thật
* **Khi cần dựng để test/demo:** `terraform apply`
* **Ngay sau khi test/demo xong:** `terraform destroy` (xóa toàn bộ để tiết kiệm tối đa $100 credit).
