[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path -Parent $PSScriptRoot
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 10) { throw 'InstallRoot path is invalid.' }

$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$backendSource = Join-Path $bundleRoot 'backend\phi-backend.exe'
$caddySource = Join-Path $bundleRoot 'caddy\Caddyfile'
$runBackendSource = Join-Path $bundleRoot 'scripts\Run-Backend.ps1'
$publishAnnouncementSource = Join-Path $bundleRoot 'scripts\Publish-AppAnnouncement.ps1'
$currentRoot = Join-Path $installRoot 'current'
$backendTarget = Join-Path $currentRoot 'backend\phi-backend.exe'
$caddyTarget = Join-Path $currentRoot 'caddy\Caddyfile'
$caddyExe = Join-Path $currentRoot 'caddy\caddy.exe'
$runBackendTarget = Join-Path $currentRoot 'scripts\Run-Backend.ps1'
$publishAnnouncementTarget = Join-Path $currentRoot 'scripts\Publish-AppAnnouncement.ps1'
$latestTarget = Join-Path $installRoot 'app-update\latest.json'
$announcementIndexTarget = Join-Path $installRoot 'app-announcement\index.json'

function Get-FileHashOrNull([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-HttpStatus([string] $Uri, [hashtable] $Headers = @{}) {
    try {
        $response = Invoke-WebRequest -Uri $Uri -Headers $Headers -UseBasicParsing -TimeoutSec 15
        return [int] $response.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int] $_.Exception.Response.StatusCode }
        return 0
    }
}

function Wait-TaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "Timed out stopping $TaskName." }
        Start-Sleep -Milliseconds 250
    }
}

function Wait-BackendHealth {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        if ((Get-HttpStatus 'http://127.0.0.1:3939/health') -eq 200) { return }
        if ((Get-Date) -ge $deadline) { throw 'Backend health check timed out.' }
        Start-Sleep -Seconds 2
    } while ($true)
}

foreach ($required in @($manifestPath, $backendSource, $caddySource, $runBackendSource, $publishAnnouncementSource)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing package file: $required" }
}

$manifest = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
foreach ($entry in $manifest.files.psobject.Properties) {
    $path = Join-Path $bundleRoot ([string] $entry.Name).Replace('/', '\')
    if ((Get-FileHashOrNull $path) -ne ([string] $entry.Value).ToLowerInvariant()) {
        throw "Package SHA-256 mismatch: $($entry.Name)"
    }
}

foreach ($required in @($backendTarget, $caddyTarget, $caddyExe, $runBackendTarget)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing installed server file: $required" }
}

$env:APP_LOG_DIR = ($installRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($installRoot -replace '\\', '/') + '/app-update'
$env:APP_ANNOUNCEMENT_DIR = ($installRoot -replace '\\', '/') + '/app-announcement'
$env:APP_AVATAR_DIR = ($installRoot -replace '\\', '/') + '/avatar'
$env:APP_SOURCE_DIR = ($installRoot -replace '\\', '/') + '/source'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'Caddy configuration validation failed.' }

if ($ValidateOnly) {
    Write-Output 'Validation passed. Package hashes and Caddy configuration are valid; no files were changed.'
    return
}

foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "Missing scheduled task: $taskName"
    }
}

$latestBefore = Get-FileHashOrNull $latestTarget
$backupRoot = Join-Path $installRoot ('backup\admin-console-api-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$backupCreated = $false

try {
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backupRoot 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backupRoot 'Caddyfile') -Force
    Copy-Item -LiteralPath $runBackendTarget -Destination (Join-Path $backupRoot 'Run-Backend.ps1') -Force
    $hadPublishAnnouncement = Test-Path -LiteralPath $publishAnnouncementTarget
    if ($hadPublishAnnouncement) {
        Copy-Item -LiteralPath $publishAnnouncementTarget -Destination (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') -Force
    }
    $backupCreated = $true

    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-TaskStopped 'PhigrosScore-Caddy'
    Wait-TaskStopped 'PhigrosScore-Backend'

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $runBackendSource -Destination $runBackendTarget -Force
    Copy-Item -LiteralPath $publishAnnouncementSource -Destination $publishAnnouncementTarget -Force

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-BackendHealth
    $invalidHeaders = @{ 'X-Admin-Token' = 'deployment-invalid-token-probe' }
    $date = Get-Date -Format 'yyyy-MM-dd'
    $dashboardProbe = "http://127.0.0.1:3939/api/v2/admin/dashboard?start=$date&end=$date&timezone=Asia%2FShanghai"
    if ((Get-HttpStatus $dashboardProbe $invalidHeaders) -ne 401) {
        throw 'Admin dashboard authentication boundary probe failed.'
    }
    if ((Get-HttpStatus 'http://127.0.0.1:3939/api/v2/admin/announcement' $invalidHeaders) -ne 401) {
        throw 'Announcement authentication boundary probe failed.'
    }
    if (-not (Test-Path -LiteralPath $announcementIndexTarget)) {
        throw 'Announcement history migration did not create index.json.'
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/health') -ne 200) {
        throw 'Public health probe failed.'
    }
    $publicDashboard = "https://api.plc-liangpi-cup.xyz/api/v2/admin/dashboard?start=$date&end=$date&timezone=Asia%2FShanghai"
    if ((Get-HttpStatus $publicDashboard $invalidHeaders) -ne 401) {
        throw 'Public admin dashboard route did not reach the authenticated backend.'
    }
    if ((Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/api/v2/admin/announcement' $invalidHeaders) -ne 401) {
        throw 'Public announcement route did not reach the authenticated backend.'
    }
    if ((Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/app-announcement/index.json') -ne 200) {
        throw 'Public announcement history route failed.'
    }
    if ((Get-FileHashOrNull $latestTarget) -ne $latestBefore) {
        throw 'APP update manifest changed unexpectedly.'
    }

    Write-Output "Admin console API fix deployed successfully. Backup: $backupRoot"
} catch {
    $failure = $_
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    if ($backupCreated) {
        Copy-Item -LiteralPath (Join-Path $backupRoot 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Caddyfile') -Destination $caddyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Run-Backend.ps1') -Destination $runBackendTarget -Force
        if ($hadPublishAnnouncement) {
            Copy-Item -LiteralPath (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') -Destination $publishAnnouncementTarget -Force
        } elseif (Test-Path -LiteralPath $publishAnnouncementTarget) {
            Remove-Item -LiteralPath $publishAnnouncementTarget -Force
        }
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-BackendHealth
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "Deployment failed and server files were rolled back: $($failure.Exception.Message)"
}
