[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($InstallRoot.Length -lt 10) { throw 'InstallRoot 路径异常。' }

$publishTarget = Join-Path $InstallRoot 'current\scripts\Publish-AppUpdate.ps1'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.7.apk'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.6.7.apk'
$expectedApkHash = 'b11db87d27d10e2945d38d34cf7044bc21d541e85ac61b4ed69dd6d34636bffc'

foreach ($required in @($publishTarget, $publishSource, $apkPath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$actualApkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApkHash -ne $expectedApkHash) { throw "APK SHA-256 校验失败: $actualApkHash" }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.7-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force

$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
if ($hadLatest) {
    Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $backup 'latest.json') -Force
}
if ($hadPublishedApk) {
    Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $backup 'Phi-Score-Query-Pre-0.9.6.7.apk') -Force
}

try {
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 20 `
        -VersionName 'Pre-0.9.6.7' `
        -InstallRoot $InstallRoot `
        -Changelog @( `
            '成绩更新信息按实际变化字段显示：仅 ACC 变化只显示 ACC，仅分数变化只显示分数，两者均变化时同时显示。', `
            '已核对 Next-Phi-Backend 最新接口；当前后端未提供最近一次实际游玩详情，因此继续使用两次存档差分。' `
        )

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 20 -or [string] $latest.versionName -ne 'Pre-0.9.6.7') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash -or $publishedHash -ne $expectedApkHash) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.6.7 已发布。备份位于: $backup"
    Write-Output '本次未停止或重启 Next-Phi-Backend 与 Caddy。'
} catch {
    Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force

    if (Test-Path -LiteralPath $publishedApkTarget) {
        Remove-Item -LiteralPath $publishedApkTarget -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath (Join-Path $backup 'Phi-Score-Query-Pre-0.9.6.7.apk') -Destination $publishedApkTarget -Force
    }

    if (Test-Path -LiteralPath $latestTarget) {
        Remove-Item -LiteralPath $latestTarget -Force
    }
    if ($hadLatest) {
        Copy-Item -LiteralPath (Join-Path $backup 'latest.json') -Destination $latestTarget -Force
    }

    throw "发布失败，已恢复原更新脚本和更新清单。原始错误: $($_.Exception.Message)"
}
