output "resource_group_name" {
  description = "Tên Resource Group chứa toàn bộ hạ tầng"
  value       = azurerm_resource_group.main.name
}

output "api_https_url" {
  description = "URL HTTPS mặc định của Backend API (giao cho Thành viên 4 cấu hình Frontend & QA)"
  value       = "https://${azurerm_linux_web_app.api.default_hostname}"
}

output "worker_https_url" {
  description = "URL của Export Worker App Service"
  value       = "https://${azurerm_linux_web_app.worker.default_hostname}"
}

output "postgres_fqdn" {
  description = "Domain nội bộ của PostgreSQL Flexible Server (chỉ truy cập được từ trong VNet)"
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "storage_account_name" {
  description = "Tên Storage Account chứa Blob Exports & Queue"
  value       = azurerm_storage_account.main.name
}

output "exports_container_name" {
  description = "Tên Private Blob Container lưu file CSV export"
  value       = azurerm_storage_container.exports.name
}

output "export_queue_name" {
  description = "Tên Queue đẩy Export Job"
  value       = azurerm_storage_queue.export_jobs.name
}

output "key_vault_uri" {
  description = "URI của Azure Key Vault"
  value       = azurerm_key_vault.main.vault_uri
}

output "app_insights_connection_string" {
  description = "Connection String của Application Insights (giao cho Thành viên 3 cấu hình Telemetry)"
  value       = azurerm_application_insights.main.connection_string
  sensitive   = true
}
