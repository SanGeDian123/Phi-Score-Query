[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $CaddyExecutable,
    [string] $CaddyConfigPath,
    [string] $BackendOrigin = 'http://127.0.0.1:3939',
    [string] $PublicOrigin = 'https://api.plc-liangpi-cup.xyz'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'UserModeration-Caddy.ps1')
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10 -or $installPath -match '^[A-Za-z]:$') { throw 'Invalid InstallRoot.' }
$backendExe = Join-Path $installPath 'current\backend\phi-backend.exe'
$caddyExe = Join-Path $installPath 'current\caddy\caddy.exe'
$caddyTarget = Join-Path $installPath 'current\caddy\Caddyfile'
$validationCaddy = if ($CaddyExecutable) { $CaddyExecutable } else { $caddyExe }
$installedConfig = if ($CaddyConfigPath) { $CaddyConfigPath } else { $caddyTarget }
if ($CaddyConfigPath -and -not $ValidateOnly) { throw 'CaddyConfigPath is only allowed with ValidateOnly.' }
$utf8 = [Text.UTF8Encoding]::new($false)

function Assert-PlainPath([string] $Path) {
    $part = [IO.Path]::GetFullPath($Path)
    while ($part) {
        if ((Test-Path -LiteralPath $part) -and
            (((Get-Item -LiteralPath $part -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Junction or symbolic link is not allowed: $part"
        }
        $parent = Split-Path -Parent $part
        if ($parent -eq $part) { break }
        $part = $parent
    }
}
function Status([string] $Uri) {
    try { return [int](Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5).StatusCode }
    catch { if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }; return 0 }
}
function Wait-Health([string] $Origin) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        if ((Status ($Origin.TrimEnd('/') + '/health')) -eq 200) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Backend health did not recover: $Origin"
}
function Stop-AppTask([string] $Name, [string] $Exe) {
    Stop-ScheduledTask -TaskName $Name -ErrorAction Stop
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $children = @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath -ieq $Exe })
        foreach ($child in $children) {
            try { Stop-Process -Id $child.ProcessId -Force -ErrorAction Stop }
            catch { if (Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue) { throw } }
        }
        if ((Get-ScheduledTask -TaskName $Name).State -ne 'Running' -and $children.Count -eq 0) { return }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "Timed out stopping $Name."
}

Assert-PlainPath $installPath
foreach ($required in @('backend\phi-backend.exe', 'caddy\UserModeration.caddy',
    'source\backend-source-Pre-0.9.7.9.zip', 'admin-console\psq-admin-web\components\user-management.tsx')) {
    $path = Join-Path $bundleRoot $required
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path).Length -eq 0) {
        throw "Missing or empty package file: $required"
    }
}
$forbidden = @(Get-ChildItem -LiteralPath $bundleRoot -Recurse -File -Force | Where-Object {
    $_.Extension -ieq '.apk' -or $_.Name -ieq 'latest.json' -or $_.Name -ieq 'Publish-AppUpdate.ps1' -or
    $_.Name -match '^\.env|^config\.toml$|^secrets\.|\.db($|-)'
})
if ($forbidden.Count) { throw 'Package contains APP update files, private configuration or user data.' }
$binaryStream = [IO.File]::OpenRead((Join-Path $bundleRoot 'backend\phi-backend.exe'))
try {
    if ($binaryStream.Length -lt 1024 -or $binaryStream.ReadByte() -ne 77 -or $binaryStream.ReadByte() -ne 90) {
        throw 'Invalid backend executable.'
    }
} finally { $binaryStream.Dispose() }
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $bundleRoot 'source\backend-source-Pre-0.9.7.9.zip'))
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(^|/)(\.env[^/]*|config\.toml|secrets\.[^/]*|latest\.json|Publish-AppUpdate\.ps1)$|\.apk$|\.db($|-)') {
            throw "Forbidden source entry: $($entry.FullName)"
        }
    }
    foreach ($required in @('src/features/user_moderation.rs', 'src/features/stats/storage/moderation.rs', 'Cargo.toml', 'Cargo.lock')) {
        if (-not $archive.GetEntry($required)) { throw "Missing corresponding source: $required" }
    }
} finally { $archive.Dispose() }
foreach ($path in @($validationCaddy, $installedConfig)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing Caddy executable or configuration: $path" }
}
$candidate = Get-UserModerationCaddy ([IO.File]::ReadAllText($installedConfig, [Text.Encoding]::UTF8)) `
    ([IO.File]::ReadAllText((Join-Path $bundleRoot 'caddy\UserModeration.caddy'), [Text.Encoding]::UTF8))
foreach ($pair in @(@('APP_LOG_DIR','logs'), @('APP_UPDATE_DIR','app-update'),
    @('APP_ANNOUNCEMENT_DIR','app-announcement'), @('APP_AVATAR_DIR','avatar'), @('APP_SOURCE_DIR','source'))) {
    [Environment]::SetEnvironmentVariable($pair[0], ((Join-Path $installPath $pair[1]) -replace '\\','/'), 'Process')
}
# Adapt from stdin without provisioning listeners or writing server files.
$adapted = $candidate | & $validationCaddy adapt --config - --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'Patched Caddy configuration could not be parsed.' }
$null = ($adapted -join "`n") | ConvertFrom-Json
$sourceMatches = [regex]::Matches($candidate, '(?m)^[\t ]*rewrite[\t ]+\*[\t ]+/(?<name>backend-source-[^/\\\s]+\.zip)[\t ]*$')
if ($sourceMatches.Count -ne 1) { throw 'Cannot identify the installed backend source download filename.' }
$files = @(
    @{ Source = 'backend\phi-backend.exe'; Target = $backendExe },
    @{ Source = 'source\backend-source-Pre-0.9.7.9.zip'; Target = (Join-Path $installPath ('source\' + $sourceMatches[0].Groups['name'].Value)) }
)
foreach ($file in $files) { Assert-PlainPath $file.Target }
Assert-PlainPath $caddyTarget
if ($ValidateOnly) {
    Write-Output 'VALIDATION OK: package, source and patched Caddy syntax. No server files changed.'
    return
}
foreach ($path in @($backendExe, $caddyExe, $caddyTarget)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Installed server file missing: $path" }
}
foreach ($task in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    Get-ScheduledTask -TaskName $task -ErrorAction Stop | Out-Null
}
$backup = Join-Path $installPath ('backup\user-management-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
Assert-PlainPath $backup
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$states = @()
foreach ($file in ($files + @(@{ Source = 'caddy\Caddyfile'; Target = $caddyTarget }))) {
    $saved = Join-Path $backup $file.Source
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    $existed = Test-Path -LiteralPath $file.Target -PathType Leaf
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
    [IO.File]::WriteAllText($caddyTarget, $candidate, $utf8)
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Health $BackendOrigin
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Wait-Health $PublicOrigin
    foreach ($origin in @($BackendOrigin, $PublicOrigin)) {
        foreach ($route in @('/api/v2/admin/users/management/search?query=PSQDeployProbe', '/api/v2/admin/users/management/appeals?status=pending')) {
            $code = Status ($origin.TrimEnd('/') + $route)
            if ($code -ne 401) { throw "User management route did not reach authentication ($code): $route" }
        }
    }
    Write-Output "DEPLOYED OK. Backup: $backup"
    Write-Output 'Update the separate admin-console to expose its user management UI. APP update files were not touched.'
} catch {
    $original = $_.Exception.Message
    try {
        Stop-AppTask 'PhigrosScore-Caddy' $caddyExe
        Stop-AppTask 'PhigrosScore-Backend' $backendExe
        if ($changed) {
            foreach ($state in $states) {
                if ($state.Existed) { Copy-Item -LiteralPath $state.Saved -Destination $state.Target -Force }
                elseif (Test-Path -LiteralPath $state.Target -PathType Leaf) { Remove-Item -LiteralPath $state.Target -Force }
            }
        }
        Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
        Wait-Health $BackendOrigin
        Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
        Wait-Health $PublicOrigin
    } catch { throw "Deployment failed: $original. Recovery failed: $($_.Exception.Message). Backup: $backup" }
    throw "Deployment failed; server files restored: $original. Backup: $backup"
}
