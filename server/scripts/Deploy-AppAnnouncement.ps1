[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path $PSScriptRoot -Parent
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 10) { throw 'InstallRoot path is invalid.' }

$currentRoot = Join-Path $installRoot 'current'
$caddyExe = Join-Path $currentRoot 'caddy\caddy.exe'
$caddyTarget = Join-Path $currentRoot 'caddy\Caddyfile'
$runCaddyTarget = Join-Path $currentRoot 'scripts\Run-Caddy.ps1'
$publishTarget = Join-Path $currentRoot 'scripts\Publish-AppAnnouncement.ps1'
$caddySource = Join-Path $bundleRoot 'caddy\Caddyfile'
$runCaddySource = Join-Path $bundleRoot 'scripts\Run-Caddy.ps1'
$publishSource = Join-Path $bundleRoot 'scripts\Publish-AppAnnouncement.ps1'
$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$announcementRoot = Join-Path $installRoot 'app-announcement'

foreach ($required in @(
    $manifestPath,
    $caddySource,
    $runCaddySource,
    $publishSource,
    $caddyExe,
    $caddyTarget,
    $runCaddyTarget
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing deployment file: $required" }
}
if (-not (Get-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue)) {
    throw 'Missing scheduled task: PhigrosScore-Caddy'
}

$manifest = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
foreach ($entry in $manifest.files.psobject.Properties) {
    $filePath = Join-Path $bundleRoot $entry.Name
    if (-not (Test-Path -LiteralPath $filePath)) { throw "Missing package file: $($entry.Name)" }
    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne [string] $entry.Value) {
        throw "Package SHA-256 mismatch: $($entry.Name) / $actualHash"
    }
}

$env:APP_LOG_DIR = ($installRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($installRoot -replace '\\', '/') + '/app-update'
$env:APP_ANNOUNCEMENT_DIR = ($installRoot -replace '\\', '/') + '/app-announcement'
$env:APP_AVATAR_DIR = ($installRoot -replace '\\', '/') + '/avatar'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'New Caddy configuration validation failed.' }

$backupRoot = Join-Path $installRoot ('backup\app-announcement-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backupRoot, $announcementRoot | Out-Null
Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backupRoot 'Caddyfile') -Force
Copy-Item -LiteralPath $runCaddyTarget -Destination (Join-Path $backupRoot 'Run-Caddy.ps1') -Force
$hadPublishScript = Test-Path -LiteralPath $publishTarget
if ($hadPublishScript) {
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') -Force
}

function Wait-AppTaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "Timed out waiting for task to stop: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

try {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Wait-AppTaskStopped 'PhigrosScore-Caddy'
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $runCaddySource -Destination $runCaddyTarget -Force
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy task failed to start.'
    }
    Write-Output 'APP announcement support deployed.'
    Write-Output "Backup: $backupRoot"
    Write-Output 'No announcement was published automatically.'
    Write-Output "Publish script: $publishTarget"
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Copy-Item -LiteralPath (Join-Path $backupRoot 'Caddyfile') -Destination $caddyTarget -Force
    Copy-Item -LiteralPath (Join-Path $backupRoot 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
    if ($hadPublishScript) {
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') -Destination $publishTarget -Force
    } elseif (Test-Path -LiteralPath $publishTarget) {
        Remove-Item -LiteralPath $publishTarget -Force
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "APP announcement deployment failed and was rolled back. Original error: $($_.Exception.Message)"
}
