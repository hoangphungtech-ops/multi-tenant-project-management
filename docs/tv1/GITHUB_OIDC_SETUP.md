# Hướng Dẫn Kết Nối GitHub Actions Với Azure Qua OIDC (Không Dùng Secret Tĩnh)

Tài liệu này hướng dẫn **Thành viên 1** cấu hình xác thực **OpenID Connect (OIDC Federated Credentials)** giữa GitHub Repository `hoangphungtech-ops/multi-tenant-project-management` và tài khoản Azure `Azure for Students` (`12c98aeb-3bb3-4fcf-a8ea-516f7f2c888f`).

> **Lợi ích bảo mật:** GitHub Actions đăng nhập vào Azure bằng token ngắn hạn tự động, **không cần tạo hay lưu mật khẩu (`client_secret`) dài hạn** trên GitHub.

---

## Bước 1: Chạy các lệnh tạo App Registration & Federated Credential trên PowerShell

Khi chuẩn bị chạy đợt Deploy thật lên Azure, bạn mở PowerShell (đã `az login`) và chạy lần lượt các lệnh sau:

```powershell
# 1. Khai báo thông tin cố định của dự án
$SUBSCRIPTION_ID = "12c98aeb-3bb3-4fcf-a8ea-516f7f2c888f"
$GITHUB_ORG      = "hoangphungtech-ops"
$GITHUB_REPO     = "multi-tenant-project-management"
$APP_NAME        = "gh-oidc-mtpm-saas"

# 2. Tạo Microsoft Entra App Registration và Service Principal
$APP_ID = $(az ad app create --display-name $APP_NAME --query appId -o tsv)
$SP_OBJECT_ID = $(az ad sp create --id $APP_ID --query id -o tsv)

# 3. Cấp quyền Contributor trên Subscription cho Service Principal để deploy App Service
az role assignment create `
  --role contributor `
  --subscription $SUBSCRIPTION_ID `
  --assignee-object-id $SP_OBJECT_ID `
  --assignee-principal-type ServicePrincipal `
  --scope "/subscriptions/$SUBSCRIPTION_ID"

# 4. Tạo OIDC Federated Credential cho nhánh main
$FED_JSON = @"
{
  "name": "github-main-branch",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main",
  "description": "Allow GitHub Actions on main branch to deploy to Azure",
  "audiences": ["api://AzureADTokenExchange"]
}
"@
$FED_JSON | Out-File -FilePath fed-credential.json -Encoding utf8
az ad app federated-credential create --id $APP_ID --parameters fed-credential.json
Remove-Item fed-credential.json

# 5. In ra 3 giá trị cần điền vào GitHub Secrets
$TENANT_ID = $(az account show --query tenantId -o tsv)
Write-Host "========================================="
Write-Host "AZURE_CLIENT_ID       = $APP_ID"
Write-Host "AZURE_TENANT_ID       = $TENANT_ID"
Write-Host "AZURE_SUBSCRIPTION_ID = $SUBSCRIPTION_ID"
Write-Host "========================================="
```

---

## Bước 2: Khai báo trên GitHub Repository (`Settings → Secrets and variables → Actions`)

1. **Tab `Secrets` → `New repository secret`:** Thêm 3 secret vừa in ra ở Bước 1:
   * `AZURE_CLIENT_ID`
   * `AZURE_TENANT_ID`
   * `AZURE_SUBSCRIPTION_ID`
2. **Tab `Variables` → `New repository variable`:** Sau khi chạy `terraform apply`, lấy tên 2 Web Apps từ `terraform output` điền vào:
   * `AZURE_API_APP_NAME` (ví dụ: `app-mtpm-saas-dev-api-xxxxx`)
   * `AZURE_WORKER_APP_NAME` (ví dụ: `app-mtpm-saas-dev-worker-xxxxx`)
