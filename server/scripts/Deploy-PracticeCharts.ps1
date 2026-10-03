[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifestPath = Join-Path $bundleRoot 'SERVER_RESOURCE_MANIFEST.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Missing resource manifest.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.kind -ne 'server-static-resource-only' -or $manifest.publishesAppUpdate -ne $false -or
    $manifest.modifiesLatestJson -ne $false -or $manifest.includesApk -ne $false) {
    throw 'Manifest is not a resource-only practice chart package.'
}
$fileNames = @($manifest.payload | ForEach-Object {
    $entry = [string]$_
    if ($entry -cnotmatch '^practice-charts/[A-Za-z0-9_.-]+\.pez$') { throw "Invalid chart path: $entry" }
    $entry.Substring('practice-charts/'.Length)
})
if ($fileNames.Count -eq 0 -or ($fileNames | Select-Object -Unique).Count -ne $fileNames.Count) {
    throw 'Manifest must list unique chart files.'
}

function Assert-PlainPath([string] $Path) {
    $part = [IO.Path]::GetFullPath($Path)
    while ($part) {
        if (Test-Path -LiteralPath $part) {
            if (((Get-Item -LiteralPath $part -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Junction or symbolic link is not allowed in deployment path: $part"
            }
        }
        $parent = Split-Path -Parent $part
        if ($parent -eq $part) { break }
        $part = $parent
    }
}

$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10) { throw 'Invalid InstallRoot.' }
$targetDirectory = Join-Path $installPath 'app-update\practice-charts'
Assert-PlainPath $installPath
Assert-PlainPath $targetDirectory

$forbidden = @(Get-ChildItem -LiteralPath $bundleRoot -Recurse -File | Where-Object {
    $_.Extension -ieq '.apk' -or $_.Name -ieq 'latest.json' -or $_.Name -ieq 'Publish-AppUpdate.ps1'
})
if ($forbidden.Count -gt 0) { throw 'This package must not contain APP update payloads.' }

foreach ($fileName in $fileNames) {
    $packageChart = Join-Path (Join-Path $bundleRoot 'practice-charts') $fileName
    if (-not (Test-Path -LiteralPath $packageChart -PathType Leaf)) { throw "Missing chart payload: $fileName" }
    if ((Get-Item -LiteralPath $packageChart).Length -lt 1024) { throw "Chart payload is unexpectedly small: $fileName" }
    $archive = [IO.Compression.ZipFile]::OpenRead($packageChart)
    try {
        $chartEntry = $archive.GetEntry('chart.json')
        if (-not $chartEntry) { throw "Chart archive is missing chart.json: $fileName" }
        $reader = New-Object IO.StreamReader($chartEntry.Open())
        try { $chart = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        $song = [string]$chart.META.song
        $background = [string]$chart.META.background
        if (-not $song -or -not $background -or -not $archive.GetEntry($song) -or -not $archive.GetEntry($background)) {
            throw "Chart archive is missing its music or illustration: $fileName"
        }
    } finally { $archive.Dispose() }
}

if ($ValidateOnly) {
    Write-Output ("Practice chart package valid: " + ($fileNames -join ', ') + '. No server files were changed.')
    return
}

New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
Assert-PlainPath $targetDirectory
$backupDirectory = Join-Path $installPath ('backup\practice-charts-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
foreach ($fileName in $fileNames) {
    $packageChart = Join-Path (Join-Path $bundleRoot 'practice-charts') $fileName
    $targetFile = Join-Path $targetDirectory $fileName
    $temporaryTarget = Join-Path $targetDirectory ($fileName + '.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    $backupPath = $null
    try {
        if (Test-Path -LiteralPath $targetFile -PathType Leaf) {
            New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
            $backupPath = Join-Path $backupDirectory $fileName
            Copy-Item -LiteralPath $targetFile -Destination $backupPath
        }
        Copy-Item -LiteralPath $packageChart -Destination $temporaryTarget
        Move-Item -LiteralPath $temporaryTarget -Destination $targetFile -Force
        Write-Output "Practice chart deployed to $targetFile"
        if ($backupPath) { Write-Output "Previous chart backup: $backupPath" }
    } catch {
        if (Test-Path -LiteralPath $temporaryTarget) { Remove-Item -LiteralPath $temporaryTarget -Force }
        if ($backupPath -and (Test-Path -LiteralPath $backupPath)) {
            Copy-Item -LiteralPath $backupPath -Destination $targetFile -Force
        }
        throw
    }
}
Write-Output 'No application update manifest, APK, backend, or Caddy file was changed.'
