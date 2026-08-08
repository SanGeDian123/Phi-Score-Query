[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path $PSScriptRoot -Parent
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 10) { throw 'InstallRoot path is invalid.' }

$currentRoot = Join-Path $installRoot 'current'
$backendTarget = Join-Path $currentRoot 'backend\phi-backend.exe'
$templateTarget = Join-Path $currentRoot 'backend\resources\templates\image\bn\minimal.svg.jinja'
$caddyExe = Join-Path $currentRoot 'caddy\caddy.exe'
$caddyTarget = Join-Path $currentRoot 'caddy\Caddyfile'
$appUpdateRoot = Join-Path $installRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$releaseNotesPath = Join-Path $bundleRoot 'changelog-Pre-0.9.7.6.json'
$apkName = 'Phi-Score-Query-Pre-0.9.7.6.apk'
$apkSource = Join-Path $bundleRoot $apkName
$publishedApk = Join-Path $appUpdateRoot $apkName
$backendSource = Join-Path $bundleRoot 'backend\phi-backend.exe'
$templateSource = Join-Path $bundleRoot 'backend\resources\templates\image\bn\minimal.svg.jinja'
$caddySource = Join-Path $bundleRoot 'caddy\Caddyfile'
$publishScript = Join-Path $bundleRoot 'scripts\Publish-AppUpdate.ps1'

function Read-Utf8Json([string] $Path) {
    [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}

foreach ($required in @(
    $manifestPath,
    $releaseNotesPath,
    $apkSource,
    $backendSource,
    $templateSource,
    $caddySource,
    $publishScript,
    $backendTarget,
    $templateTarget,
    $caddyExe,
    $caddyTarget
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing deployment file: $required" }
}
foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "Missing scheduled task: $taskName"
    }
}

$env:APP_LOG_DIR = ($installRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($installRoot -replace '\\', '/') + '/app-update'
$env:APP_AVATAR_DIR = ($installRoot -replace '\\', '/') + '/avatar'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'New Caddy configuration validation failed.' }

$manifest = Read-Utf8Json $manifestPath
foreach ($entry in $manifest.files.psobject.Properties) {
    $filePath = Join-Path $bundleRoot $entry.Name
    if (-not (Test-Path -LiteralPath $filePath)) { throw "Missing package file: $($entry.Name)" }
    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne [string] $entry.Value) {
        throw "Package SHA-256 mismatch: $($entry.Name) / $actualHash"
    }
}

$releaseNotes = Read-Utf8Json $releaseNotesPath
$changelog = @($releaseNotes.changelog)
if ($changelog.Count -eq 0) { throw 'Release changelog is empty.' }

$backupRoot = Join-Path $installRoot ('backup\pre-0.9.7.6-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$backupUpdateRoot = Join-Path $backupRoot 'app-update'
New-Item -ItemType Directory -Force -Path $backupRoot, $backupUpdateRoot | Out-Null
$hadLatest = Test-Path -LiteralPath $latestTarget
$hadNewApk = Test-Path -LiteralPath $publishedApk
$backupReady = $false

function Wait-AppTaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "Timed out waiting for task to stop: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

try {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-AppTaskStopped 'PhigrosScore-Caddy'
    Wait-AppTaskStopped 'PhigrosScore-Backend'

    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backupRoot 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $templateTarget -Destination (Join-Path $backupRoot 'minimal.svg.jinja') -Force
    Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backupRoot 'Caddyfile') -Force
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $backupUpdateRoot 'latest.json') -Force
        $oldLatest = Read-Utf8Json $latestTarget
        if (-not [string]::IsNullOrWhiteSpace([string] $oldLatest.apkUrl)) {
            $previousApkName = Split-Path ([Uri] [string] $oldLatest.apkUrl).AbsolutePath -Leaf
            $previousApkPath = Join-Path $appUpdateRoot $previousApkName
            if (Test-Path -LiteralPath $previousApkPath) {
                Copy-Item -LiteralPath $previousApkPath -Destination (Join-Path $backupUpdateRoot $previousApkName) -Force
            }
        }
    }
    if ($hadNewApk) {
        Copy-Item -LiteralPath $publishedApk -Destination (Join-Path $backupUpdateRoot ('preexisting-' + $apkName)) -Force
    }
    $backupReady = $true

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Copy-Item -LiteralPath $templateSource -Destination $templateTarget -Force
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'

    $backendReady = $false
    $backendDeadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
            $backendReady = $true
        } catch {
            if ((Get-Date) -ge $backendDeadline) { throw 'New backend did not pass health check in 60 seconds.' }
        }
    } until ($backendReady)

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy task failed to start.'
    }

    & $publishScript `
        -ApkPath $apkSource `
        -VersionCode 34 `
        -VersionName 'Pre-0.9.7.6' `
        -PublishedAt '2026-08-08' `
        -InstallRoot $installRoot `
        -Changelog $changelog

    $latest = Read-Utf8Json $latestTarget
    $expectedHash = [string] $manifest.files.$apkName
    $actualHash = (Get-FileHash -LiteralPath $publishedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 34 -or [string] $latest.versionName -ne 'Pre-0.9.7.6') {
        throw 'Published manifest version mismatch.'
    }
    if ([string] $latest.sha256 -ne $expectedHash -or $actualHash -ne $expectedHash) {
        throw 'Published APK SHA-256 mismatch.'
    }

    Write-Output 'Pre-0.9.7.6 backend and app update published.'
    Write-Output "Backup: $backupRoot"
    Write-Output "APK SHA256: $actualHash"
    Write-Output 'Backend and Caddy restarted; the independent P30 route is enabled.'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backupRoot 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'minimal.svg.jinja') -Destination $templateTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Caddyfile') -Destination $caddyTarget -Force
        if (Test-Path -LiteralPath $latestTarget) { Remove-Item -LiteralPath $latestTarget -Force }
        if ($hadLatest) {
            Copy-Item -LiteralPath (Join-Path $backupUpdateRoot 'latest.json') -Destination $latestTarget -Force
        }
        if (Test-Path -LiteralPath $publishedApk) { Remove-Item -LiteralPath $publishedApk -Force }
        if ($hadNewApk) {
            Copy-Item -LiteralPath (Join-Path $backupUpdateRoot ('preexisting-' + $apkName)) -Destination $publishedApk -Force
        }
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "Pre-0.9.7.6 deployment failed and was rolled back. Original error: $($_.Exception.Message)"
}
