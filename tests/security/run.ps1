# Chạy Security Tests 1–3.
#   .\tests\security\run.ps1                     # với mock API local (tự bật/tắt)
#   .\tests\security\run.ps1 -Target azure -BaseUrl https://app-...-api-xxxx.azurewebsites.net
#     (token đặt trong biến môi trường TOKEN_* hoặc tests\security\tokens.local.json)
param(
    [ValidateSet("mock", "local", "azure")] [string] $Target = "mock",
    [string] $BaseUrl = "http://127.0.0.1:8080"
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:API_TARGET = $Target
$env:API_BASE_URL = $BaseUrl
$env:PYTHONIOENCODING = "utf-8"
$mock = $null
if ($Target -eq "mock") {
    $mock = Start-Process python -ArgumentList "`"$here\..\mock_api\server.py`"", "--port", "8080" -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 1
}
try {
    Push-Location $here
    python -m pytest
    $code = $LASTEXITCODE
    python scan_secrets.py evidence
} finally {
    Pop-Location
    if ($mock) { Stop-Process -Id $mock.Id -Force }
}
exit $code
