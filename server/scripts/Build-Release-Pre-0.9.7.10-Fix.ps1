$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-upgrade-Pre-0.9.7.10-Fix-v69-20260925'
$apkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix-v69.apk'
$publishedApkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix.apk'
$sourceName = 'backend-source-Pre-0.9.7.10-Fix.zip'
$apkBuild = 'D:\PhigrosAppBuild\ui-release-Pre-0.9.7.10-Fix-v69\build\app\outputs\apk\release\app-release.apk'
$backendBuild = 'D:\PhigrosAppBuild\rust-target\release\phi-backend.exe'
$outputRoot = 'D:\PhigrosAppBuild'
$stage = Join-Path $outputRoot $name
$zip = Join-Path $outputRoot ($name + '.zip')
$workspaceApk = Join-Path $repo $apkName
$workspaceZip = Join-Path $repo ($name + '.zip')
$outputRoot = [IO.Path]::GetFullPath($outputRoot).TrimEnd('\')
foreach ($target in @($stage, $zip)) {
    $absoluteTarget = [IO.Path]::GetFullPath($target)
    if (-not $absoluteTarget.StartsWith($outputRoot + '\',[StringComparison]::OrdinalIgnoreCase)) {
        throw "Build output is outside the approved D: build directory: $absoluteTarget"
    }
}
foreach ($target in @($stage, $zip, $workspaceApk, $workspaceZip)) {
    if (Test-Path -LiteralPath $target) { throw "Refusing to overwrite an existing release artifact: $target" }
}
foreach ($source in @($apkBuild, $backendBuild, (Join-Path $repo 'server\changelog-Pre-0.9.7.10-Fix.json'))) {
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing release input: $source" }
}

New-Item -ItemType Directory -Path $stage | Out-Null
foreach ($dir in @('backend\resources\fonts','backend\resources\templates\image\bn','caddy','scripts','source')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $dir) -Force | Out-Null
}
Copy-Item -LiteralPath $apkBuild -Destination (Join-Path $stage $apkName)
Copy-Item -LiteralPath $apkBuild -Destination $workspaceApk -Force
Copy-Item -LiteralPath $backendBuild -Destination (Join-Path $stage 'backend\phi-backend.exe')
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\resources\fonts\Aldrich-Regular.ttf') -Destination (Join-Path $stage 'backend\resources\fonts\Aldrich-Regular.ttf')
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\resources\templates\image\bn\phi_plugin_assets') -Destination (Join-Path $stage 'backend\resources\templates\image\bn\phi_plugin_assets') -Recurse
Copy-Item -LiteralPath (Join-Path $repo 'server\caddy\Caddyfile') -Destination (Join-Path $stage 'caddy\Caddyfile')
foreach ($script in @('Deploy-Release-Pre-0.9.7.10-Fix.ps1','Run-Backend.ps1','Run-Caddy.ps1','Publish-AppUpdate.ps1','Publish-AppAnnouncement.ps1')) {
    Copy-Item -LiteralPath (Join-Path $repo ('server\scripts\' + $script)) -Destination (Join-Path $stage ('scripts\' + $script))
}
Copy-Item -LiteralPath (Join-Path $repo 'server\changelog-Pre-0.9.7.10-Fix.json') -Destination (Join-Path $stage 'changelog-Pre-0.9.7.10-Fix.json')
Copy-Item -LiteralPath (Join-Path $repo 'server\RELEASE-ANNOUNCEMENT-Pre-0.9.7.10-Fix.md') -Destination (Join-Path $stage 'RELEASE-ANNOUNCEMENT-Pre-0.9.7.10-Fix.md')
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\LICENSE') -Destination (Join-Path $stage 'LICENSE')

function Create-SourceZip([string] $Base, [string] $Target) {
    $archive = [IO.Compression.ZipFile]::Open($Target, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $selected = @()
        foreach ($tree in @('src','crates','info','resources\fonts','resources\templates','tests')) {
            $selected += @(Get-ChildItem -LiteralPath (Join-Path $Base $tree) -Recurse -File -Force | Where-Object {
                $_.FullName -notmatch '[\\/](node_modules|target|\.git|\.wrangler|__pycache__)[\\/]' -and
                $_.Name -notmatch '^\.env|^config\.toml$|^latest\.json$|\.apk$|\.db($|-)|\.log$|^secrets\.'
            })
        }
        foreach ($file in @('Cargo.toml','Cargo.lock','config.example.toml','LICENSE','MODIFICATIONS.md')) {
            $selected += Get-Item -LiteralPath (Join-Path $Base $file)
        }
        foreach ($file in ($selected | Sort-Object FullName -Unique)) {
            $relative = $file.FullName.Substring($Base.TrimEnd('\').Length).TrimStart('\').Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Fastest) | Out-Null
        }
    } finally { $archive.Dispose() }
}
Create-SourceZip (Join-Path $repo 'backend-source') (Join-Path $stage ('source\' + $sourceName))

$releaseManifest = [ordered]@{
    package=$name; kind='server-and-app-update'; versionCode=69; versionName='Pre-0.9.7.10-Fix'
    includesApk=$true; publishesAppUpdate=$true
    replaces=@('current/backend/phi-backend.exe','current/caddy/Caddyfile','source/' + $sourceName,'app-update/' + $publishedApkName,'app-update/latest.json')
    preserves=@('secrets.env','databases','resources outside the package','service data','scheduled task definitions','previous APKs','existing announcements','admin website')
}
[IO.File]::WriteAllText((Join-Path $stage 'RELEASE_MANIFEST.json'),($releaseManifest | ConvertTo-Json -Depth 5),(New-Object Text.UTF8Encoding($false)))

$readme = @'
# Pre-0.9.7.10-Fix / versionCode 69 发布升级包

本包包含签名 APK、当前后端程序及源码、Phi-Plugin 资源、Caddy 配置和 APP 更新脚本。部署会更新后端并发布非强制 APP 更新清单；不会自动发布独立公告，公告草稿已随包附上。

将 ZIP 上传到服务器桌面，以管理员身份打开 PowerShell，先解压并校验：

``````powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$zip = Join-Path $desktop 'server-upgrade-Pre-0.9.7.10-Fix-v69-20260925.zip'
$dest = Join-Path $desktop ('PSQ-Pre09710-v69-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath $zip -DestinationPath $dest -ErrorAction Stop
Set-Location $dest
& .\scripts\Deploy-Release-Pre-0.9.7.10-Fix.ps1 -ValidateOnly
``````

校验成功后，在同一窗口正式部署：

``````powershell
& .\scripts\Deploy-Release-Pre-0.9.7.10-Fix.ps1
``````

默认服务器目录为 C:\Services\PhigrosScore。部署会备份替换文件，短暂重启后端和 Caddy，检查本地健康、接口鉴权及 APP 下载；失败时自动回滚。-ValidateOnly 不修改服务器，也不代表已部署。

公告草稿：RELEASE-ANNOUNCEMENT-Pre-0.9.7.10-Fix.md。此包尚未上传或部署。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'),$readme,(New-Object Text.UTF8Encoding($true)))

$files = [ordered]@{}
Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($stage.TrimEnd('\').Length).TrimStart('\').Replace('\','/')
    $files[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$integrity = [ordered]@{ versionCode=69; versionName='Pre-0.9.7.10-Fix'; files=$files }
[IO.File]::WriteAllText((Join-Path $stage 'SHA256SUMS.json'),($integrity | ConvertTo-Json -Depth 5),(New-Object Text.UTF8Encoding($false)))
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
Copy-Item -LiteralPath $zip -Destination $workspaceZip -Force
Write-Output $workspaceApk
Write-Output $workspaceZip
