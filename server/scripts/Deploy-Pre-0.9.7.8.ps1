[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $CaddyExecutable
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path $PSScriptRoot -Parent
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 10) { throw 'InstallRoot path is invalid.' }

$currentRoot = Join-Path $installRoot 'current'
$backendTarget = Join-Path $currentRoot 'backend\phi-backend.exe'
$fontTarget = Join-Path $currentRoot 'backend\resources\fonts\Aldrich-Regular.ttf'
$assetsTarget = Join-Path $currentRoot 'backend\resources\templates\image\bn\phi_plugin_assets'
$installedCaddyExe = Join-Path $currentRoot 'caddy\caddy.exe'
$caddyExe = if ([string]::IsNullOrWhiteSpace($CaddyExecutable)) {
    $installedCaddyExe
} else {
    [IO.Path]::GetFullPath($CaddyExecutable)
}
$caddyTarget = Join-Path $currentRoot 'caddy\Caddyfile'
$runBackendTarget = Join-Path $currentRoot 'scripts\Run-Backend.ps1'
$runCaddyTarget = Join-Path $currentRoot 'scripts\Run-Caddy.ps1'
$publishUpdateTarget = Join-Path $currentRoot 'scripts\Publish-AppUpdate.ps1'
$publishAnnouncementTarget = Join-Path $currentRoot 'scripts\Publish-AppAnnouncement.ps1'
$appUpdateRoot = Join-Path $installRoot 'app-update'
$announcementRoot = Join-Path $installRoot 'app-announcement'
$suggestionMediaRoot = Join-Path $installRoot 'suggestion-media'
$sourceOfferRoot = Join-Path $installRoot 'source'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$sourceArchiveName = 'backend-source-Pre-0.9.7.8.zip'
$sourceArchiveTarget = Join-Path $sourceOfferRoot $sourceArchiveName

$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$releaseNotesPath = Join-Path $bundleRoot 'changelog-Pre-0.9.7.8.json'
$apkName = 'Phi-Score-Query-Pre-0.9.7.8.apk'
$apkSource = Join-Path $bundleRoot $apkName
$publishedApk = Join-Path $appUpdateRoot $apkName
$backendSource = Join-Path $bundleRoot 'backend\phi-backend.exe'
$fontSource = Join-Path $bundleRoot 'backend\resources\fonts\Aldrich-Regular.ttf'
$assetsSource = Join-Path $bundleRoot 'backend\resources\templates\image\bn\phi_plugin_assets'
$caddySource = Join-Path $bundleRoot 'caddy\Caddyfile'
$runBackendSource = Join-Path $bundleRoot 'scripts\Run-Backend.ps1'
$runCaddySource = Join-Path $bundleRoot 'scripts\Run-Caddy.ps1'
$publishUpdateSource = Join-Path $bundleRoot 'scripts\Publish-AppUpdate.ps1'
$publishAnnouncementSource = Join-Path $bundleRoot 'scripts\Publish-AppAnnouncement.ps1'
$sourceArchiveSource = Join-Path $bundleRoot ('source\' + $sourceArchiveName)

function Read-Utf8Json([string] $Path) {
    [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}

foreach ($required in @(
    $manifestPath,
    $releaseNotesPath,
    $apkSource,
    $backendSource,
    $fontSource,
    $assetsSource,
    $caddySource,
    $runBackendSource,
    $runCaddySource,
    $publishUpdateSource,
    $publishAnnouncementSource,
    $sourceArchiveSource,
    $caddyExe
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing deployment file: $required" }
}
if (-not $ValidateOnly) {
    foreach ($required in @(
        $backendTarget,
        $installedCaddyExe,
        $caddyTarget,
        $runBackendTarget,
        $runCaddyTarget
    )) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Missing installed file: $required" }
    }
    foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
        if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
            throw "Missing scheduled task: $taskName"
        }
    }
}

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

$env:APP_LOG_DIR = ($installRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($installRoot -replace '\\', '/') + '/app-update'
$env:APP_ANNOUNCEMENT_DIR = ($installRoot -replace '\\', '/') + '/app-announcement'
$env:APP_AVATAR_DIR = ($installRoot -replace '\\', '/') + '/avatar'
$env:APP_SOURCE_DIR = ($installRoot -replace '\\', '/') + '/source'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'New Caddy configuration validation failed.' }
if ($ValidateOnly) {
    Write-Output 'Pre-0.9.7.8 package hashes, release metadata, and Caddy configuration are valid.'
    return
}

$backupRoot = Join-Path $installRoot ('backup\pre-0.9.7.8-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$backupUpdateRoot = Join-Path $backupRoot 'app-update'
New-Item -ItemType Directory -Force -Path `
    $backupRoot, $backupUpdateRoot, $appUpdateRoot, $announcementRoot, $suggestionMediaRoot, $sourceOfferRoot | Out-Null
Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backupRoot 'phi-backend.exe') -Force
Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backupRoot 'Caddyfile') -Force
Copy-Item -LiteralPath $runBackendTarget -Destination (Join-Path $backupRoot 'Run-Backend.ps1') -Force
Copy-Item -LiteralPath $runCaddyTarget -Destination (Join-Path $backupRoot 'Run-Caddy.ps1') -Force

$hadFont = Test-Path -LiteralPath $fontTarget
$hadAssets = Test-Path -LiteralPath $assetsTarget
$hadPublishUpdate = Test-Path -LiteralPath $publishUpdateTarget
$hadPublishAnnouncement = Test-Path -LiteralPath $publishAnnouncementTarget
$hadSourceArchive = Test-Path -LiteralPath $sourceArchiveTarget
if ($hadFont) { Copy-Item -LiteralPath $fontTarget -Destination (Join-Path $backupRoot 'Aldrich-Regular.ttf') -Force }
if ($hadAssets) { Copy-Item -LiteralPath $assetsTarget -Destination (Join-Path $backupRoot 'phi_plugin_assets') -Recurse }
if ($hadPublishUpdate) { Copy-Item -LiteralPath $publishUpdateTarget -Destination (Join-Path $backupRoot 'Publish-AppUpdate.ps1') -Force }
if ($hadPublishAnnouncement) { Copy-Item -LiteralPath $publishAnnouncementTarget -Destination (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') -Force }
if ($hadSourceArchive) { Copy-Item -LiteralPath $sourceArchiveTarget -Destination (Join-Path $backupRoot $sourceArchiveName) -Force }

$hadLatest = Test-Path -LiteralPath $latestTarget
$hadNewApk = Test-Path -LiteralPath $publishedApk
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

function Wait-AppTaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "Timed out waiting for task to stop: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

function Restore-OptionalFile([bool] $Existed, [string] $BackupName, [string] $Target) {
    if ($Existed) {
        Copy-Item -LiteralPath (Join-Path $backupRoot $BackupName) -Destination $Target -Force
    } elseif (Test-Path -LiteralPath $Target) {
        Remove-Item -LiteralPath $Target -Force
    }
}

try {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-AppTaskStopped 'PhigrosScore-Caddy'
    Wait-AppTaskStopped 'PhigrosScore-Backend'

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    New-Item -ItemType Directory -Force -Path `
        (Split-Path $fontTarget -Parent), `
        (Split-Path $assetsTarget -Parent), `
        (Split-Path $publishUpdateTarget -Parent) | Out-Null
    Copy-Item -LiteralPath $fontSource -Destination $fontTarget -Force
    if (Test-Path -LiteralPath $assetsTarget) { Remove-Item -LiteralPath $assetsTarget -Recurse -Force }
    Copy-Item -LiteralPath $assetsSource -Destination $assetsTarget -Recurse
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $runBackendSource -Destination $runBackendTarget -Force
    Copy-Item -LiteralPath $runCaddySource -Destination $runCaddyTarget -Force
    Copy-Item -LiteralPath $publishUpdateSource -Destination $publishUpdateTarget -Force
    Copy-Item -LiteralPath $publishAnnouncementSource -Destination $publishAnnouncementTarget -Force
    Copy-Item -LiteralPath $sourceArchiveSource -Destination $sourceArchiveTarget -Force

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

    $suggestionStatus = 0
    try {
        Invoke-WebRequest 'http://127.0.0.1:3939/api/v2/suggestions/random' -UseBasicParsing -TimeoutSec 10 | Out-Null
        $suggestionStatus = 200
    } catch {
        if ($_.Exception.Response) { $suggestionStatus = [int] $_.Exception.Response.StatusCode }
    }
    if ($suggestionStatus -ne 401) {
        throw "Suggestion API authentication probe failed: HTTP $suggestionStatus"
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy task failed to start.'
    }

    & $publishUpdateTarget `
        -ApkPath $apkSource `
        -VersionCode 39 `
        -VersionName 'Pre-0.9.7.8' `
        -PublishedAt '2026-08-11' `
        -InstallRoot $installRoot `
        -Changelog $changelog

    $latest = Read-Utf8Json $latestTarget
    $expectedHash = [string] $manifest.files.$apkName
    $actualHash = (Get-FileHash -LiteralPath $publishedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 39 -or [string] $latest.versionName -ne 'Pre-0.9.7.8') {
        throw 'Published manifest version mismatch.'
    }
    if ([string] $latest.sha256 -ne $expectedHash -or $actualHash -ne $expectedHash) {
        throw 'Published APK SHA-256 mismatch.'
    }
    if (-not (Test-Path -LiteralPath $fontTarget) -or
        -not (Test-Path -LiteralPath $assetsTarget) -or
        -not (Test-Path -LiteralPath $sourceArchiveTarget)) {
        throw 'Phi-Plugin resources or backend source archive were not installed.'
    }

    Write-Output 'Pre-0.9.7.8 backend, suggestion API, Phi-Plugin renderer, and APP update deployed.'
    Write-Output "Backup: $backupRoot"
    Write-Output "Suggestion media: $suggestionMediaRoot"
    Write-Output "APK SHA256: $actualHash"
    Write-Output 'No announcement was published automatically.'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backupRoot 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Caddyfile') -Destination $caddyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Run-Backend.ps1') -Destination $runBackendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backupRoot 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
        Restore-OptionalFile $hadFont 'Aldrich-Regular.ttf' $fontTarget
        Restore-OptionalFile $hadPublishUpdate 'Publish-AppUpdate.ps1' $publishUpdateTarget
        Restore-OptionalFile $hadPublishAnnouncement 'Publish-AppAnnouncement.ps1' $publishAnnouncementTarget
        Restore-OptionalFile $hadSourceArchive $sourceArchiveName $sourceArchiveTarget
        if (Test-Path -LiteralPath $assetsTarget) { Remove-Item -LiteralPath $assetsTarget -Recurse -Force }
        if ($hadAssets) { Copy-Item -LiteralPath (Join-Path $backupRoot 'phi_plugin_assets') -Destination $assetsTarget -Recurse }
        if (Test-Path -LiteralPath $latestTarget) { Remove-Item -LiteralPath $latestTarget -Force }
        if ($hadLatest) { Copy-Item -LiteralPath (Join-Path $backupUpdateRoot 'latest.json') -Destination $latestTarget -Force }
        if (Test-Path -LiteralPath $publishedApk) { Remove-Item -LiteralPath $publishedApk -Force }
        if ($hadNewApk) {
            Copy-Item -LiteralPath (Join-Path $backupUpdateRoot ('preexisting-' + $apkName)) -Destination $publishedApk -Force
        }
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "Pre-0.9.7.8 deployment failed and was rolled back. Original error: $($_.Exception.Message)"
}
