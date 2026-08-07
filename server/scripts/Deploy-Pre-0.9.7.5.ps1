[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path $PSScriptRoot -Parent
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
$appUpdateRoot = Join-Path $installRoot 'app-update'
$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$releaseNotesPath = Join-Path $bundleRoot 'changelog-Pre-0.9.7.5.json'
$apkName = 'Phi-Score-Query-Pre-0.9.7.5.apk'
$apkSource = Join-Path $bundleRoot $apkName
$publishScript = Join-Path $bundleRoot 'scripts\Publish-AppUpdate.ps1'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApk = Join-Path $appUpdateRoot $apkName

function Read-Utf8Json([string] $Path) {
    [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}

foreach ($required in @($manifestPath, $releaseNotesPath, $apkSource, $publishScript)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing package file: $required" }
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

$backupRoot = Join-Path $installRoot ('backup\app-update-pre-0.9.7.5-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$backupUpdateRoot = Join-Path $backupRoot 'app-update'
New-Item -ItemType Directory -Force -Path $backupUpdateRoot | Out-Null
$hadLatest = Test-Path -LiteralPath $latestTarget
$hadNewApk = Test-Path -LiteralPath $publishedApk
$backupReady = $false

try {
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

    & $publishScript `
        -ApkPath $apkSource `
        -VersionCode 33 `
        -VersionName 'Pre-0.9.7.5' `
        -PublishedAt '2026-08-07' `
        -InstallRoot $installRoot `
        -Changelog $changelog

    $latest = Read-Utf8Json $latestTarget
    $expectedHash = [string] $manifest.files.$apkName
    $actualHash = (Get-FileHash -LiteralPath $publishedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 33 -or [string] $latest.versionName -ne 'Pre-0.9.7.5') {
        throw 'Published manifest version mismatch.'
    }
    if ([string] $latest.sha256 -ne $expectedHash -or $actualHash -ne $expectedHash) {
        throw 'Published APK SHA-256 mismatch.'
    }

    Write-Output 'Pre-0.9.7.5 app update published.'
    Write-Output "Backup: $backupRoot"
    Write-Output "SHA256: $actualHash"
    Write-Output 'Backend and Caddy were not restarted.'
} catch {
    if ($backupReady) {
        if (Test-Path -LiteralPath $latestTarget) { Remove-Item -LiteralPath $latestTarget -Force }
        if ($hadLatest) {
            Copy-Item -LiteralPath (Join-Path $backupUpdateRoot 'latest.json') -Destination $latestTarget -Force
        }
        if (Test-Path -LiteralPath $publishedApk) { Remove-Item -LiteralPath $publishedApk -Force }
        if ($hadNewApk) {
            Copy-Item -LiteralPath (Join-Path $backupUpdateRoot ('preexisting-' + $apkName)) -Destination $publishedApk -Force
        }
    }
    throw "Pre-0.9.7.5 app update failed and was rolled back. Original error: $($_.Exception.Message)"
}
