[CmdletBinding()]
param(
    [string] $OutputRoot = 'D:\PhigrosAppBuild',
    [string] $CargoTargetDir = 'D:\PhiAdminWebCargoTarget',
    [switch] $SkipBuild
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.IO.Compression', 'System.IO.Compression.FileSystem'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$backend = Join-Path $repo 'backend-source'
$name = 'server-only-upgrade-Pre-0.9.7.13-TimeRewind-P1Fix-20261024'
$stage = Join-Path $OutputRoot ($name + '-stage')
$zip = Join-Path $OutputRoot ($name + '.zip')
foreach ($path in @($stage, $zip)) { if (Test-Path -LiteralPath $path) { throw "Delivery already exists: $path" } }
if (-not $SkipBuild) {
    $env:CARGO_TARGET_DIR = $CargoTargetDir
    Push-Location $backend
    try {
        & cargo build --release --locked --offline --bin phi-backend
        if ($LASTEXITCODE -ne 0) { throw 'Backend release build failed.' }
    } finally { Pop-Location }
}
$binary = Join-Path $CargoTargetDir 'release\phi-backend.exe'
if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw 'Missing backend release binary.' }
foreach ($relative in @('backend\resources', 'scripts', 'source')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $relative) -Force | Out-Null
}
Copy-Item -LiteralPath $binary -Destination (Join-Path $stage 'backend\phi-backend.exe')
Copy-Item -LiteralPath (Join-Path $backend 'resources\difficulty_20250214.csv') -Destination (Join-Path $stage 'backend\resources\difficulty_20250214.csv')
foreach ($tree in @('fonts', 'templates')) {
    Copy-Item -LiteralPath (Join-Path $backend ('resources\' + $tree)) -Destination (Join-Path $stage 'backend\resources') -Recurse
}
Copy-Item -LiteralPath (Join-Path $repo 'app\src\main\assets\licenses\OFL-1.1.txt') -Destination (Join-Path $stage 'backend\resources\fonts\OFL-1.1.txt')
$bom = [Text.UTF8Encoding]::new($true)
$utf8 = [Text.UTF8Encoding]::new($false)
$deployName = 'Deploy-TimeRewind-Pre-0.9.7.13.ps1'
[IO.File]::WriteAllText((Join-Path $stage ('scripts\' + $deployName)), [IO.File]::ReadAllText((Join-Path $PSScriptRoot $deployName)), $bom)
Copy-Item -LiteralPath (Join-Path $repo 'server\SERVER-TimeRewind-Pre-0.9.7.13-20261024.md') -Destination (Join-Path $stage 'README.md')
foreach ($file in @('THIRD_PARTY_NOTICES.md', 'SOURCE_OFFER.md')) { Copy-Item -LiteralPath (Join-Path $repo $file) -Destination $stage }
Copy-Item -LiteralPath (Join-Path $backend 'LICENSE') -Destination (Join-Path $stage 'LICENSE')
function Relative-Name([string] $Base, [string] $Path) {
    return $Path.Substring($Base.TrimEnd([char[]]'\/').Length + 1).Replace([IO.Path]::DirectorySeparatorChar, [char]'/')
}
function Assert-DistributionName([string] $Relative) {
    if ($Relative -match '(^|/)(\.\.|app-update|node_modules|target|\.git|\.runtime|__pycache__)(/|$)' -or
        $Relative -match '(^|/)(\.env(?!\.example$)[^/]*|config\.toml|secrets\.[^/]*|latest\.json|Publish-AppUpdate\.ps1|cookie\.key|song-alias-overrides\.json)$|\.apk$|\.(db|sqlite)(-|$)|\.(log|tmp|bak)$') {
        throw "Non-distribution file: $Relative"
    }
}
$sourceFiles = @()
foreach ($tree in @('src', 'crates', 'info', 'resources\fonts', 'resources\templates', 'tests', 'examples')) {
    $sourceFiles += @(Get-ChildItem -LiteralPath (Join-Path $backend $tree) -Recurse -File -Force | Where-Object {
        $_.FullName -notmatch '[\\/](target|node_modules|\.git|__pycache__)[\\/]'
    })
}
foreach ($file in @('Cargo.toml', 'Cargo.lock', 'config.example.toml', 'LICENSE', 'MODIFICATIONS.md', 'resources\difficulty_20250214.csv')) {
    $sourceFiles += Get-Item -LiteralPath (Join-Path $backend $file)
}
$sourceRelative = 'source\backend-source-Pre-0.9.7.13-TimeRewind-P1Fix.zip'
$archive = [IO.Compression.ZipFile]::Open((Join-Path $stage $sourceRelative), [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in ($sourceFiles | Sort-Object FullName -Unique)) {
        $relative = Relative-Name $backend $file.FullName
        Assert-DistributionName $relative
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative, [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
    foreach ($relative in @('server\scripts\Build-TimeRewindPackage-Pre-0.9.7.13.ps1', 'server\scripts\Deploy-TimeRewind-Pre-0.9.7.13.ps1',
        'server\SERVER-TimeRewind-Pre-0.9.7.13-20261024.md', 'server\caddy\Caddyfile')) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $repo $relative), $relative.Replace('\','/'), [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $archive.Dispose() }
$manifest = [ordered]@{
    package = $name; kind = 'server-only'; createdAt = '2026-10-24'
    appVersion = 'Pre-0.9.7.13'; compatibleVersionCode = 109
    includesApk = $false; publishesAppUpdate = $false; modifiesAppUpdate = $false
    updates = @('Stable P1 selection shared with Android, including tied constants', 'Desultory Signals IN 16.4 and Exoplanetary Mirage IN 16.3 in time rewind', 'p1-v2 response contract and cache invalidation', '2025-02-14 constants for 327 songs and 1037 charts', 'Time rewind uses P1 plus Best 19 and isolated image caches', 'Legacy Phi-Plugin B19 layout with PSQ entertainment watermark', 'Best N and P30 are rejected in time rewind mode', 'Preserve current Best N support outside time rewind')
    payload = @('backend/phi-backend.exe', 'backend/resources/difficulty_20250214.csv', 'backend/resources/fonts', 'backend/resources/templates')
    preserves = @('APP updates and announcements', 'databases and private configuration', 'runtime catalog, aliases, artwork and charts', 'existing template layout JSON', 'PWA and admin-console')
    source = $sourceRelative.Replace('\','/'); deployed = $false
}
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_ONLY_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
$bundle = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in (Get-ChildItem -LiteralPath $stage -Recurse -File -Force | Sort-Object FullName)) {
        $relative = Relative-Name $stage $file.FullName
        Assert-DistributionName $relative
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, $file.FullName, $relative, [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $bundle.Dispose() }
Write-Output "Package: $zip"
Write-Output "Stage: $stage"
