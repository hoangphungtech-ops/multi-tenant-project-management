resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-${local.name_prefix}-${random_string.suffix.result}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = local.tags
}

resource "azurerm_application_insights" "main" {
  name                = "appi-${local.name_prefix}-${random_string.suffix.result}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "java"

  tags = local.tags
}

resource "azurerm_monitor_action_group" "alerts" {
  name                = "ag-${local.name_prefix}"
  resource_group_name = azurerm_resource_group.main.name
  short_name          = "saasalert"

  email_receiver {
    name                    = "oncall-email"
    email_address           = var.alert_email
    use_common_alert_schema = true
  }

  tags = local.tags
}

# Alert mẫu theo dõi lỗi HTTP 5xx trên API (Thành viên 3 sẽ bổ sung thêm Alert Rule cho Forged Tenant Export)
resource "azurerm_monitor_metric_alert" "api_5xx" {
  name                = "alert-api-http-5xx"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [azurerm_linux_web_app.api.id]
  description         = "Cảnh báo khi API phát sinh lỗi HTTP 5xx"
  severity            = 2
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Web/sites"
    metric_name      = "Http5xx"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 5
  }

  action {
    action_group_id = azurerm_monitor_action_group.alerts.id
  }

  tags = local.tags
}
