[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [string] $BackendTaskName = 'PhigrosScore-Backend',
    [string] $CaddyTaskName = 'PhigrosScore-Caddy',
    [string] $BackendOrigin = 'http://127.0.0.1:3939',
    [string] $PublicOrigin = 'https://api.plc-liangpi-cup.xyz',
    [string] $CaddyExecutable,
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.IO.Compression', 'System.IO.Compression.FileSystem'
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
$utf8 = [Text.UTF8Encoding]::new($false)
if ($installPath.Length -lt 10 -or $installPath -eq [IO.Path]::GetPathRoot($installPath).TrimEnd('\') -or
    $installPath -eq $bundleRoot -or $bundleRoot.StartsWith($installPath + '\', [StringComparison]::OrdinalIgnoreCase) -or
    $installPath.StartsWith($bundleRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Extract the package outside the server installation and specify a valid InstallRoot.'
}
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
function Target([string] $Relative) {
    $path = [IO.Path]::GetFullPath((Join-Path $installPath $Relative))
    if (-not $path.StartsWith($installPath + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Target escapes InstallRoot.' }
    Assert-PlainPath $path
    return $path
}
function Assert-DistributionName([string] $Relative) {
    if ($Relative -match '(^|/)(\.\.|app-update|node_modules|target|\.git|\.runtime)(/|$)' -or
        $Relative -match '(^|/)(\.env(?!\.example$)[^/]*|config\.toml|secrets\.[^/]*|latest\.json|Publish-AppUpdate\.ps1|cookie\.key)$|\.apk$|\.(db|sqlite)(-|$)|\.log$') {
        throw "Forbidden package file: $Relative"
    }
}
function Status([string] $Uri, [switch] $Post, [string] $Body = '{}') {
    try {
        if ($Post) { return [int](Invoke-WebRequest -Uri $Uri -Method Post -ContentType 'application/json' -Body $Body -UseBasicParsing -TimeoutSec 5).StatusCode }
        return [int](Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5).StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}
function Wait-Health([string] $Origin) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        if ((Status ($Origin.TrimEnd('/') + '/health')) -eq 200) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Health check failed: $Origin"
}
function Stop-AppTask([string] $Name, [string] $Executable) {
    if ((Get-ScheduledTask -TaskName $Name -ErrorAction Stop).State -eq 'Running') { Stop-ScheduledTask -TaskName $Name -ErrorAction Stop }
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $processes = @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath -ieq $Executable })
        foreach ($process in $processes) {
            try { Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop }
            catch { if (Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue) { throw } }
        }
        if ((Get-ScheduledTask -TaskName $Name).State -ne 'Running' -and $processes.Count -eq 0) { return }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "Timed out stopping $Name."
}

Assert-PlainPath $bundleRoot
Assert-PlainPath $installPath
$manifest = Get-Content -LiteralPath (Join-Path $bundleRoot 'SERVER_ONLY_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.package -ne 'server-only-upgrade-Pre-0.9.8.0-RksTrend-20261025' -or $manifest.kind -ne 'server-only' -or $manifest.appVersion -ne 'Pre-0.9.8.0' -or
    $manifest.includesApk -ne $false -or $manifest.publishesAppUpdate -ne $false -or $manifest.modifiesAppUpdate -ne $false) {
    throw 'This is not the expected server-only RKS trend package.'
}
foreach ($file in (Get-ChildItem -LiteralPath $bundleRoot -Recurse -File -Force)) {
    Assert-PlainPath $file.FullName
    Assert-DistributionName ($file.FullName.Substring($bundleRoot.Length + 1).Replace('\', '/'))
}
$sourceRelative = 'source\backend-source-Pre-0.9.8.0-RksTrend.zip'
foreach ($relative in @('backend\phi-backend.exe', $sourceRelative,
    'backend\resources\difficulty_20250214.csv', 'backend\resources\templates\image\bn\phi_plugin_assets\legacy_phigros_icon.png',
    'backend\resources\templates\image\bn\minimal.svg.jinja', 'backend\resources\templates\image\bn\minimal.json')) {
    $path = Join-Path $bundleRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path).Length -eq 0) { throw "Missing payload: $relative" }
}
$binary = [IO.File]::OpenRead((Join-Path $bundleRoot 'backend\phi-backend.exe'))
try { if ($binary.Length -lt 1024 -or $binary.ReadByte() -ne 77 -or $binary.ReadByte() -ne 90) { throw 'Invalid backend executable.' } }
finally { $binary.Dispose() }
$source = [IO.Compression.ZipFile]::OpenRead((Join-Path $bundleRoot $sourceRelative))
try {
    foreach ($entry in $source.Entries) { Assert-DistributionName $entry.FullName }
    foreach ($required in @('Cargo.toml', 'Cargo.lock', 'src/features/image/handler/bn.rs',
        'src/features/image/types.rs', 'src/features/image/handler/time_rewind.rs', 'src/features/image/renderer/phi_plugin_legacy.rs',
        'resources/difficulty_20250214.csv', 'resources/templates/image/bn/minimal.svg.jinja',
        'src/features/rks/handler.rs', 'src/features/stats/storage/submission.rs')) {
        if (-not $source.GetEntry($required)) { throw "Missing corresponding source: $required" }
    }
} finally { $source.Dispose() }
$backendExe = Target 'current\backend\phi-backend.exe'
$caddyExe = Target 'current\caddy\caddy.exe'
$caddyTarget = Target 'current\caddy\Caddyfile'
$validationCaddy = if ($CaddyExecutable) { $CaddyExecutable } else { $caddyExe }
foreach ($path in @($backendExe, $caddyTarget, $validationCaddy)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing installed server file: $path" }
}
$candidate = [IO.File]::ReadAllText($caddyTarget, [Text.Encoding]::UTF8)
function Add-AllowedRoute([string] $Config, [string] $Route) {
    $pattern = '(?m)^[\t ]*' + [regex]::Escape($Route) + '[\t ]*(?:\\[\t ]*)?\r?$'
    if ($Config -match $pattern) { return $Config }
    $anchors = [regex]::Matches($Config, '(?m)^(?<indent>[\t ]*)/api/v2/image/bn[\t ]*(?:\\[\t ]*)?\r?$')
    if ($anchors.Count -ne 1) { throw 'Cannot identify the existing image/bn allowlist line; no server files were changed.' }
    $newline = if ($Config.Contains("`r`n")) { "`r`n" } else { "`n" }
    $anchor = $anchors[0]
    return $Config.Insert($anchor.Index, ($anchor.Groups['indent'].Value + $Route + ' \' + $newline))
}
$candidate = Add-AllowedRoute $candidate '/api/v2/image/bestn'
$candidate = Add-AllowedRoute $candidate '/api/v2/rks/history'
$sourceNames = [regex]::Matches($candidate, '(?m)^[\t ]*rewrite[\t ]+\*[\t ]+/(?<name>backend-source-[^/\\\s]+\.zip)[\t ]*\r?$')
if ($sourceNames.Count -ne 1) { throw 'Cannot identify the installed /source archive filename.' }
foreach ($pair in @(@('APP_LOG_DIR','logs'), @('APP_UPDATE_DIR','app-update'),
    @('APP_ANNOUNCEMENT_DIR','app-announcement'), @('APP_AVATAR_DIR','avatar'), @('APP_SOURCE_DIR','source'))) {
    [Environment]::SetEnvironmentVariable($pair[0], ((Join-Path $installPath $pair[1]) -replace '\\','/'), 'Process')
}
$savedOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = $utf8
    $adapted = $candidate | & $validationCaddy adapt --config - --adapter caddyfile
    if ($LASTEXITCODE -ne 0) { throw 'Patched Caddy configuration could not be parsed.' }
    $null = ($adapted -join "`n") | ConvertFrom-Json
} finally { $OutputEncoding = $savedOutputEncoding }
$files = @()
foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $bundleRoot 'backend') -Recurse -File)) {
    $relative = $file.FullName.Substring($bundleRoot.Length + 1)
    $destination = Target ('current\' + $relative)
    # Keep any existing administrator-customized layout parameters.
    if ($file.Extension -ieq '.json' -and (Test-Path -LiteralPath $destination -PathType Leaf)) { continue }
    $files += @{ Source = $file.FullName; Relative = $relative; Target = $destination }
}
$files += @{ Source = (Join-Path $bundleRoot $sourceRelative); Relative = $sourceRelative;
    Target = (Target ('source\' + $sourceNames[0].Groups['name'].Value)) }
if ($ValidateOnly) {
    Write-Output 'VALIDATION OK: backend, matching source, templates and patched Caddy syntax. No server files changed; no APP update published.'
    return
}
foreach ($name in @($BackendTaskName, $CaddyTaskName)) { Get-ScheduledTask -TaskName $name -ErrorAction Stop | Out-Null }
if (-not (Test-Path -LiteralPath $caddyExe -PathType Leaf)) { throw 'Missing installed Caddy executable.' }
$backup = Target ('backup\rks-trend-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$states = @()
foreach ($file in ($files + @(@{ Source = ''; Relative = 'caddy\Caddyfile'; Target = $caddyTarget }))) {
    $saved = Join-Path $backup $file.Relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    $existed = Test-Path -LiteralPath $file.Target -PathType Leaf
    if ($existed) { Copy-Item -LiteralPath $file.Target -Destination $saved -Force }
    $states += @{ Target = $file.Target; Saved = $saved; Existed = $existed }
}
$changed = $false
try {
    Stop-AppTask $CaddyTaskName $caddyExe
    Stop-AppTask $BackendTaskName $backendExe
    $changed = $true
    foreach ($file in $files) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $file.Target) -Force | Out-Null
        Copy-Item -LiteralPath $file.Source -Destination $file.Target -Force
    }
    [IO.File]::WriteAllText($caddyTarget, $candidate, $utf8)
    Start-ScheduledTask -TaskName $BackendTaskName
    Wait-Health $BackendOrigin
    Start-ScheduledTask -TaskName $CaddyTaskName
    Wait-Health $PublicOrigin
    foreach ($origin in @($BackendOrigin, $PublicOrigin)) {
        foreach ($route in @('/api/v2/image/bestn?time_rewind=true', '/api/v2/image/p30?time_rewind=true', '/api/v2/image/bestn?width=4096')) {
            $status = Status ($origin.TrimEnd('/') + $route) -Post
            if ($status -ne 422) { throw "Time rewind route did not reject the unsupported ranking ($status): $origin$route" }
        }
        $historyStatus = Status ($origin.TrimEnd('/') + '/api/v2/rks/history') -Post -Body '{"auth":{},"limit":1}'
        if ($historyStatus -ne 401) { throw "RKS history did not reach the authenticated backend (expected 401, got $historyStatus): $origin" }
    }
    Write-Output "DEPLOYED OK. Backup: $backup"
    Write-Output 'APP update manifests, APKs, announcements, databases, catalog and private configuration were not modified.'
} catch {
    $failure = $_.Exception.Message
    try {
        Stop-AppTask $CaddyTaskName $caddyExe
        Stop-AppTask $BackendTaskName $backendExe
        if ($changed) {
            foreach ($state in $states) {
                if ($state.Existed) { Copy-Item -LiteralPath $state.Saved -Destination $state.Target -Force }
                elseif (Test-Path -LiteralPath $state.Target -PathType Leaf) { Remove-Item -LiteralPath $state.Target -Force }
            }
        }
        Start-ScheduledTask -TaskName $BackendTaskName
        Wait-Health $BackendOrigin
        Start-ScheduledTask -TaskName $CaddyTaskName
        Wait-Health $PublicOrigin
    } catch { throw "Upgrade failed: $failure. Recovery failed: $($_.Exception.Message). Backup: $backup" }
    throw "Upgrade failed; previous files restored: $failure. Backup: $backup"
}
