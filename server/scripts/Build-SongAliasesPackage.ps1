[CmdletBinding()]
param(
    [string] $OutputRoot = 'D:\PhigrosAppBuild\SongAliases-20261011',
    [string] $CargoTargetDir = 'D:\PhiAdminWebCargoTarget',
    [switch] $SkipBuild,
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.IO.Compression', 'System.IO.Compression.FileSystem'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-only-upgrade-SongAliases-20261011'
$stage = Join-Path $OutputRoot ($name + '-stage')
$zip = Join-Path $OutputRoot ($name + '.zip')
$desktopZip = Join-Path ([Environment]::GetFolderPath('Desktop')) ($name + '.zip')
foreach ($path in @($stage, $zip, $desktopZip)) {
    if (Test-Path -LiteralPath $path) { throw "Delivery already exists: $path" }
}
if (-not $SkipBuild) {
    $env:CARGO_TARGET_DIR = $CargoTargetDir
    $arguments = @('build', '--release', '--locked', '--bin', 'phi-backend')
    if ($Offline) { $arguments += '--offline' }
    Push-Location (Join-Path $repo 'backend-source')
    try {
        & cargo @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Backend release build failed.' }
    } finally { Pop-Location }
}
$binary = Join-Path $CargoTargetDir 'release\phi-backend.exe'
if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw 'Missing built backend.' }
$backendBase = Join-Path $repo 'backend-source'
$compiledInputs = @(
    Get-ChildItem -LiteralPath (Join-Path $backendBase 'src') -Recurse -File -Filter '*.rs'
    Get-ChildItem -LiteralPath (Join-Path $backendBase 'crates') -Recurse -File | Where-Object {
        $_.FullName -notmatch '[\\/](target|\.git)[\\/]' -and ($_.Extension -eq '.rs' -or $_.Name -eq 'Cargo.toml')
    }
    Get-Item -LiteralPath (Join-Path $backendBase 'Cargo.toml'), (Join-Path $backendBase 'Cargo.lock'), (Join-Path $backendBase 'info\avatar.txt')
)
$latestInput = $compiledInputs | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if ((Get-Item -LiteralPath $binary).LastWriteTimeUtc -lt $latestInput.LastWriteTimeUtc) {
    throw "Built backend is older than its source: $($latestInput.FullName). Build again before packaging."
}

function Selected-Files([string] $Base, [string[]] $Trees, [string[]] $Files) {
    $selected = @()
    foreach ($tree in $Trees) {
        $selected += @(Get-ChildItem -LiteralPath (Join-Path $Base $tree) -Recurse -File -Force | Where-Object {
            $_.FullName -notmatch '[\\/](node_modules|target|\.git|\.wrangler|__pycache__|dist|\.next)[\\/]' -and
            $_.Name -notmatch '^\.env|^config\.toml$|^latest\.json$|\.apk$|\.db($|-)|\.sqlite($|-)|\.log$|^secrets\.|^song-alias-overrides\.json($|\.)|\.tsbuildinfo$'
        })
    }
    foreach ($file in $Files) { $selected += Get-Item -LiteralPath (Join-Path $Base $file) }
    return @($selected | Sort-Object FullName -Unique)
}
function Relative-Name([string] $Base, [string] $Path) {
    return $Path.Substring($Base.TrimEnd([char[]]'\/').Length + 1).Replace([IO.Path]::DirectorySeparatorChar, [char]'/')
}

foreach ($directory in @('backend', 'scripts', 'source', 'admin-console\psq-admin-web')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $directory) -Force | Out-Null
}
$utf8 = [Text.UTF8Encoding]::new($false)
$bom = [Text.UTF8Encoding]::new($true)
Copy-Item -LiteralPath $binary -Destination (Join-Path $stage 'backend\phi-backend.exe')
[IO.File]::WriteAllText((Join-Path $stage 'scripts\Deploy-SongAliases.ps1'),
    [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Deploy-SongAliases.ps1')), $bom)
Copy-Item -LiteralPath (Join-Path $backendBase 'LICENSE') -Destination (Join-Path $stage 'LICENSE')
Copy-Item -LiteralPath (Join-Path $repo 'THIRD_PARTY_NOTICES.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $repo 'SOURCE_OFFER.md') -Destination $stage

# These info files are public source fixtures only. No backend/info payload is deployed.
$backendFiles = Selected-Files $backendBase @('src', 'crates', 'info', 'resources\fonts', 'resources\templates', 'tests') `
    @('Cargo.toml', 'Cargo.lock', 'config.example.toml', 'LICENSE', 'MODIFICATIONS.md', '.gitignore')
$sourceZip = [IO.Compression.ZipFile]::Open((Join-Path $stage 'source\backend-source-SongAliases-20261011.zip'), [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $backendFiles) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($sourceZip, $file.FullName,
            (Relative-Name $backendBase $file.FullName), [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
    foreach ($relative in @('server\scripts\Build-SongAliasesPackage.ps1', 'server\scripts\Deploy-SongAliases.ps1',
        'server\SERVER-SongAliases-20261011.md')) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($sourceZip, (Join-Path $repo $relative),
            $relative.Replace([char]'\', [char]'/'), [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $sourceZip.Dispose() }

$adminBase = Join-Path $repo 'psq-admin-web'
$adminFiles = Selected-Files $adminBase @('app', 'components', 'hooks', 'lib', 'public') `
    @('package.json', 'package-lock.json', 'tsconfig.json', 'vite.config.ts', 'next.config.ts', 'components.json',
        'README.md', 'Start-AdminWeb.ps1', 'next-env.d.ts', '.openai\hosting.json', '.gitignore', '.oxfmtrc.json', '.oxlintrc.json')
foreach ($file in $adminFiles) {
    $target = Join-Path (Join-Path $stage 'admin-console\psq-admin-web') (Relative-Name $adminBase $file.FullName)
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    if ($file.Name -eq 'Start-AdminWeb.ps1') {
        [IO.File]::WriteAllText($target, [IO.File]::ReadAllText($file.FullName), $bom)
    } else { Copy-Item -LiteralPath $file.FullName -Destination $target }
}
Copy-Item -LiteralPath (Join-Path $repo '启动APP后台网页.cmd') -Destination (Join-Path $stage 'admin-console')
$manifest = [ordered]@{
    package = $name; kind = 'server-only'; createdAt = '2026-10-11'
    includesApk = $false; publishesAppUpdate = $false; modifiesAppUpdate = $false
    updates = @('Admin song alias listing and editing', 'Persisted per-song alias overrides applied immediately to song search')
    replaces = @('current/backend/phi-backend.exe', 'source/installed-backend-source.zip')
    patches = @('current/caddy/Caddyfile: add only a dedicated /api/v2/admin/songs/aliases handler with a 128KB request limit if needed')
    preserves = @('entire current/backend/info directory and server catalog', 'existing song-alias-overrides.json',
        'APP update directory and manifests', 'database and private configuration', 'illustrations and practice charts')
    runtimeData = 'info/song-alias-overrides.json is created or updated by the running backend on successful admin saves. No runtime alias data is packaged or overwritten.'
    adminConsole = 'Current complete clean source and local launcher included. Run separately on the administrator computer; no website is published by this package.'
    database = 'No alias database migration; existing databases retained.'
    source = 'Built from current backend-source; corresponding clean source is included. Public info fixtures inside the source archive are never installed.'
}
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_ONLY_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'),
    [IO.File]::ReadAllText((Join-Path $repo 'server\SERVER-SongAliases-20261011.md')), $bom)
$bundle = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in (Get-ChildItem -LiteralPath $stage -Recurse -File -Force | Sort-Object FullName)) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, $file.FullName,
            (Relative-Name $stage $file.FullName), [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $bundle.Dispose() }
Copy-Item -LiteralPath $zip -Destination $desktopZip
Write-Output $zip
Write-Output $desktopZip
Write-Output $stage
