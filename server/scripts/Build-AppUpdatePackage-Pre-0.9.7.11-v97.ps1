$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-upgrade-Pre-0.9.7.11-v97-20261002'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
$desktopZip = Join-Path ([Environment]::GetFolderPath('Desktop')) ($name + '.zip')
$apkName = 'Phi_Score_Query_Pre-0.9.7.11_Update.apk'
$apkSource = Join-Path 'D:\PhigrosAppBuild' $apkName
$sdk = 'C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.1.0'
foreach ($path in @($stage, $zip, $desktopZip)) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite an existing package: $path" }
}
$badging = & (Join-Path $sdk 'aapt.exe') dump badging $apkSource
if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "name='xyz.plcliangpicup.phigrosscore'" -or
    $badging[0] -notmatch "versionCode='97'" -or $badging[0] -notmatch "versionName='Pre-0.9.7.11'") { throw 'APK package/version mismatch.' }
$env:JAVA_HOME = 'D:\Android Studio\jbr'
$signature = & (Join-Path $sdk 'apksigner.bat') verify --print-certs $apkSource
if ($LASTEXITCODE -ne 0 -or ($signature -join "`n") -notmatch 'd9ac2c862de9ab4a5c114a6c8f0f63edc2ca516dd5cf7afaa4188434a456ba9a') { throw 'APK signing verification failed.' }
foreach ($dir in @('app-update', 'scripts')) { New-Item -ItemType Directory -Path (Join-Path $stage $dir) -Force | Out-Null }
$utf8 = New-Object Text.UTF8Encoding($false)
$bom = New-Object Text.UTF8Encoding($true)
Copy-Item -LiteralPath $apkSource -Destination (Join-Path $stage ('app-update\' + $apkName))
$deployName = 'Deploy-AppUpdate-Pre-0.9.7.11-v97.ps1'
$deploySource = Join-Path $PSScriptRoot $deployName
[IO.File]::WriteAllText((Join-Path $stage ('scripts\' + $deployName)), [IO.File]::ReadAllText($deploySource), $bom)
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination $stage
Copy-Item -LiteralPath (Join-Path $repo 'THIRD_PARTY_NOTICES.md') -Destination $stage
$notes = @(
    '修复延迟校准中只播放音效而不进行 AUTOPLAY 判定的问题。',
    '延迟校准新增 0.5–2.0x 倍速调整，切换时同步音频、谱面与音效。',
    '优化音频时钟与打击音效调度，减少音符判定和音效的额外等待。',
    '修复停顿或换向滑动后的 Flick 漏判，减少黄键接红键的断触。',
    '新增严判模式，采用 Phigros 课题模式的判定规则。',
    '重构练习结算页面，采用半透明曲绘背景与新的成绩布局。',
    'Good 总数旁直接显示 Early、Late 详细计数。',
    '严判局在游戏页面底部居中显示“*严判模式”。',
    '修复连击数为 0–2 时偶发显示 COMBO 数字的问题。',
    '优化谱面资源下载与曲绘加载。',
    '修复部分谱面下载完成后重复下载及闪退的问题。'
)
# sha256 is required by the APP's update protocol to verify the downloaded APK.
$hash = (Get-FileHash -LiteralPath $apkSource -Algorithm SHA256).Hash.ToLowerInvariant()
$release = [ordered]@{
    versionCode=97; versionName='Pre-0.9.7.11'; publishedAt='2026-10-02';
    apkUrl=('https://api.plc-liangpi-cup.xyz/app-update/' + $apkName);
    sha256=$hash; sizeBytes=(Get-Item -LiteralPath $apkSource).Length; mandatory=$false; changelog=$notes
}
[IO.File]::WriteAllText((Join-Path $stage 'app-update\latest.json'), ($release | ConvertTo-Json -Depth 5), $utf8)
[IO.File]::WriteAllText((Join-Path $stage 'changelog-Pre-0.9.7.11-v97.json'),
    (@{ versionCode=97; versionName='Pre-0.9.7.11'; changelog=$notes } | ConvertTo-Json -Depth 5), $utf8)
$manifest = [ordered]@{
    package=$name; kind='app-update'; versionCode=97; versionName='Pre-0.9.7.11'; apkFile=$apkName;
    includesApk=$true; publishesAppUpdate=$true; mandatory=$false;
    changes=@('app-update/latest.json', ('app-update/' + $apkName))
}
[IO.File]::WriteAllText((Join-Path $stage 'RELEASE_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
$readme = @'
# Pre-0.9.7.11 / 97 发布更新升级包

部署后，低于版本号 97 的 APP 可检测到此次非强制更新。
默认安装目录：C:\Services\PhigrosScore。
部署范围：app-update/latest.json 与 app-update/Phi_Score_Query_Pre-0.9.7.11_Update.apk。
脚本在正式发布前备份当前文件，先完整发布 APK，再原子替换更新清单，随后核对公开清单和 APK 大小；异常时恢复原文件。
更新说明详见 changelog-Pre-0.9.7.11-v97.json。

## 部署命令

将 ZIP 上传服务器桌面，以管理员身份打开 PowerShell。

先解压及校验：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$zip = Join-Path $desktop 'server-upgrade-Pre-0.9.7.11-v97-20261002.zip'
$dest = Join-Path $desktop ('PSQ-Publish-v97-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath $zip -DestinationPath $dest -ErrorAction Stop
Set-Location $dest
& .\scripts\Deploy-AppUpdate-Pre-0.9.7.11-v97.ps1 -ValidateOnly
```

看到 VALIDATION OK 后，在同一窗口正式发布：

```powershell
& .\scripts\Deploy-AppUpdate-Pre-0.9.7.11-v97.ps1
```

成功标志：PUBLISHED OK；版本号 97，mandatory=false，输出备份目录。
若服务器安装目录不同，两条部署命令都加 -InstallRoot '实际安装目录'。
校验模式只读取已安装文件，不创建临时目录；正式部署的临时文件写入现有 app-update 目录。
本包在本机生成和验证，正式发布由服务器部署命令执行。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, $bom)
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)
Copy-Item -LiteralPath $zip -Destination $desktopZip
Write-Output $desktopZip
