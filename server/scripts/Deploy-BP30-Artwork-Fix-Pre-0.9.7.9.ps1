[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path -Parent $PSScriptRoot
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 10) { throw 'InstallRoot path is invalid.' }

$manifestPath = Join-Path $bundleRoot 'MANIFEST.json'
$currentRoot = Join-Path $installRoot 'current'
$backendRoot = Join-Path $currentRoot 'backend'
$illustrationRoot = Join-Path $backendRoot 'resources\ill'
$backendExecutable = Join-Path $backendRoot 'phi-backend.exe'
$latestPath = Join-Path $installRoot 'app-update\latest.json'

function Get-FileSnapshot([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{ Exists = $false; Hash = $null }
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.PSIsContainer) { throw "Expected file but found directory: $Path" }
    [pscustomobject]@{
        Exists = $true
        Hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Assert-SnapshotUnchanged([string] $Name, $Before, [string] $Path) {
    $after = Get-FileSnapshot $Path
    if ([bool] $Before.Exists -ne [bool] $after.Exists -or
        ($Before.Exists -and [string] $Before.Hash -ne [string] $after.Hash)) {
        throw "$Name changed unexpectedly: $Path"
    }
}

function Assert-NormalDirectory([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required directory is missing: $Path" }
    $item = Get-Item -LiteralPath $Path -Force
    if (-not $item.PSIsContainer) { throw "Expected directory but found file: $Path" }
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Directory cannot be a junction or symbolic link: $Path"
    }
}

function Wait-AppTaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "Timed out waiting for task to stop: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

function Wait-BackendHealth([int] $TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:3939/health' -UseBasicParsing -TimeoutSec 8
            if ([int] $response.StatusCode -eq 200) { return }
        } catch {
            if ((Get-Date) -ge $deadline) { throw 'Backend health check timed out.' }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Backend health check timed out.'
}

function Assert-Png([string] $Path) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 8 -or
        $bytes[0] -ne 0x89 -or $bytes[1] -ne 0x50 -or $bytes[2] -ne 0x4E -or
        $bytes[3] -ne 0x47 -or $bytes[4] -ne 0x0D -or $bytes[5] -ne 0x0A -or
        $bytes[6] -ne 0x1A -or $bytes[7] -ne 0x0A) {
        throw "Invalid PNG payload: $Path"
    }
}

if (-not (Test-Path -LiteralPath $manifestPath)) { throw 'MANIFEST.json is missing.' }
$manifest = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
if ([string] $manifest.packageType -ne 'bp30-artwork-resource-only' -or
    [string] $manifest.appVersion -ne 'Pre-0.9.7.9' -or
    [bool] $manifest.publishesAppUpdate) {
    throw 'Package manifest does not describe the expected resource-only fix.'
}

$payloadFiles = @($manifest.files)
if ($payloadFiles.Count -ne 6) { throw "Expected exactly 6 artwork files, found $($payloadFiles.Count)." }
$expectedIds = @('NWAD.Knighthood', 'DevastatingHistory.NAMV')
$expectedKinds = @('ill', 'illLow', 'illBlur')
$resolvedPayload = @()
foreach ($entry in $payloadFiles) {
    $relative = [string] $entry.path
    $parts = $relative -split '/'
    if ($parts.Count -ne 4 -or $parts[0] -ne 'resources' -or $parts[1] -ne 'ill' -or
        $parts[2] -notin $expectedKinds -or
        [IO.Path]::GetFileNameWithoutExtension($parts[3]) -notin $expectedIds -or
        [IO.Path]::GetExtension($parts[3]) -ne '.png') {
        throw "Unexpected artwork path in manifest: $relative"
    }
    $source = Join-Path $bundleRoot ($relative -replace '/', '\')
    if (-not (Test-Path -LiteralPath $source)) { throw "Payload file is missing: $relative" }
    Assert-Png $source
    $actualHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne ([string] $entry.sha256).ToLowerInvariant()) {
        throw "Payload SHA-256 mismatch: $relative"
    }
    $targetRelative = ($relative -replace '^resources/ill/', '') -replace '/', '\'
    $resolvedPayload += [pscustomobject]@{
        Relative = $relative
        Source = $source
        Target = Join-Path $illustrationRoot $targetRelative
        Sha256 = $actualHash
    }
}

$actualBundleFiles = @(Get-ChildItem -LiteralPath $bundleRoot -File -Recurse | ForEach-Object {
    $_.FullName.Substring($bundleRoot.Length + 1).Replace('\', '/')
})
$allowedBundleFiles = @('MANIFEST.json', 'README-DEPLOY.txt', 'scripts/Deploy-BP30-Artwork-Fix-Pre-0.9.7.9.ps1') + @($payloadFiles | ForEach-Object { [string] $_.path })
$unexpected = @($actualBundleFiles | Where-Object { $_ -notin $allowedBundleFiles })
$missing = @($allowedBundleFiles | Where-Object { $_ -notin $actualBundleFiles })
if ($unexpected.Count -gt 0 -or $missing.Count -gt 0) {
    throw "Package contents mismatch. Unexpected=$($unexpected -join ', '); Missing=$($missing -join ', ')"
}

if (-not (Test-Path -LiteralPath $backendExecutable)) { throw "Backend executable is missing: $backendExecutable" }
foreach ($kind in $expectedKinds) { Assert-NormalDirectory (Join-Path $illustrationRoot $kind) }
if (-not (Get-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue)) {
    throw 'Scheduled task PhigrosScore-Backend is missing.'
}

if ($ValidateOnly) {
    Write-Output 'Validation passed: 6 PNG assets and target server layout are valid.'
    Write-Output 'This package contains no APK, backend executable, catalog, Caddy configuration, or APP update publisher.'
    return
}

$latestBefore = Get-FileSnapshot $latestPath
$backendBefore = Get-FileSnapshot $backendExecutable
$backupRoot = Join-Path $installRoot ('backup\bp30-artwork-fix-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$states = @()
$backupReady = $false

try {
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    foreach ($file in $resolvedPayload) {
        $existed = Test-Path -LiteralPath $file.Target
        $backupPath = Join-Path $backupRoot (($file.Relative -replace '^resources/ill/', '') -replace '/', '\')
        if ($existed) {
            New-Item -ItemType Directory -Path (Split-Path -Parent $backupPath) -Force | Out-Null
            Copy-Item -LiteralPath $file.Target -Destination $backupPath -Force
        }
        $states += [pscustomobject]@{ Target = $file.Target; Existed = $existed; Backup = $backupPath }
    }
    $backupReady = $true

    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-AppTaskStopped 'PhigrosScore-Backend'

    foreach ($file in $resolvedPayload) {
        Copy-Item -LiteralPath $file.Source -Destination $file.Target -Force
        $installedHash = (Get-FileHash -LiteralPath $file.Target -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($installedHash -ne $file.Sha256) { throw "Installed SHA-256 mismatch: $($file.Target)" }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-BackendHealth
    Start-Sleep -Seconds 5
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Backend').State -ne 'Running') {
        throw 'Backend task did not remain running.'
    }

    foreach ($file in $resolvedPayload) {
        $publicRelative = ($file.Relative -replace '^resources/ill/', '')
        $uri = 'https://api.plc-liangpi-cup.xyz/_ill/' + $publicRelative
        $response = Invoke-WebRequest -Method Head -Uri $uri -UseBasicParsing -TimeoutSec 20
        if ([int] $response.StatusCode -ne 200) { throw "Public artwork probe failed: $uri" }
    }

    Assert-SnapshotUnchanged 'APP update manifest' $latestBefore $latestPath
    Assert-SnapshotUnchanged 'Backend executable' $backendBefore $backendExecutable
    Write-Output 'BP30 artwork resource fix deployed successfully.'
    Write-Output "Backup: $backupRoot"
    Write-Output 'Six artwork files are public; APP update metadata and backend executable are unchanged.'
} catch {
    $original = $_.Exception.Message
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-AppTaskStopped 'PhigrosScore-Backend'
    if ($backupReady) {
        foreach ($state in $states) {
            if ($state.Existed) {
                Copy-Item -LiteralPath $state.Backup -Destination $state.Target -Force
            } elseif (Test-Path -LiteralPath $state.Target) {
                Remove-Item -LiteralPath $state.Target -Force
            }
        }
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-BackendHealth
    Assert-SnapshotUnchanged 'APP update manifest during rollback' $latestBefore $latestPath
    Assert-SnapshotUnchanged 'Backend executable during rollback' $backendBefore $backendExecutable
    throw "BP30 artwork deployment failed and artwork files were rolled back: $original"
}
