[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly
)
$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path -Parent $PSScriptRoot
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10) { throw 'Invalid InstallRoot.' }
$current = Join-Path $installPath 'current'
$backendExe = Join-Path $current 'backend\phi-backend.exe'
$files = @(
    @{ Source = 'backend\phi-backend.exe'; Target = $backendExe },
    @{ Source = 'source\backend-source-Pre-0.9.7.9.zip'; Target = (Join-Path $installPath 'source\backend-source-Pre-0.9.7.9.zip') }
)

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
function Status([string] $Uri) {
    try { return [int](Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10).StatusCode }
    catch { if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }; return 0 }
}
function Wait-Health {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        if ((Status 'http://127.0.0.1:3939/health') -eq 200) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Backend did not become healthy within 60 seconds.'
}
function Stop-AppTask([string] $Name, [string] $Exe) {
    Stop-ScheduledTask -TaskName $Name -ErrorAction Stop
    $deadline = (Get-Date).AddSeconds(30)
    do {
        # Task Scheduler may leave a child process behind. Match only the exact installed executable.
        $children = @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath -ieq $Exe })
        foreach ($child in $children) {
            try {
                Stop-Process -Id $child.ProcessId -Force -ErrorAction Stop
            } catch {
                # The scheduled task's child can exit between enumeration and Stop-Process.
                # Treat an already-gone process as a successful stop; preserve real failures.
                if (Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue) { throw }
            }
        }
        if ((Get-ScheduledTask -TaskName $Name).State -ne 'Running' -and $children.Count -eq 0) { return }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "Timed out stopping $Name."
}

Assert-PlainPath $installPath
foreach ($file in $files) {
    $source = Join-Path $bundleRoot $file.Source
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing package file: $source" }
    if ((Get-Item -LiteralPath $source).Length -eq 0) { throw "Empty package file: $source" }
    Assert-PlainPath $file.Target
}
$forbidden = @(Get-ChildItem -LiteralPath $bundleRoot -Recurse -File | Where-Object { $_.Extension -ieq '.apk' -or $_.Name -ieq 'latest.json' -or $_.Name -ieq 'Publish-AppUpdate.ps1' })
if ($forbidden.Count) { throw 'Package contains forbidden APP update files.' }
$exeBytes = [IO.File]::ReadAllBytes((Join-Path $bundleRoot 'backend\phi-backend.exe'))
if ($exeBytes.Length -lt 1024 -or $exeBytes[0] -ne 77 -or $exeBytes[1] -ne 90) { throw 'Invalid backend executable.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $bundleRoot 'source\backend-source-Pre-0.9.7.9.zip'))
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(^|/)(\.env[^/]*|latest\.json|Publish-AppUpdate\.ps1)$|\.apk$|\.db($|-)' ) { throw "Forbidden source entry: $($entry.FullName)" }
    }
    foreach ($required in @('src/features/checkin.rs','src/features/media_preview.rs','src/features/rks_guess/mod.rs','Cargo.toml','Cargo.lock')) {
        if (-not $archive.GetEntry($required)) { throw "Missing source entry: $required" }
    }
} finally { $archive.Dispose() }
if ($ValidateOnly) {
    Write-Output 'Server package structure, executable and source archive are valid. No server files changed.'
    return
}
foreach ($target in @($backendExe)) {
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Installed server file missing: $target" }
}
foreach ($task in @('PhigrosScore-Backend')) { Get-ScheduledTask -TaskName $task -ErrorAction Stop | Out-Null }

$backup = Join-Path $installPath ('backup\media-performance-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$states = @()
foreach ($file in $files) {
    $saved = Join-Path $backup $file.Source
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    $existed = Test-Path -LiteralPath $file.Target
    if ($existed) { Copy-Item -LiteralPath $file.Target -Destination $saved -Force }
    $states += @{ Target = $file.Target; Saved = $saved; Existed = $existed }
}
$changed = $false
try {
    Stop-AppTask 'PhigrosScore-Backend' $backendExe
    $changed = $true
    foreach ($file in $files) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $file.Target) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $bundleRoot $file.Source) -Destination $file.Target -Force
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Health
    foreach ($route in @('/api/v2/checkin','/api/v2/games/rks-guess/leaderboard','/api/v2/feedback/mine','/api/v2/feedback/notifications','/api/v2/feedback/updates','/api/v2/admin/feedback')) {
        $code = Status ('http://127.0.0.1:3939' + $route)
        if ($code -ne 401) { throw "Backend authentication probe failed ($code): $route" }
    }
    Start-Sleep -Seconds 3
    if ((Status 'http://127.0.0.1:3939/health') -ne 200) { throw 'Backend exited after startup.' }
    $deadline = (Get-Date).AddSeconds(30)
    do {
        if ((Status 'https://api.plc-liangpi-cup.xyz/health') -eq 200) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ((Status 'https://api.plc-liangpi-cup.xyz/health') -ne 200) { throw 'Public backend health check failed.' }
    foreach ($route in @('/api/v2/checkin','/api/v2/games/rks-guess/leaderboard','/api/v2/feedback/mine','/api/v2/feedback/notifications','/api/v2/feedback/updates','/api/v2/admin/feedback')) {
        $code = Status ('https://api.plc-liangpi-cup.xyz' + $route)
        if ($code -ne 401) { throw "Public authentication probe failed ($code): $route" }
    }
    Write-Output "Media performance backend deployed. Backup: $backup"
    Write-Output 'APP update files were not touched.'
} catch {
    $original = $_.Exception.Message
    try {
        Stop-AppTask 'PhigrosScore-Backend' $backendExe
        if ($changed) {
            foreach ($state in $states) {
                if ($state.Existed) { Copy-Item -LiteralPath $state.Saved -Destination $state.Target -Force }
                elseif (Test-Path -LiteralPath $state.Target) { Remove-Item -LiteralPath $state.Target -Force }
            }
        }
        Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
        Wait-Health
    } catch { throw "Deployment failed: $original. Automatic recovery failed: $($_.Exception.Message). Backup: $backup" }
    throw "Deployment failed and server files were restored: $original. Backup: $backup"
}
