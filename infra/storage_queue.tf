locals {
  storage_clean_name = substr(lower(replace("${var.project_name}${var.environment}", "-", "")), 0, 17)
}

resource "azurerm_storage_account" "main" {
  name                            = "st${local.storage_clean_name}${random_string.suffix.result}"
  resource_group_name             = azurerm_resource_group.main.name
  location                        = azurerm_resource_group.main.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false

  tags = local.tags
}

# Container Private để lưu file CSV export theo từng tenant: exports/{tenant_id}/{job_id}.csv
resource "azurerm_storage_container" "exports" {
  name                  = "exports"
  storage_account_id    = azurerm_storage_account.main.id
  container_access_type = "private"
}

# Hàng đợi chính nhận job Export bất đồng bộ
resource "azurerm_storage_queue" "export_jobs" {
  name                 = "export-jobs"
  storage_account_name = azurerm_storage_account.main.name
}

# Hàng đợi lỗi (Poison Queue / DLQ) cho các job thất bại hoặc bị từ chối
resource "azurerm_storage_queue" "export_jobs_poison" {
  name                 = "export-jobs-poison"
  storage_account_name = azurerm_storage_account.main.name
}
