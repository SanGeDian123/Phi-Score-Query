$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-resource-practice-NWAD-IN-15.6-v80-20260926'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
if ((Test-Path -LiteralPath $stage) -or (Test-Path -LiteralPath $zip)) { throw "Package output already exists: $stage" }

# Add future practice charts here; playback and deployment use the same shared path.
$charts = @(
    @{ Source = 'C:\Users\Administrator\Desktop\杂项\chart\[NWAD]_IN_15.6.pez'; FileName = 'NWAD_IN_15.6.pez' }
)
New-Item -ItemType Directory -Path (Join-Path $stage 'practice-charts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'scripts') -Force | Out-Null
foreach ($chart in $charts) {
    if (-not (Test-Path -LiteralPath $chart.Source -PathType Leaf)) { throw "Missing source chart: $($chart.Source)" }
    Copy-Item -LiteralPath $chart.Source -Destination (Join-Path $stage "practice-charts\$($chart.FileName)")
}
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Deploy-PracticeCharts.ps1') -Destination (Join-Path $stage 'scripts\Deploy-PracticeCharts.ps1')

$manifest = @{
    package = $name
    kind = 'server-static-resource-only'
    appVersionName = 'Pre-0.9.7.10-Practice-Test'
    appVersionCode = 80
    includesApk = $false
    publishesAppUpdate = $false
    modifiesLatestJson = $false
    payload = @($charts | ForEach-Object { "practice-charts/$($_.FileName)" })
    destination = 'C:/Services/PhigrosScore/app-update/practice-charts/'
}
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_RESOURCE_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($true)))

$readme = @"
# PSQ 练习谱面服务器资源包

此包新增 [NWAD] IN Lv.15.6 的谱面文件，供 PSQ `Pre-0.9.7.10-Practice-Test`（versionCode 80）按需下载。

- 不含 APK、后端程序或 Caddy 配置。
- 不发布 APP 更新，不创建或修改 `app-update/latest.json`。
- 仅复制 `practice-charts/NWAD_IN_15.6.pez` 到现有静态目录。
- 不影响现有 Exoplanetary Mirage 谱面。

## 服务器校验

将 ZIP 上传服务器桌面并解压，在管理员 PowerShell 中执行：

``````powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "`$env:USERPROFILE\Desktop\$name"
& .\scripts\Deploy-PracticeCharts.ps1 -ValidateOnly
``````

## 正式部署

校验通过后执行：

``````powershell
& .\scripts\Deploy-PracticeCharts.ps1
``````

资源 URL：`https://api.plc-liangpi-cup.xyz/app-update/practice-charts/NWAD_IN_15.6.pez`

此包仅在本地生成，尚未部署到服务器。
"@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, (New-Object Text.UTF8Encoding($true)))

& (Join-Path $stage 'scripts\Deploy-PracticeCharts.ps1') -ValidateOnly
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)
$archive = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    if ($entries -match '\.apk$|(^|/)latest\.json$|Publish-AppUpdate\.ps1$') { throw 'Package contains forbidden APP update content.' }
    foreach ($required in @('practice-charts/NWAD_IN_15.6.pez', 'scripts/Deploy-PracticeCharts.ps1', 'SERVER_RESOURCE_MANIFEST.json', 'DEPLOY.md')) {
        if ($required -notin $entries) { throw "Package is missing $required" }
    }
} finally { $archive.Dispose() }
Write-Output $zip
