[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($InstallRoot.Length -lt 10) { throw 'InstallRoot 路径异常。' }

$current = Join-Path $InstallRoot 'current'
$backendTarget = Join-Path $current 'backend\phi-backend.exe'
$caddyExe = Join-Path $current 'caddy\caddy.exe'
$caddyTarget = Join-Path $current 'caddy\Caddyfile'
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.7.0.apk'

$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.7.0.apk'
$backendSource = Join-Path $bundleRoot 'phi-backend.exe'
$caddySource = Join-Path $bundleRoot 'Caddyfile'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'

foreach ($required in @(
    $backendTarget,
    $caddyExe,
    $caddyTarget,
    $publishTarget,
    $apkPath,
    $backendSource,
    $caddySource,
    $publishSource
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}
foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "缺少计划任务: $taskName"
    }
}

$expectedHashes = @{
    $apkPath = '9be5373cf62c052a355148e61b37fcac6c2b08d1bf3b7e7cb881818f089a0975'
    $backendSource = 'de8f9801887e74936a5948d6116c36f7934bb5de92cb5a4846a67a28cec95f12'
    $caddySource = '5808cd8ab9c6a34aa39573c0e5b11f0d13fa56a31fb37f92d55e4c7a9b7e8200'
    $publishSource = '549dd7efd4a06b6d9151256ac00718451ffd04a0a4314a8f4e17ee4973e2e27d'
}
foreach ($entry in $expectedHashes.GetEnumerator()) {
    $actualHash = (Get-FileHash -LiteralPath $entry.Key -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $entry.Value) {
        throw "文件 SHA-256 校验失败: $($entry.Key) / $actualHash"
    }
}

if (Test-Path -LiteralPath (Join-Path $InstallRoot 'secrets.env')) {
    Get-Content -LiteralPath (Join-Path $InstallRoot 'secrets.env') | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
$env:APP_LOG_DIR = ($InstallRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($InstallRoot -replace '\\', '/') + '/app-update'
$env:APP_AVATAR_DIR = ($InstallRoot -replace '\\', '/') + '/avatar'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw '新 Caddy 配置验证失败，尚未修改线上文件。' }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.7.0-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $backup, $appUpdateBackup | Out-Null
$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
$backupReady = $false

function Stop-AppTask([string] $TaskName) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "等待任务停止超时: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

function Wait-Endpoint([string] $Uri, [int] $TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            return Invoke-RestMethod -Uri $Uri -TimeoutSec 8
        } catch {
            if ((Get-Date) -ge $deadline) { throw "接口未能在 $TimeoutSeconds 秒内通过检查: $Uri" }
            Start-Sleep -Seconds 2
        }
    } while ($true)
}

try {
    Stop-AppTask 'PhigrosScore-Caddy'
    Stop-AppTask 'PhigrosScore-Backend'

    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backup 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backup 'Caddyfile') -Force
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.7.0.apk') -Force
    }
    $backupReady = $true

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Endpoint 'http://127.0.0.1:3939/health' | Out-Null
    $localCatalog = Wait-Endpoint 'http://127.0.0.1:3939/api/v2/songs/catalog'
    if ([string]::IsNullOrWhiteSpace([string] $localCatalog.version) -or @($localCatalog.items).Count -eq 0) {
        throw '后端曲库接口返回内容无效。'
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy 任务启动失败。'
    }
    $publicCatalog = Wait-Endpoint 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog'
    if ([string] $publicCatalog.version -ne [string] $localCatalog.version) {
        throw '公网曲库接口版本与本机后端不一致。'
    }

    $releaseChangelog = [string[]] @(
        '曲库改为由服务器统一提供，后端更新曲库后 APP 会在启动时自动同步。',
        '服务器曲库会保存在本地，网络不可用时自动使用上次同步结果或 APK 内置曲库。',
        '定数表、单曲搜索和曲目详情统一使用同步后的曲库，新曲无需先产生游玩记录即可显示。'
    )
    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 24 `
        -VersionName 'Pre-0.9.7.0' `
        -InstallRoot $InstallRoot `
        -Changelog $releaseChangelog

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $installedBackendHash = (Get-FileHash -LiteralPath $backendTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $installedCaddyHash = (Get-FileHash -LiteralPath $caddyTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 24 -or [string] $latest.versionName -ne 'Pre-0.9.7.0') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedHashes[$apkPath] -or $publishedHash -ne $expectedHashes[$apkPath]) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }
    if ($installedBackendHash -ne $expectedHashes[$backendSource]) {
        throw '安装后的后端 SHA-256 校验失败。'
    }
    if ($installedCaddyHash -ne $expectedHashes[$caddySource]) {
        throw '安装后的 Caddyfile SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.7.0 已发布。备份位于: $backup"
    Write-Output "曲库版本: $($localCatalog.version) / 曲目数: $(@($localCatalog.items).Count)"
    Write-Output '后端、Caddy、曲库公网接口和更新清单均已通过检查。'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backup 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Caddyfile') -Destination $caddyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force
        if (Test-Path -LiteralPath $latestTarget) {
            Remove-Item -LiteralPath $latestTarget -Force
        }
        if ($hadLatest) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'latest.json') -Destination $latestTarget -Force
        }
        if (Test-Path -LiteralPath $publishedApkTarget) {
            Remove-Item -LiteralPath $publishedApkTarget -Force
        }
        if ($hadPublishedApk) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.7.0.apk') -Destination $publishedApkTarget -Force
        }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端、Caddy 配置和更新清单。原始错误: $($_.Exception.Message)"
}
