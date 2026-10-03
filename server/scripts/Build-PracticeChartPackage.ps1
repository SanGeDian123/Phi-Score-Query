$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-resource-practice-chart-Pre-0.9.7.10-Practice-Test-20260926'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
$chartSource = 'C:\Users\Administrator\Desktop\杂项\chart\Exoplanetary Mirage_AT_17.9.pez'
if ((Test-Path -LiteralPath $stage) -or (Test-Path -LiteralPath $zip)) { throw "Package output already exists: $stage" }
if (-not (Test-Path -LiteralPath $chartSource -PathType Leaf)) { throw "Missing source chart: $chartSource" }

New-Item -ItemType Directory -Path (Join-Path $stage 'practice-charts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'scripts') -Force | Out-Null
Copy-Item -LiteralPath $chartSource -Destination (Join-Path $stage 'practice-charts\ExoplanetaryMirage_AT_17.9.pez')
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Deploy-PracticeChart-Pre-0.9.7.10-Practice-Test.ps1') `
    -Destination (Join-Path $stage 'scripts\Deploy-PracticeChart-Pre-0.9.7.10-Practice-Test.ps1')

$manifest = @{
    package = $name
    kind = 'server-static-resource-only'
    appVersionName = 'Pre-0.9.7.10-Practice-Test'
    appVersionCode = 70
    includesApk = $false
    publishesAppUpdate = $false
    modifiesLatestJson = $false
    payload = @('practice-charts/ExoplanetaryMirage_AT_17.9.pez')
    destination = 'C:/Services/PhigrosScore/app-update/practice-charts/ExoplanetaryMirage_AT_17.9.pez'
    publicUrl = 'https://api.plc-liangpi-cup.xyz/app-update/practice-charts/ExoplanetaryMirage_AT_17.9.pez'
}
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_RESOURCE_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($true)))

$readme = @"
# Exoplanetary Mirage AT 17.9 在线谱面资源

此包只新增服务器静态谱面文件，供 PSQ `Pre-0.9.7.10-Practice-Test`（versionCode 70）内置练习播放器按需下载。

- 不含 APK、后端程序或 Caddy 配置。
- 不发布 APP 更新，也不创建或修改 `app-update/latest.json`。
- 只复制 `practice-charts/ExoplanetaryMirage_AT_17.9.pez` 到现有 `/app-update/` 静态目录。
- 部署不需要重启服务。若覆盖已有谱面，脚本会先在服务器 `backup` 目录留存旧文件。

## 在服务器校验

将 ZIP 上传到服务器桌面并解压，在管理员 PowerShell 中执行：

``````powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "`$env:USERPROFILE\Desktop\$name"
& .\scripts\Deploy-PracticeChart-Pre-0.9.7.10-Practice-Test.ps1 -ValidateOnly
``````

## 正式复制谱面资源

校验通过后再执行：

``````powershell
& .\scripts\Deploy-PracticeChart-Pre-0.9.7.10-Practice-Test.ps1
``````

默认目标为 `C:\Services\PhigrosScore\app-update\practice-charts\`。脚本只写入该目录中的谱面文件，不触碰应用更新清单或版本发布状态。

资源 URL：

`https://api.plc-liangpi-cup.xyz/app-update/practice-charts/ExoplanetaryMirage_AT_17.9.pez`

本包生成于本地，尚未部署到服务器。
"@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, (New-Object Text.UTF8Encoding($true)))

& (Join-Path $stage 'scripts\Deploy-PracticeChart-Pre-0.9.7.10-Practice-Test.ps1') -ValidateOnly
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)

$archive = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entryNames = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    if ($entryNames -match '\.apk$|(^|/)latest\.json$|Publish-AppUpdate\.ps1$') { throw 'Generated package contains forbidden APP update content.' }
    foreach ($required in @('practice-charts/ExoplanetaryMirage_AT_17.9.pez', 'scripts/Deploy-PracticeChart-Pre-0.9.7.10-Practice-Test.ps1', 'SERVER_RESOURCE_MANIFEST.json', 'DEPLOY.md')) {
        if ($required -notin $entryNames) { throw "Generated package is missing $required" }
    }
} finally { $archive.Dispose() }

Write-Output $zip
