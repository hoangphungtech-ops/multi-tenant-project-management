resource "azurerm_service_plan" "main" {
  name                = "asp-${local.name_prefix}-${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  os_type             = "Linux"
  sku_name            = "B1"

  tags = local.tags
}

# 1. App Service cho API chính (SPRING_PROFILES_ACTIVE=api)
resource "azurerm_linux_web_app" "api" {
  name                      = "app-${local.name_prefix}-api-${random_string.suffix.result}"
  resource_group_name       = azurerm_resource_group.main.name
  location                  = azurerm_resource_group.main.location
  service_plan_id           = azurerm_service_plan.main.id
  https_only                = true
  virtual_network_subnet_id = azurerm_subnet.app.id

  identity {
    type = "SystemAssigned"
  }

  site_config {
    always_on = true
    application_stack {
      java_server         = "JAVA"
      java_server_version = "21"
      java_version        = "21"
    }
  }

  app_settings = {
    "SPRING_PROFILES_ACTIVE"                = "api"
    "SPRING_DATASOURCE_URL"                 = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/saas_db?sslmode=require"
    "SPRING_DATASOURCE_USERNAME"            = var.db_admin_username
    "SPRING_DATASOURCE_PASSWORD"            = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.db_password.versionless_id})"
    "AZURE_STORAGE_ACCOUNT_NAME"            = azurerm_storage_account.main.name
    "AZURE_STORAGE_CONTAINER_EXPORTS"       = azurerm_storage_container.exports.name
    "AZURE_STORAGE_QUEUE_EXPORTS"           = azurerm_storage_queue.export_jobs.name
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = azurerm_application_insights.main.connection_string
    "OIDC_ISSUER_URI"                       = var.oidc_issuer_uri
  }

  tags = local.tags
}

# 2. App Service cho Async Export Worker (SPRING_PROFILES_ACTIVE=worker)
# Chạy chung App Service Plan B1 để không phát sinh thêm chi phí cố định
resource "azurerm_linux_web_app" "worker" {
  name                      = "app-${local.name_prefix}-worker-${random_string.suffix.result}"
  resource_group_name       = azurerm_resource_group.main.name
  location                  = azurerm_resource_group.main.location
  service_plan_id           = azurerm_service_plan.main.id
  https_only                = true
  virtual_network_subnet_id = azurerm_subnet.app.id

  identity {
    type = "SystemAssigned"
  }

  site_config {
    always_on = true
    application_stack {
      java_server         = "JAVA"
      java_server_version = "21"
      java_version        = "21"
    }
  }

  app_settings = {
    "SPRING_PROFILES_ACTIVE"                = "worker"
    "SPRING_DATASOURCE_URL"                 = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/saas_db?sslmode=require"
    "SPRING_DATASOURCE_USERNAME"            = var.db_admin_username
    "SPRING_DATASOURCE_PASSWORD"            = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.db_password.versionless_id})"
    "AZURE_STORAGE_ACCOUNT_NAME"            = azurerm_storage_account.main.name
    "AZURE_STORAGE_CONTAINER_EXPORTS"       = azurerm_storage_container.exports.name
    "AZURE_STORAGE_QUEUE_EXPORTS"           = azurerm_storage_queue.export_jobs.name
    "AZURE_STORAGE_QUEUE_POISON"            = azurerm_storage_queue.export_jobs_poison.name
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = azurerm_application_insights.main.connection_string
  }

  tags = local.tags
}

# --- RBAC Workload Identities (Không dùng Access Key cứng) ---

resource "azurerm_role_assignment" "api_kv_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_linux_web_app.api.identity[0].principal_id
}

resource "azurerm_role_assignment" "worker_kv_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_linux_web_app.worker.identity[0].principal_id
}

resource "azurerm_role_assignment" "api_storage_blob" {
  scope                = azurerm_storage_account.main.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_linux_web_app.api.identity[0].principal_id
}

resource "azurerm_role_assignment" "api_storage_blob_delegator" {
  scope                = azurerm_storage_account.main.id
  role_definition_name = "Storage Blob Delegator"
  principal_id         = azurerm_linux_web_app.api.identity[0].principal_id
}

resource "azurerm_role_assignment" "api_storage_queue" {
  scope                = azurerm_storage_account.main.id
  role_definition_name = "Storage Queue Data Contributor"
  principal_id         = azurerm_linux_web_app.api.identity[0].principal_id
}

resource "azurerm_role_assignment" "worker_storage_blob" {
  scope                = azurerm_storage_account.main.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_linux_web_app.worker.identity[0].principal_id
}

resource "azurerm_role_assignment" "worker_storage_queue" {
  scope                = azurerm_storage_account.main.id
  role_definition_name = "Storage Queue Data Contributor"
  principal_id         = azurerm_linux_web_app.worker.identity[0].principal_id
}
