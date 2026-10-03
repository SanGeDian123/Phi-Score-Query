$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-upgrade-Pre-0.9.7.11-Fix-v98-20261002'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
$desktopZip = Join-Path ([Environment]::GetFolderPath('Desktop')) ($name + '.zip')
$apkName = 'Phi_Score_Query_Pre-0.9.7.11-Fix.apk'
$apkSource = Join-Path 'D:\PhigrosAppBuild' $apkName
$sdk = 'C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.1.0'
foreach ($path in @($stage, $zip, $desktopZip)) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite an existing package: $path" }
}
$badging = & (Join-Path $sdk 'aapt.exe') dump badging $apkSource
if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "name='xyz.plcliangpicup.phigrosscore'" -or
    $badging[0] -notmatch "versionCode='98'" -or $badging[0] -notmatch "versionName='Pre-0.9.7.11-Fix'") { throw 'APK package/version mismatch.' }
$env:JAVA_HOME = 'D:\Android Studio\jbr'
$signature = & (Join-Path $sdk 'apksigner.bat') verify --print-certs $apkSource
if ($LASTEXITCODE -ne 0 -or ($signature -join "`n") -notmatch 'd9ac2c862de9ab4a5c114a6c8f0f63edc2ca516dd5cf7afaa4188434a456ba9a') { throw 'APK signing verification failed.' }
foreach ($dir in @('app-update', 'scripts')) { New-Item -ItemType Directory -Path (Join-Path $stage $dir) -Force | Out-Null }
$utf8 = New-Object Text.UTF8Encoding($false)
$bom = New-Object Text.UTF8Encoding($true)
Copy-Item -LiteralPath $apkSource -Destination (Join-Path $stage ('app-update\' + $apkName))
$deployName = 'Deploy-AppUpdate-Pre-0.9.7.11-Fix-v98.ps1'
$deploySource = Join-Path $PSScriptRoot $deployName
[IO.File]::WriteAllText((Join-Path $stage ('scripts\' + $deployName)), [IO.File]::ReadAllText($deploySource), $bom)
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination $stage
Copy-Item -LiteralPath (Join-Path $repo 'THIRD_PARTY_NOTICES.md') -Destination $stage
$notes = @(
    '定数表新增 18 级入口，修复 18.0 及以上谱面未显示的问题，等级入口随曲库自动扩展。',
    '谱面评级达成率与自定义 BP30 选谱同步支持 18.0 及以上谱面。',
    '补充本次七首新曲的章节信息，统一归入 Chapter 9。'
)
# sha256 is required by the APP's update protocol to verify the downloaded APK.
$hash = (Get-FileHash -LiteralPath $apkSource -Algorithm SHA256).Hash.ToLowerInvariant()
$release = [ordered]@{
    versionCode=98; versionName='Pre-0.9.7.11-Fix'; publishedAt='2026-10-02';
    apkUrl=('https://api.plc-liangpi-cup.xyz/app-update/' + $apkName);
    sha256=$hash; sizeBytes=(Get-Item -LiteralPath $apkSource).Length; mandatory=$false; changelog=$notes
}
[IO.File]::WriteAllText((Join-Path $stage 'app-update\latest.json'), ($release | ConvertTo-Json -Depth 5), $utf8)
[IO.File]::WriteAllText((Join-Path $stage 'changelog-Pre-0.9.7.11-Fix-v98.json'),
    (@{ versionCode=98; versionName='Pre-0.9.7.11-Fix'; changelog=$notes } | ConvertTo-Json -Depth 5), $utf8)
$manifest = [ordered]@{
    package=$name; kind='app-update'; versionCode=98; versionName='Pre-0.9.7.11-Fix'; apkFile=$apkName;
    includesApk=$true; publishesAppUpdate=$true; mandatory=$false;
    changes=@('app-update/latest.json', ('app-update/' + $apkName))
}
[IO.File]::WriteAllText((Join-Path $stage 'RELEASE_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
$readme = @'
# Pre-0.9.7.11-Fix / 98 发布更新升级包

部署后，低于版本号 98 的 APP 可检测到此次非强制更新。
默认安装目录：C:\Services\PhigrosScore。
部署范围：app-update/latest.json 与 app-update/Phi_Score_Query_Pre-0.9.7.11-Fix.apk。
脚本在正式发布前备份当前文件，先完整发布 APK，再原子替换更新清单，随后核对公开清单和 APK 大小；异常时恢复原文件。
更新说明详见 changelog-Pre-0.9.7.11-Fix-v98.json。

## 部署命令

将 ZIP 上传服务器桌面，以管理员身份打开 PowerShell。

先解压及校验：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$zip = Join-Path $desktop 'server-upgrade-Pre-0.9.7.11-Fix-v98-20261002.zip'
$dest = Join-Path $desktop ('PSQ-Publish-v98-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath $zip -DestinationPath $dest -ErrorAction Stop
Set-Location $dest
& .\scripts\Deploy-AppUpdate-Pre-0.9.7.11-Fix-v98.ps1 -ValidateOnly
```

看到 VALIDATION OK 后，在同一窗口正式发布：

```powershell
& .\scripts\Deploy-AppUpdate-Pre-0.9.7.11-Fix-v98.ps1
```

成功标志：PUBLISHED OK；版本号 98，mandatory=false，输出备份目录。
若服务器安装目录不同，两条部署命令都加 -InstallRoot '实际安装目录'。
校验模式只读取已安装文件，不创建临时目录；正式部署的临时文件写入现有 app-update 目录。
本包在本机生成和验证，正式发布由服务器部署命令执行。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, $bom)
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)
Copy-Item -LiteralPath $zip -Destination $desktopZip
Write-Output $desktopZip
