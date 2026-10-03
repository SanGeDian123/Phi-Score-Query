[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $PublicLatestUri = 'https://api.plc-liangpi-cup.xyz/app-update/latest.json',
    [ValidateRange(1, 120)] [int] $PublicVerificationTimeoutSeconds = 45
)
$ErrorActionPreference = 'Stop'
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10 -or $installPath -match '^[A-Za-z]:$') { throw 'Invalid InstallRoot.' }
$updateRoot = Join-Path $installPath 'app-update'
$apkName = 'Phi_Score_Query_Pre-0.9.7.11-Fix.apk'
$apkSource = Join-Path $bundleRoot ('app-update\' + $apkName)
$latestSource = Join-Path $bundleRoot 'app-update\latest.json'
$apkTarget = Join-Path $updateRoot $apkName
$latestTarget = Join-Path $updateRoot 'latest.json'

function Read-Json([string] $Path) {
    return [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}
function Assert-PlainPath([string] $Path) {
    $part = [IO.Path]::GetFullPath($Path)
    while ($part) {
        if (Test-Path -LiteralPath $part) {
            if (((Get-Item -LiteralPath $part -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Junction or symbolic link is not allowed: $part"
            }
        }
        $parent = Split-Path -Parent $part
        if ($parent -eq $part) { break }
        $part = $parent
    }
}
function Copy-Atomically([string] $Source, [string] $Target) {
    $temporary = $Target + '.upload-' + [Guid]::NewGuid().ToString('N')
    $replaceBackup = $Target + '.replace-backup-' + [Guid]::NewGuid().ToString('N')
    try {
        Copy-Item -LiteralPath $Source -Destination $temporary -ErrorAction Stop
        for ($attempt = 1; $attempt -le 20; $attempt++) {
            try {
                if (Test-Path -LiteralPath $Target -PathType Leaf) {
                    [IO.File]::Replace($temporary, $Target, $replaceBackup, $true)
                } else { [IO.File]::Move($temporary, $Target) }
                return
            } catch {
                if ($attempt -eq 20) { throw }
                Start-Sleep -Milliseconds 500
            }
        }
    } finally {
        foreach ($temporaryPath in @($temporary, $replaceBackup)) {
            if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
                Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
            }
        }
    }
}
function Assert-Release($Value, $Expected) {
    if ([int]$Value.versionCode -ne 98 -or [string]$Value.versionName -cne 'Pre-0.9.7.11-Fix' -or
        [string]$Value.apkUrl -cne [string]$Expected.apkUrl -or
        [string]$Value.sha256 -cne [string]$Expected.sha256 -or
        [int64]$Value.sizeBytes -ne [int64]$Expected.sizeBytes -or $Value.mandatory -ne $false -or
        (@($Value.changelog) -join "`n") -cne (@($Expected.changelog) -join "`n")) {
        throw 'Published update metadata does not match version 98.'
    }
}

foreach ($path in @($installPath, $updateRoot, $apkTarget, $latestTarget)) { Assert-PlainPath $path }
if (-not (Test-Path -LiteralPath $updateRoot -PathType Container)) { throw "APP update directory not found: $updateRoot" }
foreach ($path in @($apkSource, $latestSource, (Join-Path $bundleRoot 'RELEASE_MANIFEST.json'))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing package file: $path" }
}
foreach ($path in @($apkTarget, $latestTarget)) {
    if ((Test-Path -LiteralPath $path) -and -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Deployment target is not a file: $path"
    }
}
$release = Read-Json $latestSource
$package = Read-Json (Join-Path $bundleRoot 'RELEASE_MANIFEST.json')
if ([int]$package.versionCode -ne 98 -or [string]$package.versionName -cne 'Pre-0.9.7.11-Fix' -or
    [string]$package.apkFile -cne $apkName -or $package.publishesAppUpdate -ne $true) { throw 'Package release manifest mismatch.' }
if ([string]$release.apkUrl -cne ('https://api.plc-liangpi-cup.xyz/app-update/' + $apkName) -or
    [string]$release.sha256 -notmatch '^[0-9a-f]{64}$' -or @($release.changelog).Count -eq 0) { throw 'Invalid update manifest.' }
Assert-Release $release $release
if ((Get-Item -LiteralPath $apkSource).Length -ne [int64]$release.sizeBytes -or
    (Get-FileHash -LiteralPath $apkSource -Algorithm SHA256).Hash.ToLowerInvariant() -cne [string]$release.sha256) {
    throw 'APK does not match the update manifest.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apkSource)
try {
    if (-not $archive.GetEntry('AndroidManifest.xml') -or -not $archive.GetEntry('classes.dex')) { throw 'Invalid APK archive.' }
} finally { $archive.Dispose() }
$oldLatest = if (Test-Path -LiteralPath $latestTarget -PathType Leaf) { Read-Json $latestTarget } else { $null }
if ($oldLatest -and [int]$oldLatest.versionCode -ge 98) {
    throw "Server already publishes versionCode $($oldLatest.versionCode); refusing to replace it with 98."
}
if ($ValidateOnly) {
    Write-Output "VALIDATION OK: Pre-0.9.7.11-Fix / 98; current=$([int]$oldLatest.versionCode); no server files changed."
    return
}

$backupRoot = Join-Path $installPath ('backup\app-update-v98-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
Assert-PlainPath $backupRoot
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$states = @(
    @{ Source=$apkSource; Target=$apkTarget; Saved=(Join-Path $backupRoot $apkName); Existed=$false; Changed=$false },
    @{ Source=$latestSource; Target=$latestTarget; Saved=(Join-Path $backupRoot 'latest.json'); Existed=$false; Changed=$false }
)
foreach ($state in $states) {
    $state.Existed = Test-Path -LiteralPath $state.Target -PathType Leaf
    if ($state.Existed) { Copy-Item -LiteralPath $state.Target -Destination $state.Saved }
}
try {
    foreach ($state in $states) {
        Copy-Atomically $state.Source $state.Target
        $state.Changed = $true
    }
    Assert-Release (Read-Json $latestTarget) $release
    $separator = if ($PublicLatestUri.Contains('?')) { '&' } else { '?' }
    $probeUri = $PublicLatestUri + $separator + 'release=98&probe=' + [Guid]::NewGuid().ToString('N')
    $deadline = (Get-Date).AddSeconds($PublicVerificationTimeoutSeconds)
    $publicOk = $false
    $lastPublicError = 'Public release did not become available.'
    do {
        try {
            # Windows PowerShell 5.1 may decode JSON without a charset as Latin-1.
            $response = Invoke-WebRequest -Uri $probeUri -UseBasicParsing -TimeoutSec 8 -Headers @{ 'Cache-Control'='no-cache' }
            $response.RawContentStream.Position = 0
            $reader = [IO.StreamReader]::new($response.RawContentStream, [Text.Encoding]::UTF8)
            try { $public = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            Assert-Release $public $release
            $apkProbe = New-Object UriBuilder($PublicLatestUri)
            $apkProbe.Path = $apkProbe.Path.Substring(0, $apkProbe.Path.LastIndexOf('/') + 1) + $apkName
            $apkProbe.Query = 'release=98'
            $head = Invoke-WebRequest -Uri $apkProbe.Uri.AbsoluteUri -Method Head -UseBasicParsing -TimeoutSec 8
            if ([int]$head.StatusCode -ne 200 -or [int64]$head.Headers['Content-Length'] -ne [int64]$release.sizeBytes) {
                throw 'Public APK size or response status does not match the release.'
            }
            $publicOk = $true
            break
        } catch { $lastPublicError = $_.Exception.Message }
        if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
    } while ((Get-Date) -lt $deadline)
    if (-not $publicOk) { throw "Public release verification failed: $lastPublicError" }
    Write-Output "PUBLISHED OK: Pre-0.9.7.11-Fix / versionCode 98; mandatory=false; backup=$backupRoot"
    Write-Output "APK: $apkTarget"
} catch {
    $failure = $_.Exception.Message
    try {
        foreach ($state in $states) {
            if (-not $state.Changed) { continue }
            if ($state.Existed) { Copy-Atomically $state.Saved $state.Target }
            elseif (Test-Path -LiteralPath $state.Target -PathType Leaf) { Remove-Item -LiteralPath $state.Target -Force }
        }
    } catch { throw "Publishing failed: $failure. Automatic restoration failed: $($_.Exception.Message). Backup: $backupRoot" }
    throw "Publishing failed; previous local release was restored. Cause: $failure. Backup: $backupRoot"
}
