param(
    [ValidateSet('Start','Health','Stop','Verify')] [string]$Action = 'Start',
    [string]$MigrationDirectory,
    [string]$Maven = 'mvn',
    [switch]$RequireWorker
)
$ErrorActionPreference = 'Stop'
$module = Split-Path $PSScriptRoot -Parent
$compose = @('compose', '-p', 'phung-tv3-demo', '-f', (Join-Path $module 'compose.yaml'))
if (-not $env:TV3_DEMO_DB_PASSWORD) { throw 'Set TV3_DEMO_DB_PASSWORD for this local demo.' }

function Invoke-DemoDocker {
    & docker @compose @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose exited with code $LASTEXITCODE." }
}

Push-Location -LiteralPath $module
try {
    if ($Action -eq 'Stop') { Invoke-DemoDocker stop; return }
    if ($Action -eq 'Health') {
        Invoke-DemoDocker ps
        Invoke-DemoDocker exec -T postgres pg_isready -U tv3demo -d tv3demo
        if ($RequireWorker) {
            $worker = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
                Where-Object {
                    $_.CommandLine -match 'tv3-worker-1\.0\.0-SNAPSHOT\.jar' -and
                    $_.CommandLine -match 'worker,azurite,workspace-demo'
                }
            if (-not $worker) { throw 'Workspace demo Worker process was not found.' }
            Write-Output 'Workspace demo Worker process is running.'
        }
        foreach ($port in 10000, 10001) {
            $client = [Net.Sockets.TcpClient]::new()
            try { $client.Connect('127.0.0.1', $port) } finally { $client.Dispose() }
        }
        return
    }
    if (-not $MigrationDirectory) { throw 'Pass -MigrationDirectory with the extracted TV2 Flyway migrations.' }
    $schema = Get-Content -LiteralPath (Join-Path $MigrationDirectory 'V1__create_tables.sql') -Raw
    $rls = Get-Content -LiteralPath (Join-Path $MigrationDirectory 'V2__enable_rls.sql') -Raw
    Invoke-DemoDocker up -d --wait
    $bootstrap = @"
SELECT pg_advisory_xact_lock(32403);
SELECT to_regclass('public.export_jobs') IS NULL AS needs_schema \gset
\if :needs_schema
$schema
$rls
\endif
"@
    $bootstrap | docker @compose exec -T postgres psql -X -U tv3demo -d tv3demo -v ON_ERROR_STOP=1 -1 -f -
    if ($LASTEXITCODE -ne 0) { throw 'TV2 schema initialization failed.' }

    $previousStorage = $env:AZURE_STORAGE_CONNECTION_STRING
    $configuredStorage = $true
    $env:AZURE_STORAGE_CONNECTION_STRING = 'UseDevelopmentStorage=true'
    if ($Action -eq 'Verify') {
        & $Maven -B -ntp '-Dtv3.demo.it=true' clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Worker integration verification failed.' }
        return
    }
    & $Maven -B -ntp '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Worker package failed.' }
    & java -Xms64m -Xmx256m -XX:MaxDirectMemorySize=128m -jar target/tv3-worker-1.0.0-SNAPSHOT.jar '--spring.profiles.active=worker,azurite,workspace-demo'
    if ($LASTEXITCODE -ne 0) { throw 'Worker exited with an error.' }
} finally {
    if ($configuredStorage) { $env:AZURE_STORAGE_CONNECTION_STRING = $previousStorage }
    Pop-Location
}
