# Pre-0.9.7.5 服务器升级包

本包发布 Pre-0.9.7.5 签名 Android 客户端与更新清单，不修改或重启 Next-Phi-Backend 和 Caddy。

## 升级步骤

将 `server-upgrade-Pre-0.9.7.5.zip` 与同名 `.sha256` 放到 Windows 服务器桌面，以管理员身份打开 PowerShell 并执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'server-upgrade-Pre-0.9.7.5.zip'; $p=Join-Path $d 'server-upgrade-Pre-0.9.7.5'; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-Pre-0.9.7.5.ps1')
```

脚本会校验包内文件，备份现有更新清单与同名 APK，再原子发布 Pre-0.9.7.5；失败时自动回滚。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-WebRequest -Method Head $latest.apkUrl | Select-Object StatusCode, Headers
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
```

预期 `versionCode` 为 `33`、`versionName` 为 `Pre-0.9.7.5`，APK 请求状态为 `200`，后端健康检查保持正常。

## 本版本更新说明

- B30 图片、单曲图片、排行榜及其他联网场景新增统一的无响应超时检测和自动重试。
- APP 首次打开提示体验问卷，设置页保留腾讯问卷入口。
