$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
New-Item -ItemType Directory -Force -Path build-tv3 | Out-Null
$files = @(
  "src/main/java/com/cloudsaas/tv3/ExportWorker.java",
  "src/main/java/com/cloudsaas/tv3/CsvBuilder.java",
  "src/test/java/com/cloudsaas/tv3/ExportWorkerSelfTest.java"
)
& javac -encoding UTF-8 -d build-tv3 $files
if ($LASTEXITCODE -ne 0) { throw "Java compile failed" }
& java -cp build-tv3 com.cloudsaas.tv3.ExportWorkerSelfTest
if ($LASTEXITCODE -ne 0) { throw "Java test failed" }
