# Pre-0.9.6.9 服务器部署

本版本的定数表数据已经打包在 APP 内，曲绘继续使用服务器现有接口，因此不需要替换或重启后端，也不需要修改 Caddy。

## 部署

1. 将 `app-update-release-Pre-0.9.6.9.zip` 上传到云服务器桌面并解压。
2. 以管理员身份打开 PowerShell。
3. 执行：

```powershell
Set-Location 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.9'
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.6.9.ps1
```

脚本会校验 APK、备份原 `Publish-AppUpdate.ps1` 和 `latest.json`，发布 versionCode 22 的 APK，并明确使用 UTF-8 读取和校验更新清单。失败时会恢复原更新脚本和清单。

## 验收

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers

Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'

Get-ScheduledTask 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期版本为 `versionCode 22`、`versionName Pre-0.9.6.9`，APK SHA-256 为 `e561ff06309156d41db02e1c98fc9caee8de64a71c80e84eac9ea5a13589791b`，APK HEAD 状态码为 200。
