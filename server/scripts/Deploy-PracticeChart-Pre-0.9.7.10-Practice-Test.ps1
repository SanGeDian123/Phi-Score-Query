[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$packageChart = Join-Path $bundleRoot 'practice-charts\ExoplanetaryMirage_AT_17.9.pez'
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10) { throw 'Invalid InstallRoot.' }

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

if (-not (Test-Path -LiteralPath $packageChart -PathType Leaf)) { throw "Missing chart payload: $packageChart" }
if ((Get-Item -LiteralPath $packageChart).Length -lt 1024) { throw 'Chart payload is unexpectedly small.' }
$chartArchive = [IO.Compression.ZipFile]::OpenRead($packageChart)
try {
    foreach ($required in @('chart.json', 'music.wav', 'illustration.png', 'info.txt')) {
        if (-not $chartArchive.GetEntry($required)) { throw "Chart archive is missing $required" }
    }
} finally { $chartArchive.Dispose() }

$targetDirectory = Join-Path $installPath 'app-update\practice-charts'
$targetFile = Join-Path $targetDirectory 'ExoplanetaryMirage_AT_17.9.pez'
Assert-PlainPath $installPath
Assert-PlainPath $targetDirectory
$forbidden = @(Get-ChildItem -LiteralPath $bundleRoot -Recurse -File | Where-Object {
    $_.Extension -ieq '.apk' -or $_.Name -ieq 'latest.json' -or $_.Name -ieq 'Publish-AppUpdate.ps1'
})
if ($forbidden.Count -gt 0) { throw 'This resource package must not contain APP update payloads.' }

if ($ValidateOnly) {
    Write-Output 'Practice chart package is valid. No server files were changed.'
    return
}

$backupPath = $null
$temporaryTarget = Join-Path $targetDirectory ('ExoplanetaryMirage_AT_17.9.' + [Guid]::NewGuid().ToString('N') + '.tmp')
New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
try {
    Assert-PlainPath $targetDirectory
    if (Test-Path -LiteralPath $targetFile -PathType Leaf) {
        $backupDirectory = Join-Path $installPath ('backup\practice-chart-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
        New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
        $backupPath = Join-Path $backupDirectory 'ExoplanetaryMirage_AT_17.9.pez'
        Copy-Item -LiteralPath $targetFile -Destination $backupPath
    }
    Copy-Item -LiteralPath $packageChart -Destination $temporaryTarget
    Move-Item -LiteralPath $temporaryTarget -Destination $targetFile -Force
    Write-Output "Practice chart deployed to $targetFile"
    if ($backupPath) { Write-Output "Previous chart backup: $backupPath" }
    Write-Output 'No application update manifest, APK, backend, or Caddy file was changed.'
} catch {
    if (Test-Path -LiteralPath $temporaryTarget) { Remove-Item -LiteralPath $temporaryTarget -Force }
    if ($backupPath -and (Test-Path -LiteralPath $backupPath)) {
        Copy-Item -LiteralPath $backupPath -Destination $targetFile -Force
    }
    throw
}
