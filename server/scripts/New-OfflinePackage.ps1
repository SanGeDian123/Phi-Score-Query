[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $BackendSource,
    [Parameter(Mandatory)] [string] $IllustrationSource,
    [Parameter(Mandatory)] [string] $CaddyExe,
    [string] $Version = (Get-Date -Format 'yyyyMMdd-HHmmss')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$BackendSource = (Resolve-Path -LiteralPath $BackendSource).Path
$IllustrationSource = (Resolve-Path -LiteralPath $IllustrationSource).Path
$CaddyExe = (Resolve-Path -LiteralPath $CaddyExe).Path

foreach ($required in @('Cargo.toml', 'config.example.toml', 'LICENSE', 'src')) {
    if (-not (Test-Path -LiteralPath (Join-Path $BackendSource $required))) {
        throw "Next-Phi-Backend 源码不完整，缺少: $required"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $IllustrationSource 'ill'))) {
    throw '曲绘仓库根目录中未找到 ill 文件夹。'
}

$buildDrive = if (Test-Path 'D:\') { 'D:\PhigrosScoreBuild' } else { Join-Path $projectRoot '.backend-build' }
New-Item -ItemType Directory -Force -Path $buildDrive | Out-Null
$env:CARGO_TARGET_DIR = Join-Path $buildDrive 'backend-target'
$swaggerZip = Join-Path $buildDrive 'swagger-ui-v5.17.14.zip'
if (-not (Test-Path -LiteralPath $swaggerZip)) {
    Invoke-WebRequest -Uri 'https://codeload.github.com/swagger-api/swagger-ui/zip/refs/tags/v5.17.14' -OutFile $swaggerZip -UseBasicParsing
}
$swaggerBytes = [IO.File]::ReadAllBytes($swaggerZip)
if ($swaggerBytes.Length -lt 1MB -or $swaggerBytes[0] -ne 0x50 -or $swaggerBytes[1] -ne 0x4B) {
    throw 'Swagger UI ZIP is invalid or incomplete.'
}
$env:SWAGGER_UI_DOWNLOAD_URL = ([Uri]$swaggerZip).AbsoluteUri
$buildSource = Join-Path $buildDrive 'backend-source-patched'
if (Test-Path -LiteralPath $buildSource) { Remove-Item -LiteralPath $buildSource -Recurse -Force }
Copy-Item -LiteralPath $BackendSource -Destination $buildSource -Recurse
foreach ($relativeFile in @('src\features\stats\archive.rs', 'src\features\stats\mod.rs')) {
    $sourceFile = Join-Path $buildSource $relativeFile
    $sourceText = [IO.File]::ReadAllText($sourceFile, [Text.Encoding]::UTF8)
    $sourceText = $sourceText.Replace('::from_mins(1)', '::from_secs(60)')
    [IO.File]::WriteAllText($sourceFile, $sourceText, (New-Object Text.UTF8Encoding($false)))
}
Push-Location $buildSource
try {
    cargo build --locked --profile release-dist --target x86_64-pc-windows-msvc
    if ($LASTEXITCODE -ne 0) { throw 'Next-Phi-Backend 编译失败。' }
} finally {
    Pop-Location
}

$stage = Join-Path $projectRoot "server\dist\phigros-score-server-$Version"
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$stage\backend\resources", "$stage\backend\info", "$stage\caddy", "$stage\scripts", "$stage\prerequisites" | Out-Null

$vcRedist = Join-Path $buildDrive 'vc_redist.x64.exe'
if (-not (Test-Path -LiteralPath $vcRedist)) {
    Invoke-WebRequest -Uri 'https://aka.ms/vs/17/release/vc_redist.x64.exe' -OutFile $vcRedist -UseBasicParsing
}
$vcRedistBytes = [IO.File]::ReadAllBytes($vcRedist)
if ($vcRedistBytes.Length -lt 1MB -or $vcRedistBytes[0] -ne 0x4D -or $vcRedistBytes[1] -ne 0x5A) {
    throw 'Microsoft Visual C++ x64 Redistributable is invalid or incomplete.'
}
Copy-Item -LiteralPath $vcRedist -Destination "$stage\prerequisites\vc_redist.x64.exe"

$backendExe = Join-Path $env:CARGO_TARGET_DIR 'x86_64-pc-windows-msvc\release-dist\phi-backend.exe'
Copy-Item -LiteralPath $backendExe -Destination "$stage\backend\phi-backend.exe"
Copy-Item -LiteralPath "$BackendSource\LICENSE" -Destination "$stage\backend\LICENSE"
Copy-Item -LiteralPath "$projectRoot\SOURCE_OFFER.md" -Destination "$stage\SOURCE_OFFER.md"
Copy-Item -LiteralPath "$projectRoot\server\backend\config.toml" -Destination "$stage\backend\config.toml"
Copy-Item -LiteralPath "$BackendSource\resources\fonts" -Destination "$stage\backend\resources\fonts" -Recurse
Copy-Item -LiteralPath "$BackendSource\resources\templates" -Destination "$stage\backend\resources\templates" -Recurse
New-Item -ItemType Directory -Force -Path "$stage\backend\resources\ill" | Out-Null
foreach ($illustrationFolder in @('ill', 'illLow', 'illBlur')) {
    $sourceFolder = Join-Path $IllustrationSource $illustrationFolder
    if (Test-Path -LiteralPath $sourceFolder) {
        Copy-Item -LiteralPath $sourceFolder -Destination "$stage\backend\resources\ill\$illustrationFolder" -Recurse
    }
}
Copy-Item -Path "$BackendSource\info\*" -Destination "$stage\backend\info" -Recurse
Copy-Item -LiteralPath $CaddyExe -Destination "$stage\caddy\caddy.exe"
Copy-Item -LiteralPath "$projectRoot\server\caddy\Caddyfile" -Destination "$stage\caddy\Caddyfile"
Copy-Item -Path "$projectRoot\server\scripts\Install-Server.ps1", "$projectRoot\server\scripts\Run-Backend.ps1", "$projectRoot\server\scripts\Run-Caddy.ps1", "$projectRoot\server\scripts\Test-ServerConnectivity.ps1", "$projectRoot\server\scripts\Publish-AppUpdate.ps1", "$projectRoot\server\scripts\Publish-AppAnnouncement.ps1" -Destination "$stage\scripts"
Set-Content -LiteralPath "$stage\VERSION" -Value $Version -Encoding ascii

$zip = "$stage.zip"
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path "$stage\*" -DestinationPath $zip -CompressionLevel Optimal
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$zip.sha256" -Value "$hash  $(Split-Path $zip -Leaf)" -Encoding ascii
Write-Output "离线包: $zip"
Write-Output "SHA256: $hash"
