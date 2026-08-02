[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($InstallRoot.Length -lt 10) { throw 'InstallRoot 路径异常。' }

$current = Join-Path $InstallRoot 'current'
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.7.0-Fix2.apk'

$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.7.0-Fix2.apk'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'

foreach ($required in @($publishTarget, $apkPath, $publishSource)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$expectedApkHash = 'bffa299806f2dd9f452117828f330cbac8e9b9cfdbafe4218d170230801160e5'
$expectedPublishHash = '549dd7efd4a06b6d9151256ac00718451ffd04a0a4314a8f4e17ee4973e2e27d'
$actualApkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$actualPublishHash = (Get-FileHash -LiteralPath $publishSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApkHash -ne $expectedApkHash) { throw "APK SHA-256 校验失败: $actualApkHash" }
if ($actualPublishHash -ne $expectedPublishHash) { throw "发布脚本 SHA-256 校验失败: $actualPublishHash" }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.7.0-fix2-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $backup, $appUpdateBackup | Out-Null

$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
$backupReady = $false

function Wait-PublicApk([string] $Uri, [int] $TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Method Head -Uri $Uri -UseBasicParsing -TimeoutSec 10
            if ([int] $response.StatusCode -eq 200) { return }
        } catch {
            if ((Get-Date) -ge $deadline) { throw }
        }
        if ((Get-Date) -ge $deadline) { throw "APK 公网地址未能在 $TimeoutSeconds 秒内通过检查。" }
        Start-Sleep -Seconds 2
    } while ($true)
}

try {
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.7.0-Fix2.apk') -Force
    }
    $backupReady = $true

    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force
    $releaseChangelog = [string[]] @(
        '补全《星拂云锦 feat. koi》的章节信息：Single。',
        '补全 EZ、HD、IN 谱师：华星秋月、帷畔托星、灵琶弄月。',
        '补全 EZ、HD、IN 谱面物量：227、600、1235。'
    )
    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 26 `
        -VersionName 'Pre-0.9.7.0-Fix2' `
        -InstallRoot $InstallRoot `
        -Changelog $releaseChangelog

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $publishedSize = (Get-Item -LiteralPath $publishedApkTarget).Length
    if ([int] $latest.versionCode -ne 26 -or [string] $latest.versionName -ne 'Pre-0.9.7.0-Fix2') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash -or $publishedHash -ne $expectedApkHash) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }
    if ([long] $latest.sizeBytes -ne $publishedSize) {
        throw 'latest.json APK 文件大小校验失败。'
    }

    Wait-PublicApk ([string] $latest.apkUrl)
    Write-Output "Pre-0.9.7.0-Fix2 已发布。备份位于: $backup"
    Write-Output 'APK 和更新清单已通过校验；本次未修改或重启后端与 Caddy。'
} catch {
    if ($backupReady) {
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
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.7.0-Fix2.apk') -Destination $publishedApkTarget -Force
        }
    }
    throw "部署失败，已恢复原发布脚本和更新清单。原始错误: $($_.Exception.Message)"
}
