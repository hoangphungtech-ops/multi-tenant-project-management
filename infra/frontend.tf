# Azure Static Web Apps (Gói Free $0/tháng, có sẵn SSL/HTTPS miễn phí cho React SPA của Thành viên 4)
# Lưu ý kỹ thuật Azure: Static Web Apps tại khu vực Châu Á chỉ hỗ trợ đặt control-plane tại "eastasia"
resource "azurerm_static_web_app" "frontend" {
  name                = "swa-${local.name_prefix}-${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.main.name
  location            = "eastasia"
  sku_tier            = "Free"
  sku_size            = "Free"

  tags = local.tags
}
