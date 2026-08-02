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
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.6.9-Fix.apk'

$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.9-Fix.apk'
$backendSource = Join-Path $bundleRoot 'phi-backend.exe'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'

foreach ($required in @($backendTarget, $publishTarget, $apkPath, $backendSource, $publishSource)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}
if (-not (Get-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue)) {
    throw '缺少计划任务: PhigrosScore-Backend'
}

$expectedApkHash = '7fd0cef7b880860065738da96f824f74497884ebeca21bc7d17143a9052f99d0'
$expectedBackendHash = '29ee8f263b2b97507dba33957b065c3005cd76baf431a94e7b8266ee57569c4b'
$actualApkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$actualBackendHash = (Get-FileHash -LiteralPath $backendSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApkHash -ne $expectedApkHash) { throw "APK SHA-256 校验失败: $actualApkHash" }
if ($actualBackendHash -ne $expectedBackendHash) { throw "后端 SHA-256 校验失败: $actualBackendHash" }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.9-fix-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $backup, $appUpdateBackup | Out-Null

$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
$backupReady = $false

function Wait-BackendTaskStopped {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName 'PhigrosScore-Backend').State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw '等待后端任务停止超时。' }
        Start-Sleep -Milliseconds 250
    }
}

try {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-BackendTaskStopped

    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backup 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.9-Fix.apk') -Force
    }
    $backupReady = $true

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    $backendReady = $false
    $backendDeadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
            $backendReady = $true
        } catch {
            if ((Get-Date) -ge $backendDeadline) { throw '新后端未能在 60 秒内通过健康检查。' }
        }
    } until ($backendReady)

    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 23 `
        -VersionName 'Pre-0.9.6.9-Fix' `
        -InstallRoot $InstallRoot `
        -Changelog @( `
            '修复 B30 成绩图底部版本水印固定停留在 Pre-0.9.6.8 的问题；水印现会跟随当前 APP 版本自动更新。', `
            '生图接口按 APP 版本隔离图片缓存，避免继续命中带有旧版本水印的缓存图片。', `
            '主页 Ranking Score 与 P30 Ranking Score 统一字号，并放大 Challenge Mode 中间的白色等级数字。' `
        )

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $installedBackendHash = (Get-FileHash -LiteralPath $backendTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 23 -or [string] $latest.versionName -ne 'Pre-0.9.6.9-Fix') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash -or $publishedHash -ne $expectedApkHash) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }
    if ($installedBackendHash -ne $expectedBackendHash) {
        throw '安装后的后端 SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.6.9-Fix 已发布。备份位于: $backup"
    Write-Output '后端已重启并通过本机健康检查；更新清单已向旧版本用户发布。'
    Write-Output '本次未修改或重启 Caddy。'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backup 'phi-backend.exe') -Destination $backendTarget -Force
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
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.9-Fix.apk') -Destination $publishedApkTarget -Force
        }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端和更新清单。原始错误: $($_.Exception.Message)"
}
