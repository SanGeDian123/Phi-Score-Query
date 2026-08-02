# Pre-0.9.7.0-Fix 服务器部署

本版本修复 `Pre-0.9.7.0` 发布包遗漏运行时曲库文件的问题。发布包会同时更新 APP、后端、Caddyfile、三份曲库文件以及《星拂云锦 feat. koi》的曲绘资源。

## 部署步骤

1. 将 `app-update-release-Pre-0.9.7.0-Fix.zip` 上传到云服务器桌面。
2. 解压到 `C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.7.0-Fix`。
3. 以管理员身份打开 PowerShell。
4. 执行：

```powershell
Set-Location 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.7.0-Fix'
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.7.0-Fix.ps1
```

脚本会执行以下操作：

- 校验 APK、后端、Caddyfile、曲库文件、别名表和新曲曲绘的 SHA-256；
- 验证新 Caddy 配置；
- 备份当前后端、曲库、曲绘、Caddyfile、更新清单和同名 APK；
- 替换后端及运行时曲库，重启后端与 Caddy；
- 验证本机和公网曲库均为 312 首，并确认包含《星拂云锦 feat. koi》；
- 验证新曲低清曲绘可以从公网访问；
- 最后发布 versionCode 25 的 APK 和 `latest.json`。

任何步骤失败都会恢复原后端、曲库、曲绘、Caddy 配置和更新清单。

## 验收

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog

$catalogResponse = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog'
$catalog = $catalogResponse.Content | ConvertFrom-Json
$catalog | Select-Object version, @{Name='songCount'; Expression={@($_.items).Count}}
$catalog.items |
    Where-Object id -eq '星拂云锦featkoi.S9ryne' |
    Format-List id, name, composer, illustrator, chartConstants

$artworkName = [Uri]::EscapeDataString('星拂云锦featkoi.S9ryne.png')
Invoke-WebRequest -Method Head "https://api.plc-liangpi-cup.xyz/_ill/illLow/$artworkName" |
    Select-Object StatusCode, Headers

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers

Get-ScheduledTask 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期结果：

- `versionCode` 为 `25`；
- `versionName` 为 `Pre-0.9.7.0-Fix`；
- APK SHA-256 为 `feff60cc2cfa432ecd7056903546a21441cb8793d820528890f2f73ca28fdbe1`；
- 公网曲库数量为 `312`，并包含《星拂云锦 feat. koi》；
- 新曲曲绘和 APK HEAD 状态码均为 200；
- 两个计划任务均为 `Running`。

部署成功后，旧版本用户可以通过自动检查更新或设置中的手动检查更新获取 `Pre-0.9.7.0-Fix`。安装后重新启动 APP，旧的曲库版本哈希会失效，APP 会自动下载 312 首的新曲库。
