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
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.6.apk'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.6.6.apk'
$expectedApkHash = '0e045be6f3b65b0b1ab74bbc8894435cfc31bffc25c082cb2d5cbf586aec59c4'

foreach ($required in @($publishTarget, $publishSource, $apkPath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$actualApkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApkHash -ne $expectedApkHash) { throw "APK SHA-256 校验失败: $actualApkHash" }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.6-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force

$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
if ($hadLatest) {
    Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $backup 'latest.json') -Force
}
if ($hadPublishedApk) {
    Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $backup 'Phi-Score-Query-Pre-0.9.6.6.apk') -Force
}

try {
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 19 `
        -VersionName 'Pre-0.9.6.6' `
        -Changelog @( `
            '成绩更新信息会区分“分数提升”“ACC 提升”与“分数、ACC 提升”，不再混用未变化项目的历史最高值。', `
            '概览页 B30 成绩图支持点击进入图片页，图片页大图支持双指缩放和拖动查看。', `
            'B30 图片生成时显示当前用时，重新生成前会先删除旧图片。' `
        )

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 19 -or [string] $latest.versionName -ne 'Pre-0.9.6.6') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash -or $publishedHash -ne $expectedApkHash) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.6.6 已发布。备份位于: $backup"
    Write-Output '本次未停止或重启 Next-Phi-Backend 与 Caddy。'
} catch {
    Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force

    if (Test-Path -LiteralPath $publishedApkTarget) {
        Remove-Item -LiteralPath $publishedApkTarget -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath (Join-Path $backup 'Phi-Score-Query-Pre-0.9.6.6.apk') -Destination $publishedApkTarget -Force
    }

    if (Test-Path -LiteralPath $latestTarget) {
        Remove-Item -LiteralPath $latestTarget -Force
    }
    if ($hadLatest) {
        Copy-Item -LiteralPath (Join-Path $backup 'latest.json') -Destination $latestTarget -Force
    }

    throw "发布失败，已恢复原更新脚本和更新清单。原始错误: $($_.Exception.Message)"
}
