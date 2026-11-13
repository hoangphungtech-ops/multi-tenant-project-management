resource "azurerm_key_vault" "main" {
  name                       = "kv-${substr(local.name_prefix, 0, 14)}-${random_string.suffix.result}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  purge_protection_enabled   = false
  soft_delete_retention_days = 7

  tags = local.tags
}

# Cấp quyền cho tài khoản đang chạy Terraform ghi secret vào Key Vault
resource "azurerm_role_assignment" "deployer_kv_officer" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# Lưu mật khẩu PostgreSQL vào Key Vault (App Service sẽ đọc qua Key Vault Reference + Managed Identity)
resource "azurerm_key_vault_secret" "db_password" {
  name         = "db-admin-password"
  value        = var.db_admin_password
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [
    azurerm_role_assignment.deployer_kv_officer
  ]
}
