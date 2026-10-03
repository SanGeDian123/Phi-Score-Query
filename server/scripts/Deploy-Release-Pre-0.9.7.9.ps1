[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $CaddyExecutable
)
$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path -Parent $PSScriptRoot
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10) { throw 'Invalid InstallRoot.' }
$current = Join-Path $installPath 'current'
$backendExe = Join-Path $current 'backend\phi-backend.exe'
$caddyExe = Join-Path $current 'caddy\caddy.exe'
$validationCaddy = if ($CaddyExecutable) { $CaddyExecutable } else { $caddyExe }
$apkName = 'Phi Score Query Pre-0.9.7.9.apk'
$apkRelative = 'app-update\releases\53\' + $apkName
$latestTarget = Join-Path $installPath 'app-update\latest.json'
$files = @(
    @{ Source = 'backend\phi-backend.exe'; Target = $backendExe },
    @{ Source = 'caddy\Caddyfile'; Target = (Join-Path $current 'caddy\Caddyfile') },
    @{ Source = 'source\backend-source-Pre-0.9.7.9.zip'; Target = (Join-Path $installPath 'source\backend-source-Pre-0.9.7.9.zip') },
    @{ Source = $apkName; Target = (Join-Path $installPath $apkRelative) },
    @{ Source = 'latest.json'; Target = $latestTarget }
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
$metadata = Get-Content -LiteralPath (Join-Path $bundleRoot 'latest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedUrl = 'https://api.plc-liangpi-cup.xyz/app-update/releases/53/' + [Uri]::EscapeDataString($apkName)
$apkSource = Join-Path $bundleRoot $apkName
if ($metadata.versionCode -ne 53 -or $metadata.versionName -cne 'Pre-0.9.7.9' -or
    $metadata.apkUrl -cne $expectedUrl -or $metadata.mandatory -ne $false -or
    @($metadata.changelog).Count -eq 0) { throw 'Invalid release metadata.' }
if ($metadata.sizeBytes -ne (Get-Item -LiteralPath $apkSource).Length) { throw 'APK size mismatch.' }
# This digest is required by the existing APP update protocol, not a package-wide hash audit.
$apkHash = (Get-FileHash -LiteralPath $apkSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($metadata.sha256 -cne $apkHash) { throw 'APK update digest mismatch.' }
if (Test-Path -LiteralPath $latestTarget) {
    $installedMetadata = Get-Content -LiteralPath $latestTarget -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([int]$installedMetadata.versionCode -gt 53) { throw 'Refusing to replace a newer APP update.' }
}
if (-not (Test-Path -LiteralPath $validationCaddy -PathType Leaf)) { throw 'Caddy not found. Supply -CaddyExecutable for package validation.' }
foreach ($pair in @(@('APP_LOG_DIR','logs'),@('APP_UPDATE_DIR','app-update'),@('APP_ANNOUNCEMENT_DIR','app-announcement'),@('APP_AVATAR_DIR','avatar'),@('APP_SOURCE_DIR','source'))) {
    [Environment]::SetEnvironmentVariable($pair[0], ((Join-Path $installPath $pair[1]) -replace '\\','/'), 'Process')
}
& $validationCaddy validate --config (Join-Path $bundleRoot 'caddy\Caddyfile') --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'Caddy configuration validation failed.' }
if ($ValidateOnly) {
    Write-Output 'Release package, APK update metadata and Caddy configuration are valid. No services stopped or installed files replaced.'
    return
}
foreach ($target in @($backendExe,$caddyExe,(Join-Path $current 'caddy\Caddyfile'))) {
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Installed server file missing: $target" }
}
foreach ($task in @('PhigrosScore-Backend','PhigrosScore-Caddy')) { Get-ScheduledTask -TaskName $task -ErrorAction Stop | Out-Null }

$backup = Join-Path $installPath ('backup\release-v53-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
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
    Stop-AppTask 'PhigrosScore-Caddy' $caddyExe
    Stop-AppTask 'PhigrosScore-Backend' $backendExe
    $changed = $true
    foreach ($file in $files) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $file.Target) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $bundleRoot $file.Source) -Destination $file.Target -Force
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Health
    foreach ($route in @('/api/v2/suggestions/random','/api/v2/games/rks-guess/leaderboard','/api/v2/feedback/mine','/api/v2/feedback/notifications','/api/v2/feedback/updates','/api/v2/admin/feedback')) {
        $code = Status ('http://127.0.0.1:3939' + $route)
        if ($code -ne 401) { throw "Feedback authentication probe failed ($code): $route" }
    }
    Start-Sleep -Seconds 3
    if ((Status 'http://127.0.0.1:3939/health') -ne 200) { throw 'Backend exited after startup.' }
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    $deadline = (Get-Date).AddSeconds(30)
    do {
        if ((Status 'https://api.plc-liangpi-cup.xyz/health') -eq 200) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ((Status 'https://api.plc-liangpi-cup.xyz/health') -ne 200) { throw 'Public health check failed.' }
    foreach ($route in @('/api/v2/suggestions/random','/api/v2/games/rks-guess/leaderboard','/api/v2/feedback/mine','/api/v2/feedback/notifications','/api/v2/feedback/updates','/api/v2/admin/feedback')) {
        $code = Status ('https://api.plc-liangpi-cup.xyz' + $route)
        if ($code -ne 401) { throw "Public feedback route probe failed ($code): $route" }
    }
    $publicMetadata = Invoke-RestMethod -Uri 'https://api.plc-liangpi-cup.xyz/app-update/latest.json?v=53' -TimeoutSec 15
    if ($publicMetadata.versionCode -ne 53 -or $publicMetadata.apkUrl -cne $expectedUrl -or
        $publicMetadata.sha256 -cne $apkHash -or $publicMetadata.sizeBytes -ne $metadata.sizeBytes) {
        throw 'Public APP update metadata does not match this release.'
    }
    $head = Invoke-WebRequest -Uri $expectedUrl -Method Head -UseBasicParsing -TimeoutSec 15
    if ($head.StatusCode -ne 200 -or [long]$head.Headers['Content-Length'] -ne $metadata.sizeBytes) {
        throw 'Public APK download probe failed.'
    }
    Write-Output "Backend and APP update v53 deployed. Backup: $backup"
    Write-Output "APK: $expectedUrl"
    Write-Output 'No separate announcement or admin website was published.'
} catch {
    $original = $_.Exception.Message
    try {
        Stop-AppTask 'PhigrosScore-Caddy' $caddyExe
        Stop-AppTask 'PhigrosScore-Backend' $backendExe
        if ($changed) {
            foreach ($state in $states) {
                if ($state.Existed) { Copy-Item -LiteralPath $state.Saved -Destination $state.Target -Force }
                elseif (Test-Path -LiteralPath $state.Target) { Remove-Item -LiteralPath $state.Target -Force }
            }
        }
        Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
        Wait-Health
        Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    } catch { throw "Deployment failed: $original. Automatic recovery failed: $($_.Exception.Message). Backup: $backup" }
    throw "Deployment failed and server files were restored: $original. Backup: $backup"
}
