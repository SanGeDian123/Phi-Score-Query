$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-only-upgrade-Pre-0.9.7.11-v92-ResourcePerformance-Fix1'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
if ((Test-Path -LiteralPath $zip) -or (Test-Path -LiteralPath $stage)) { throw 'Package output already exists.' }
foreach ($dir in @('backend','scripts','source','caddy')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $dir) -Force | Out-Null
}
$bom = New-Object Text.UTF8Encoding($true)
Copy-Item -LiteralPath 'D:\PhiAdminWebCargoTarget\release\phi-backend.exe' -Destination (Join-Path $stage 'backend\phi-backend.exe')
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Deploy-ResourcePerformance-v92.ps1') -Destination (Join-Path $stage 'scripts\Deploy-ResourcePerformance-v92.ps1')
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\LICENSE') -Destination (Join-Path $stage 'LICENSE')
function Create-SourceZip([string] $Base, [string] $Target, [string[]] $Trees, [string[]] $Files) {
    $archive = [IO.Compression.ZipFile]::Open($Target, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $selected = @()
        foreach ($tree in $Trees) {
            $selected += @(Get-ChildItem -LiteralPath (Join-Path $Base $tree) -Recurse -File -Force | Where-Object {
                $_.FullName -notmatch '[\\/](node_modules|target|\.git|\.wrangler|__pycache__)[\\/]' -and
                $_.Name -notmatch '^\.env|^config\.toml$|^latest\.json$|\.apk$|\.db($|-)|\.log$|^secrets\.'
            })
        }
        foreach ($file in $Files) { $selected += Get-Item -LiteralPath (Join-Path $Base $file) }
        foreach ($file in ($selected | Sort-Object FullName -Unique)) {
            $relative = $file.FullName.Substring($Base.TrimEnd('\').Length).TrimStart('\').Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Fastest) | Out-Null
        }
    } finally { $archive.Dispose() }
}

Create-SourceZip (Join-Path $repo 'backend-source') (Join-Path $stage 'source\backend-source-Pre-0.9.7.9.zip') @('src','crates','info','resources\fonts','resources\templates','tests') @('Cargo.toml','Cargo.lock','config.example.toml','LICENSE','MODIFICATIONS.md')
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\ResourcePerformance-Caddy.ps1') -Destination (Join-Path $stage 'scripts')
Copy-Item -LiteralPath (Join-Path $repo 'server\caddy\Caddyfile') -Destination (Join-Path $stage 'caddy')
Copy-Item -LiteralPath (Join-Path $repo 'server\RESOURCE-PERFORMANCE-v92.md') -Destination (Join-Path $stage 'DEPLOY.md')
$manifest = @{ package=$name; kind='server-only'; clientVersionCode=92; includesApk=$false; publishesAppUpdate=$false; changes=@('backend','source','resource cache policy in installed Caddyfile') }
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_ONLY_MANIFEST.json'),($manifest | ConvertTo-Json -Depth 4),$bom)
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
Write-Output $zip
