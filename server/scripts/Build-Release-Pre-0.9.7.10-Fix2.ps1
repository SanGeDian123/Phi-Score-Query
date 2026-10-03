$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-upgrade-Pre-0.9.7.10-Fix2-v82-20260926'
$outputRoot = [IO.Path]::GetFullPath('D:\PhigrosAppBuild').TrimEnd('\')
$stage = Join-Path $outputRoot $name
$zip = $stage + '.zip'
$workspaceZip = Join-Path $repo ($name + '.zip')
$apkName = 'Phi-Score-Query-Pre-0.9.7.10-Fix2-v82.apk'
$chartName = 'Archidoxen_AT_16.9.pez'
$apkSource = Join-Path $repo $apkName
$chartSource = 'C:\Users\Administrator\Desktop\杂项\chart\Archidoxen_AT_16.9.pez'
foreach ($target in @($stage, $zip)) {
    $full = [IO.Path]::GetFullPath($target)
    if (-not $full.StartsWith($outputRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Build output is outside D: build directory: $full"
    }
}
foreach ($target in @($stage, $zip, $workspaceZip)) {
    if (Test-Path -LiteralPath $target) { throw "Refusing to overwrite existing artifact: $target" }
}
foreach ($source in @($apkSource, $chartSource,
    (Join-Path $repo 'server\changelog-Pre-0.9.7.10-Fix2.json'),
    (Join-Path $repo 'server\scripts\Deploy-Release-Pre-0.9.7.10-Fix2.ps1'),
    (Join-Path $repo 'server\scripts\Publish-AppUpdate.ps1'))) {
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing release input: $source" }
}

New-Item -ItemType Directory -Path (Join-Path $stage 'practice-charts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'scripts') -Force | Out-Null
Copy-Item -LiteralPath $apkSource -Destination (Join-Path $stage $apkName)
Copy-Item -LiteralPath $chartSource -Destination (Join-Path $stage ('practice-charts\' + $chartName))
Copy-Item -LiteralPath (Join-Path $repo 'server\changelog-Pre-0.9.7.10-Fix2.json') -Destination $stage
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Deploy-Release-Pre-0.9.7.10-Fix2.ps1') -Destination (Join-Path $stage 'scripts')
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Publish-AppUpdate.ps1') -Destination (Join-Path $stage 'scripts')

$manifest = [ordered]@{
    package = $name
    kind = 'app-update-and-practice-chart'
    versionCode = 82
    versionName = 'Pre-0.9.7.10-Fix2'
    apkFile = $apkName
    chartFile = $chartName
    includesApk = $true
    publishesAppUpdate = $true
    changes = @('app-update/latest.json', 'app-update/Phi-Score-Query-Pre-0.9.7.10-Fix2.apk', 'app-update/practice-charts/Archidoxen_AT_16.9.pez')
}
[IO.File]::WriteAllText((Join-Path $stage 'RELEASE_MANIFEST.json'),
    ($manifest | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))

$readme = @'
# Pre-0.9.7.10-Fix2 / versionCode 82 发布升级包

此包包含签名 APK、Archidoxen AT Lv.16.9 谱面、APP 更新发布脚本及更新日志。谱面会存放在服务器，用户打开入口时按需下载。本包不替换后端程序或 Caddy 配置。

将 ZIP 上传服务器桌面，在管理员 PowerShell 中执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$zip = Join-Path $desktop 'server-upgrade-Pre-0.9.7.10-Fix2-v82-20260926.zip'
$dest = Join-Path $desktop ('PSQ-Fix2-v82-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath $zip -DestinationPath $dest -ErrorAction Stop
Set-Location $dest
& .\scripts\Deploy-Release-Pre-0.9.7.10-Fix2.ps1 -ValidateOnly
```

校验通过后，在同一窗口正式部署：

```powershell
& .\scripts\Deploy-Release-Pre-0.9.7.10-Fix2.ps1
```

默认服务器目录为 C:\Services\PhigrosScore。正式部署会备份旧更新清单和同名文件，发布非强制 APP 更新，并复制新谱面；失败时恢复原文件。此包只在本地生成，尚未部署。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, [Text.UTF8Encoding]::new($true))
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)
Copy-Item -LiteralPath $zip -Destination $workspaceZip
Write-Output $workspaceZip
