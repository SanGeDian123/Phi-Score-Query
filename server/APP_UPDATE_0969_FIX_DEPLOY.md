# Pre-0.9.6.9-Fix 服务器部署

本版本需要同时更新 APP 与 Next-Phi-Backend。新 APP 会把自身版本传递给生图接口，后端据此生成动态版本水印，并把 APP 版本加入图片缓存键。

## 部署

1. 将 `app-update-release-Pre-0.9.6.9-Fix.zip` 上传到云服务器桌面并解压。
2. 以管理员身份打开 PowerShell。
3. 执行：

```powershell
Set-Location 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.9-Fix'
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.6.9-Fix.ps1
```

脚本会校验 APK 和后端程序，停止并备份原后端，替换后端后执行本机健康检查，再发布 versionCode 23 的 APK 与更新清单。任何步骤失败都会恢复原后端和更新清单。本次不修改或重启 Caddy。

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

预期版本为 `versionCode 23`、`versionName Pre-0.9.6.9-Fix`，APK SHA-256 为 `7fd0cef7b880860065738da96f824f74497884ebeca21bc7d17143a9052f99d0`，APK HEAD 状态码为 200，两个计划任务均为 Running。

安装 Fix 版 APP 后重新生成 B30 图片，底部应显示：

```text
Phi Score Query · Pre-0.9.6.9-Fix
```
