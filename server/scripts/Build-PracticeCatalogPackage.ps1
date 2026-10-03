$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-only-upgrade-Pre-0.9.7.11-v87-PracticeCatalog'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
if ((Test-Path -LiteralPath $stage) -or (Test-Path -LiteralPath $zip)) { throw 'Package output already exists.' }
foreach ($dir in @('backend','source','scripts','practice-charts')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $dir) -Force | Out-Null
}
Copy-Item -LiteralPath 'D:\PhiAdminWebCargoTarget\release\phi-backend.exe' -Destination (Join-Path $stage 'backend')
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Deploy-PracticeCatalog-Pre-0.9.7.11.ps1') -Destination (Join-Path $stage 'scripts')
Copy-Item -LiteralPath (Join-Path $repo 'server\PRACTICE-CATALOG.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\LICENSE') -Destination $stage
foreach ($file in @('Rrharil_IN_16.1.pez','Rrharil_AT_17.6.pez')) {
    Copy-Item -LiteralPath (Join-Path 'D:\PhigrosAppBuild\server-only-upgrade-Pre-0.9.7.11-v86-Rrharil-20260929\practice-charts' $file) -Destination (Join-Path $stage 'practice-charts')
}
$sourceRoot = Join-Path $repo 'backend-source'
$archive = [IO.Compression.ZipFile]::Open((Join-Path $stage 'source\backend-source-Pre-0.9.7.9.zip'), [IO.Compression.ZipArchiveMode]::Create)
try {
    $selected = @()
    foreach ($tree in @('src','crates','info','resources\fonts','resources\templates','tests')) {
        $selected += @(Get-ChildItem -LiteralPath (Join-Path $sourceRoot $tree) -Recurse -File -Force | Where-Object {
            $_.FullName -notmatch '[\\/](node_modules|target|\.git|__pycache__)[\\/]' -and
            $_.Name -notmatch '^\.env|^config\.toml$|^latest\.json$|\.apk$|\.db($|-)|\.log$|^secrets\.'
        })
    }
    foreach ($file in @('Cargo.toml','Cargo.lock','config.example.toml','LICENSE','MODIFICATIONS.md')) { $selected += Get-Item -LiteralPath (Join-Path $sourceRoot $file) }
    foreach ($file in ($selected | Sort-Object FullName -Unique)) {
        $relative = $file.FullName.Substring($sourceRoot.Length).TrimStart('\').Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $archive.Dispose() }
$manifest = @{
    package=$name; kind='server-only'; versionName='Pre-0.9.7.11'; clientVersionCode=87
    includesApk=$false; publishesAppUpdate=$false; modifiesLatestJson=$false
    replaces=@('current/backend/phi-backend.exe','source/backend-source-Pre-0.9.7.9.zip','app-update/practice-charts/Rrharil_IN_16.1.pez','app-update/practice-charts/Rrharil_AT_17.6.pez')
}
$utf8 = New-Object Text.UTF8Encoding($true)
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_ONLY_MANIFEST.json'),($manifest | ConvertTo-Json -Depth 5),$utf8)
$guide = @'
# Pre-0.9.7.11 v87 服务器升级包

启用服务器谱面目录自动发现，附带已转换的 Rrhar'il IN/AT。
此包不发布 APP 更新，不含 APK，不修改 latest.json、Caddy、数据库或凭据。
替换后端及配套源码归档，添加两张谱面。默认目录 C:\Services\PhigrosScore。
部署会短暂重启 PhigrosScore-Backend，失败自动恢复；现有内存对局会中断。
新增/下架谱面的操作方法见 PRACTICE-CATALOG.md。

上传 ZIP 到服务器桌面，在管理员 PowerShell 执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$dest = Join-Path $desktop ('PSQ-v87-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath (Join-Path $desktop 'server-only-upgrade-Pre-0.9.7.11-v87-PracticeCatalog.zip') -DestinationPath $dest
Set-Location $dest
& .\scripts\Deploy-PracticeCatalog-Pre-0.9.7.11.ps1 -ValidateOnly
```

校验通过后，同一窗口正式部署：

```powershell
& .\scripts\Deploy-PracticeCatalog-Pre-0.9.7.11.ps1
```

本包在本地构建验证，尚未部署生产。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'),$guide,$utf8)
& (Join-Path $stage 'scripts\Deploy-PracticeCatalog-Pre-0.9.7.11.ps1') -ValidateOnly
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
Write-Output $zip
