variable "subscription_id" {
  description = "Azure Subscription ID (lấy từ lệnh az account show)"
  type        = string
}

variable "project_name" {
  description = "Tên dự án ngắn gọn, chữ thường không dấu (dùng làm tiền tố đặt tên tài nguyên)"
  type        = string
  default     = "mtpm-saas"
}

variable "environment" {
  description = "Môi trường triển khai (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "location" {
  description = "Azure Region (đã kiểm tra hỗ trợ B1ms cho Azure for Students)"
  type        = string
  default     = "southeastasia"
}

variable "db_admin_username" {
  description = "Tên tài khoản admin PostgreSQL"
  type        = string
  default     = "saasadmin"
}

variable "db_admin_password" {
  description = "Mật khẩu admin PostgreSQL (tối thiểu 12 ký tự, có chữ hoa, thường, số)"
  type        = string
  sensitive   = true
}

variable "alert_email" {
  description = "Email nhận cảnh báo sự cố từ Azure Monitor"
  type        = string
}

variable "oidc_issuer_uri" {
  description = "OIDC Issuer URI từ Microsoft Entra External ID (cập nhật sau khi tạo tenant CIAM)"
  type        = string
  default     = ""
}
