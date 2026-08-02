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
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.9.apk'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.6.9.apk'
$expectedApkHash = 'e561ff06309156d41db02e1c98fc9caee8de64a71c80e84eac9ea5a13589791b'

foreach ($required in @($publishTarget, $publishSource, $apkPath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$actualApkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApkHash -ne $expectedApkHash) { throw "APK SHA-256 校验失败: $actualApkHash" }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.9-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force

$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
if ($hadLatest) {
    Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $backup 'latest.json') -Force
}
if ($hadPublishedApk) {
    Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $backup 'Phi-Score-Query-Pre-0.9.6.9.apk') -Force
}

try {
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 22 `
        -VersionName 'Pre-0.9.6.9' `
        -InstallRoot $InstallRoot `
        -Changelog @( `
            '侧边导航新增定数表，默认按 17 级至 1 级展示全部谱面，并可直接筛选指定整数等级。', `
            '同一等级内按精确定数从高到低排列，显示完整曲名、谱面难度和一位小数定数。', `
            '定数表曲绘沿用单曲页面的横向渐变样式，点击任意谱面可直接进入对应曲目详情。', `
            '定数表首次显示和切换等级时增加自上而下依次展开的流畅动画。' `
        )

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 22 -or [string] $latest.versionName -ne 'Pre-0.9.6.9') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash -or $publishedHash -ne $expectedApkHash) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.6.9 已发布。备份位于: $backup"
    Write-Output '本次仅发布 APP 安装包与更新清单，未停止或重启 Next-Phi-Backend 与 Caddy。'
} catch {
    Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force

    if (Test-Path -LiteralPath $publishedApkTarget) {
        Remove-Item -LiteralPath $publishedApkTarget -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath (Join-Path $backup 'Phi-Score-Query-Pre-0.9.6.9.apk') -Destination $publishedApkTarget -Force
    }

    if (Test-Path -LiteralPath $latestTarget) {
        Remove-Item -LiteralPath $latestTarget -Force
    }
    if ($hadLatest) {
        Copy-Item -LiteralPath (Join-Path $backup 'latest.json') -Destination $latestTarget -Force
    }

    throw "发布失败，已恢复原更新脚本和更新清单。原始错误: $($_.Exception.Message)"
}
