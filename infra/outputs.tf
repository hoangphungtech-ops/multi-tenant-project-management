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

output "frontend_https_url" {
  description = "URL HTTPS miễn phí của Azure Static Web App (Frontend React)"
  value       = "https://${azurerm_static_web_app.frontend.default_host_name}"
}

output "postgres_fqdn" {
  description = "Domain nội bộ của PostgreSQL Flexible Server (chỉ truy cập được từ trong VNet)"
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "storage_account_name" {
  description = "Tên Storage Account chứa Blob Exports & Queue"
  value       = azurerm_storage_account.main.name
}

output "storage_blob_endpoint" {
  description = "Blob Endpoint URL (dùng với Managed Identity ở API & Worker)"
  value       = azurerm_storage_account.main.primary_blob_endpoint
}

output "storage_queue_endpoint" {
  description = "Queue Endpoint URL (dùng với Managed Identity ở API & Worker - khớp với code TV3)"
  value       = azurerm_storage_account.main.primary_queue_endpoint
}

output "exports_container_name" {
  description = "Tên Private Blob Container lưu file CSV export"
  value       = azurerm_storage_container.exports.name
}

output "export_queue_name" {
  description = "Tên Queue đẩy Export Job"
  value       = azurerm_storage_queue.export_jobs.name
}

output "export_poison_queue_name" {
  description = "Tên Poison Queue (DLQ) chứa các Export Job lỗi vượt quá số lần thử lại"
  value       = azurerm_storage_queue.export_jobs_poison.name
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
