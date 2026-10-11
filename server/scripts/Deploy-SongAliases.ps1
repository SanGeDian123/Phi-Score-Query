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
Add-Type -AssemblyName 'System.IO.Compression', 'System.IO.Compression.FileSystem'
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10 -or $installPath -match '^[A-Za-z]:$') { throw 'Invalid InstallRoot.' }
$backendExe = Join-Path $installPath 'current\backend\phi-backend.exe'
$caddyExe = Join-Path $installPath 'current\caddy\caddy.exe'
$caddyTarget = Join-Path $installPath 'current\caddy\Caddyfile'
$validationCaddy = if ($CaddyExecutable) { $CaddyExecutable } else { $caddyExe }
$installedConfig = if ($CaddyConfigPath) { $CaddyConfigPath } else { $caddyTarget }
if (($CaddyConfigPath -or $CaddyExecutable) -and -not $ValidateOnly) {
    throw 'CaddyExecutable and CaddyConfigPath are only allowed with ValidateOnly.'
}
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
function Add-SongAliasRoute([string] $Text) {
    $aliases = [regex]::Matches($Text, '(?m)^(?<indent>[\t ]*)@songAliasesApi[\t ]+path[\t ]+(?<paths>[^\r\n]+)\r?$')
    if ($aliases.Count -gt 1) { throw 'Multiple existing song alias Caddy matchers found; no files were changed.' }
    if ($aliases.Count -eq 1) {
        $tokens = ($aliases[0].Groups['paths'].Value -split '#', 2)[0].Trim() -split '\s+'
        if ($tokens.Count -ne 1 -or $tokens[0] -ne '/api/v2/admin/songs/aliases') {
            throw 'Existing song alias matcher has unexpected paths; no files were changed.'
        }
        $indentPattern = [regex]::Escape($aliases[0].Groups['indent'].Value)
        $handlers = [regex]::Matches($Text, '(?ms)^' + $indentPattern + 'handle[\t ]+@songAliasesApi[\t ]*\{[^\r\n]*\r?\n(?<body>.*?)^' + $indentPattern + '\}')
        if ($handlers.Count -ne 1 -or $handlers[0].Groups['body'].Value -notmatch '(?m)^[\t ]*reverse_proxy[\t ]+127\.0\.0\.1:3939[\t ]*\{') {
            throw 'Cannot identify the existing song alias proxy handler; no files were changed.'
        }
        $limits = [regex]::Matches($handlers[0].Groups['body'].Value, '(?m)^[\t ]*max_size[\t ]+(?<amount>[0-9]+)(?<unit>KB|MB|GB|B)?[\t ]*\r?$')
        if ($limits.Count -ne 1) { throw 'Cannot identify the existing song alias request limit; no files were changed.' }
        $multiplier = switch ($limits[0].Groups['unit'].Value) { 'KB' { 1000 }; 'MB' { 1000000 }; 'GB' { 1000000000 }; default { 1 } }
        if ([long]$limits[0].Groups['amount'].Value * $multiplier -lt 128000) {
            throw 'Existing song alias request limit is smaller than 128KB; no files were changed.'
        }
        return $Text
    }
    if ($Text -match '(?m)^[\t ]*handle[\t ]+@songAliasesApi[\t ]*\{') { throw 'Existing song alias handler has no recognized matcher; no files were changed.' }
    $anchor = [regex]::Matches($Text, '(?m)^(?<indent>[\t ]*)@adminConsoleApi[\t ]+path[\t ]+[^\r\n]+\r?$')
    if ($anchor.Count -ne 1) { throw 'Cannot identify the existing admin console Caddy path matcher; no files were changed.' }
    $indent = $anchor[0].Groups['indent'].Value
    $newline = if ($Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $lines = @(
        '@songAliasesApi path /api/v2/admin/songs/aliases',
        'handle @songAliasesApi {',
        "`trequest_body {",
        "`t`tmax_size 128KB",
        "`t}",
        "`treverse_proxy 127.0.0.1:3939 {",
        "`t`theader_up -X-Exchange-Secret",
        "`t`theader_up -X-OpenApi-Token",
        "`t}",
        '}'
    )
    $block = (($lines | ForEach-Object { $indent + $_ }) -join $newline) + $newline + $newline
    return $Text.Insert($anchor[0].Index, $block)
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
foreach ($required in @('backend\phi-backend.exe', 'source\backend-source-SongAliases-20261011.zip',
    'admin-console\psq-admin-web\components\song-alias-panel.tsx', 'admin-console\psq-admin-web\Start-AdminWeb.ps1',
    'SERVER_ONLY_MANIFEST.json')) {
    $path = Join-Path $bundleRoot $required
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path).Length -eq 0) {
        throw "Missing or empty package file: $required"
    }
}
if (Test-Path -LiteralPath (Join-Path $bundleRoot 'backend\info')) { throw 'A runtime catalog payload is not allowed in this package.' }
$forbidden = @(Get-ChildItem -LiteralPath $bundleRoot -Recurse -File -Force | Where-Object {
    $_.Extension -ieq '.apk' -or $_.Name -ieq 'latest.json' -or $_.Name -ieq 'Publish-AppUpdate.ps1' -or
    $_.FullName -match '[\\/](node_modules|target|\.git|\.wrangler|\.next|dist)[\\/]' -or
    $_.Name -match '^\.env|^config\.toml$|^secrets\.|\.db($|-)|\.sqlite($|-)|^song-alias-overrides\.json($|\.)'
})
if ($forbidden.Count) { throw 'Package contains APP updates, private configuration, runtime data or build caches.' }
$manifest = [IO.File]::ReadAllText((Join-Path $bundleRoot 'SERVER_ONLY_MANIFEST.json')) | ConvertFrom-Json
if ($manifest.kind -ne 'server-only' -or $manifest.includesApk -ne $false -or
    $manifest.publishesAppUpdate -ne $false -or $manifest.modifiesAppUpdate -ne $false) { throw 'Invalid server-only manifest.' }
$binaryStream = [IO.File]::OpenRead((Join-Path $bundleRoot 'backend\phi-backend.exe'))
try {
    if ($binaryStream.Length -lt 1024 -or $binaryStream.ReadByte() -ne 77 -or $binaryStream.ReadByte() -ne 90) {
        throw 'Invalid backend executable.'
    }
} finally { $binaryStream.Dispose() }
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $bundleRoot 'source\backend-source-SongAliases-20261011.zip'))
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(^|/)(\.env[^/]*|config\.toml|secrets\.[^/]*|latest\.json|Publish-AppUpdate\.ps1|song-alias-overrides\.json[^/]*)$|\.apk$|\.db($|-)|\.sqlite($|-)|(^|/)(target|node_modules|\.git)/') {
            throw "Forbidden source entry: $($entry.FullName)"
        }
    }
    foreach ($required in @('src/features/song/aliases.rs', 'src/startup/song_loader.rs', 'src/startup/catalog_sync.rs', 'Cargo.toml', 'Cargo.lock')) {
        if (-not $archive.GetEntry($required)) { throw "Missing corresponding source: $required" }
    }
} finally { $archive.Dispose() }
foreach ($path in @($validationCaddy, $installedConfig)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing Caddy executable or configuration: $path" }
}
$existingCaddy = [IO.File]::ReadAllText($installedConfig, [Text.Encoding]::UTF8)
$candidate = Add-SongAliasRoute $existingCaddy
foreach ($pair in @(@('APP_LOG_DIR','logs'), @('APP_UPDATE_DIR','app-update'),
    @('APP_ANNOUNCEMENT_DIR','app-announcement'), @('APP_AVATAR_DIR','avatar'), @('APP_SOURCE_DIR','source'))) {
    [Environment]::SetEnvironmentVariable($pair[0], ((Join-Path $installPath $pair[1]) -replace '\\','/'), 'Process')
}
# Syntax adaptation from stdin is read-only and does not start Caddy or provision listeners.
$adapted = $candidate | & $validationCaddy adapt --config - --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw 'Patched Caddy configuration could not be parsed.' }
$null = ($adapted -join "`n") | ConvertFrom-Json
$sourceMatches = [regex]::Matches($candidate, '(?m)^[\t ]*rewrite[\t ]+\*[\t ]+/(?<name>backend-source-[^/\\\s]+\.zip)[\t ]*\r?$')
if ($sourceMatches.Count -ne 1) { throw 'Cannot identify the installed backend source download filename.' }
$files = @(
    @{ Source = 'backend\phi-backend.exe'; Target = $backendExe },
    @{ Source = 'source\backend-source-SongAliases-20261011.zip'; Target = (Join-Path $installPath ('source\' + $sourceMatches[0].Groups['name'].Value)) }
)
foreach ($file in $files) { Assert-PlainPath $file.Target }
Assert-PlainPath $caddyTarget
if ($ValidateOnly) {
    Write-Output 'VALIDATION OK: server-only package, corresponding source and Caddy syntax. No server files changed.'
    return
}
foreach ($path in @($backendExe, $caddyExe, $caddyTarget)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Installed server file missing: $path" }
}
foreach ($task in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    Get-ScheduledTask -TaskName $task -ErrorAction Stop | Out-Null
}
$backup = Join-Path $installPath ('backup\song-aliases-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
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
    if ($candidate -cne $existingCaddy) { [IO.File]::WriteAllText($caddyTarget, $candidate, $utf8) }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Health $BackendOrigin
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Wait-Health $PublicOrigin
    foreach ($origin in @($BackendOrigin, $PublicOrigin)) {
        $code = Status ($origin.TrimEnd('/') + '/api/v2/admin/songs/aliases')
        if ($code -ne 401) { throw "Song alias route did not reach authentication ($code): $origin" }
    }
    Write-Output "DEPLOYED OK. Backup: $backup"
    Write-Output 'Run the included admin-console separately on your computer. Existing catalog, alias data, databases and APP updates were preserved.'
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
