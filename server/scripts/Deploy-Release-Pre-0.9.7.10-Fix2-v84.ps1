[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $PublicLatestUri = 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$versionCode = 84
$versionName = 'Pre-0.9.7.10-Fix2'
$apkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix2-v84.apk'
$publishedApkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix2.apk'
$chartName = 'Archidoxen_AT_16.9.pez'
$retiredChartName = 'NWAD_IN_15.6.pez'
$apkSource = Join-Path $bundleRoot $apkName
$chartSource = Join-Path $bundleRoot ('practice-charts\' + $chartName)
$notesPath = Join-Path $bundleRoot 'changelog-Pre-0.9.7.10-Fix2.json'
$manifestPath = Join-Path $bundleRoot 'RELEASE_MANIFEST.json'
$publisher = Join-Path $PSScriptRoot 'Publish-AppUpdate.ps1'
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10 -or $installPath -match '^[A-Za-z]:$') { throw 'Invalid InstallRoot.' }
$updateRoot = Join-Path $installPath 'app-update'
$chartDirectory = Join-Path $updateRoot 'practice-charts'
$chartTarget = Join-Path $chartDirectory $chartName
$retiredChartTarget = Join-Path $chartDirectory $retiredChartName
$apkTarget = Join-Path $updateRoot $publishedApkName
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

foreach ($path in @($apkSource, $chartSource, $notesPath, $manifestPath, $publisher)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing release payload: $path" }
}
if (-not (Test-Path -LiteralPath $updateRoot -PathType Container)) { throw "APP update directory not found: $updateRoot" }
foreach ($path in @($bundleRoot, $installPath, $chartDirectory, $retiredChartTarget)) { Assert-PlainPath $path }
$manifest = Read-Json $manifestPath
$notes = Read-Json $notesPath
if ([int]$manifest.versionCode -ne $versionCode -or [string]$manifest.versionName -cne $versionName -or
    [string]$manifest.apkFile -cne $apkName -or [string]$manifest.chartFile -cne $chartName -or
    @($manifest.removes).Count -ne 1 -or
    [string]$manifest.removes[0] -cne 'app-update/practice-charts/NWAD_IN_15.6.pez' -or
    $manifest.publishesAppUpdate -ne $true) { throw 'Release manifest does not match this package.' }
$expectedNotes = @('补全了新曲目的完整信息；', '优化使用体验。')
if ([string]$notes.versionName -cne $versionName -or @($notes.changelog).Count -ne 2 -or
    [string]$notes.changelog[0] -cne $expectedNotes[0] -or
    [string]$notes.changelog[1] -cne $expectedNotes[1]) { throw 'Release changelog does not match the approved text.' }
if ((Get-Item -LiteralPath $apkSource).Length -lt 1048576) { throw 'Release APK is unexpectedly small.' }

$archive = [IO.Compression.ZipFile]::OpenRead($chartSource)
try {
    $entry = $archive.GetEntry('chart.json')
    if (-not $entry) { throw 'Chart archive is missing chart.json.' }
    $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
    try { $chart = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if ([string]$chart.META.id -cne 'official_archidoxen_at' -or
        [string]$chart.META.level -cne 'AT Lv.16.9' -or
        -not $archive.GetEntry([string]$chart.META.song) -or
        -not $archive.GetEntry([string]$chart.META.background)) {
        throw 'Chart metadata or required media does not match Archidoxen AT.'
    }
} finally { $archive.Dispose() }

$oldLatest = if (Test-Path -LiteralPath $latestTarget -PathType Leaf) { Read-Json $latestTarget } else { $null }
if ($oldLatest -and [int]$oldLatest.versionCode -ge $versionCode) {
    throw "Server already publishes versionCode $($oldLatest.versionCode); refusing to replace it."
}
if ($ValidateOnly) {
    Write-Output "VALIDATION OK: $versionName / versionCode $versionCode, Archidoxen AT chart, NWAD retirement, current=$([int]$oldLatest.versionCode); no files changed."
    return
}

$backupRoot = Join-Path $installPath ('backup\fix2-v84-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$targets = @(
    @{ Path = $chartTarget; Name = $chartName },
    @{ Path = $retiredChartTarget; Name = $retiredChartName },
    @{ Path = $apkTarget; Name = $publishedApkName },
    @{ Path = $latestTarget; Name = 'latest.json' }
)
foreach ($target in $targets) {
    if (Test-Path -LiteralPath $target.Path -PathType Leaf) {
        Copy-Item -LiteralPath $target.Path -Destination (Join-Path $backupRoot $target.Name)
    }
}

try {
    New-Item -ItemType Directory -Path $chartDirectory -Force | Out-Null
    Assert-PlainPath $chartDirectory
    $temporaryChart = Join-Path $chartDirectory ($chartName + '.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    Copy-Item -LiteralPath $chartSource -Destination $temporaryChart
    Move-Item -LiteralPath $temporaryChart -Destination $chartTarget -Force
    if (Test-Path -LiteralPath $retiredChartTarget -PathType Leaf) {
        Remove-Item -LiteralPath $retiredChartTarget -Force
    }
    & $publisher -ApkPath $apkSource -VersionCode $versionCode -VersionName $versionName `
        -Changelog ([string[]]$notes.changelog) -PublishedAt (Get-Date -Format 'yyyy-MM-dd') -InstallRoot $installPath
    $published = Read-Json $latestTarget
    if ([int]$published.versionCode -ne $versionCode -or [string]$published.versionName -cne $versionName -or
        [int64]$published.sizeBytes -ne (Get-Item -LiteralPath $apkTarget).Length -or
        @($published.changelog).Count -ne 2) { throw 'Published APP update metadata does not match the package.' }
    $uri = $PublicLatestUri + $(if ($PublicLatestUri.Contains('?')) { '&' } else { '?' }) + 'release=' + $versionCode
    $deadline = (Get-Date).AddSeconds(45)
    $publicOk = $false
    do {
        try {
            $public = Invoke-RestMethod -Uri $uri -TimeoutSec 8
            if ([int]$public.versionCode -eq $versionCode -and [string]$public.versionName -ceq $versionName) {
                $publicOk = $true
                break
            }
        } catch { $lastPublicError = $_.Exception.Message }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if (-not $publicOk) { throw "Public update endpoint did not confirm versionCode $versionCode. $lastPublicError" }
    Write-Output "PUBLISHED OK: $versionName / versionCode $versionCode; backup=$backupRoot"
    Write-Output "Chart: $chartTarget"
    Write-Output "Retired chart: $retiredChartTarget"
} catch {
    $failure = $_.Exception.Message
    if ($temporaryChart -and (Test-Path -LiteralPath $temporaryChart)) {
        Remove-Item -LiteralPath $temporaryChart -Force -ErrorAction SilentlyContinue
    }
    foreach ($target in $targets) {
        $backup = Join-Path $backupRoot $target.Name
        if (Test-Path -LiteralPath $backup -PathType Leaf) {
            Copy-Item -LiteralPath $backup -Destination $target.Path -Force
        } elseif (Test-Path -LiteralPath $target.Path -PathType Leaf) {
            Remove-Item -LiteralPath $target.Path -Force
        }
    }
    throw "Fix2 deployment failed; chart and APP update files were restored. Cause: $failure"
}
