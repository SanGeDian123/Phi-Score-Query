[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $PublicLatestUri = 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
)

$ErrorActionPreference = 'Stop'
$versionCode = 69
$versionName = 'Pre-0.9.7.10-Fix'
$apkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix-v69.apk'
$publishedApkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix.apk'
$bundleRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkSource = Join-Path $bundleRoot $apkName
$publisher = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$notesPath = Join-Path $bundleRoot 'changelog-Pre-0.9.7.10-Fix.json'
$manifestPath = Join-Path $bundleRoot 'APP_UPDATE_MANIFEST.json'
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 12 -or $installRoot -match '^[A-Za-z]:\\?$') { throw 'InstallRoot path is invalid.' }
$updateRoot = Join-Path $installRoot 'app-update'
$latestTarget = Join-Path $updateRoot 'latest.json'
$publishedApk = Join-Path $updateRoot $publishedApkName

function Read-Utf8Json([string] $Path) {
    return [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}
function Assert-PackageFile([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required package file missing: $Path" }
}

foreach ($path in @($apkSource, $publisher, $notesPath, $manifestPath)) { Assert-PackageFile $path }
if (-not (Test-Path -LiteralPath $installRoot -PathType Container)) { throw "InstallRoot not found: $installRoot" }
if (-not (Test-Path -LiteralPath $updateRoot -PathType Container)) { throw "APP update directory not found: $updateRoot" }
$manifest = Read-Utf8Json $manifestPath
$releaseNotes = Read-Utf8Json $notesPath
if ([int]$manifest.versionCode -ne $versionCode -or [string]$manifest.versionName -cne $versionName -or
    [string]$manifest.apkFile -cne $apkName) { throw 'APP update package manifest does not match this release.' }
if ([string]$releaseNotes.versionName -cne $versionName -or @($releaseNotes.changelog).Count -eq 0) {
    throw 'Release changelog version mismatch or empty changelog.'
}
if ((Get-Item -LiteralPath $apkSource).Length -lt 1048576) { throw 'Release APK is unexpectedly small.' }

$currentUpdate = $null
if (Test-Path -LiteralPath $latestTarget -PathType Leaf) {
    $currentUpdate = Read-Utf8Json $latestTarget
    if ([int]$currentUpdate.versionCode -ge $versionCode) {
        throw "APP update versionCode $($currentUpdate.versionCode) is already published; refusing to replace it with versionCode $versionCode."
    }
}
if ($ValidateOnly) {
    Write-Output "VALIDATION OK: versionCode=$versionCode versionName=$versionName; current=$([int]$currentUpdate.versionCode); no files changed."
    return
}

$backupRoot = Join-Path $installRoot ('backup\app-update-v69-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$hadLatest = Test-Path -LiteralPath $latestTarget -PathType Leaf
$hadPublishedApk = Test-Path -LiteralPath $publishedApk -PathType Leaf
if ($hadLatest) { Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $backupRoot 'latest.json') -Force }
if ($hadPublishedApk) { Copy-Item -LiteralPath $publishedApk -Destination (Join-Path $backupRoot $publishedApkName) -Force }

try {
    & $publisher `
        -ApkPath $apkSource `
        -VersionCode $versionCode `
        -VersionName $versionName `
        -Changelog ([string[]]$releaseNotes.changelog) `
        -PublishedAt (Get-Date -Format 'yyyy-MM-dd') `
        -InstallRoot $installRoot

    $localLatest = Read-Utf8Json $latestTarget
    if ([int]$localLatest.versionCode -ne $versionCode -or [string]$localLatest.versionName -cne $versionName -or
        [string]$localLatest.apkUrl -notmatch [regex]::Escape($publishedApkName) -or
        [int64]$localLatest.sizeBytes -ne (Get-Item -LiteralPath $publishedApk).Length -or
        [string]::IsNullOrWhiteSpace([string]$localLatest.sha256)) {
        throw 'Published local APP update manifest does not match the release APK.'
    }

    $publicUri = $PublicLatestUri + $(if ($PublicLatestUri.Contains('?')) { '&' } else { '?' }) + 'release=' + $versionCode
    $deadline = (Get-Date).AddSeconds(45)
    $publicVerified = $false
    $lastPublicError = 'Public update endpoint has not returned the new version yet.'
    do {
        try {
            $publicLatest = Invoke-RestMethod -Uri $publicUri -TimeoutSec 8
            if ([int]$publicLatest.versionCode -eq $versionCode -and [string]$publicLatest.versionName -ceq $versionName) {
                $publicVerified = $true
                break
            }
            $lastPublicError = "Public endpoint still reports versionCode $($publicLatest.versionCode)."
        } catch { $lastPublicError = $_.Exception.Message }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if (-not $publicVerified) { throw "Public APP update verification failed: $lastPublicError" }

    Write-Output "PUBLISHED OK: versionCode=$versionCode versionName=$versionName; backup=$backupRoot"
    Write-Output 'Only app-update files were changed; backend and Caddy were not restarted.'
} catch {
    $failure = $_.Exception.Message
    if ($hadLatest) {
        Copy-Item -LiteralPath (Join-Path $backupRoot 'latest.json') -Destination $latestTarget -Force
    } elseif (Test-Path -LiteralPath $latestTarget) {
        Remove-Item -LiteralPath $latestTarget -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath (Join-Path $backupRoot $publishedApkName) -Destination $publishedApk -Force
    } elseif (Test-Path -LiteralPath $publishedApk) {
        Remove-Item -LiteralPath $publishedApk -Force
    }
    foreach ($temporary in @("$publishedApk.upload", "$latestTarget.upload")) {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue }
    }
    throw "APP update publishing failed; local update files were restored. Cause: $failure"
}
