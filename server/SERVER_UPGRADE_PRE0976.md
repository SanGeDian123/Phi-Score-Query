# Pre-0.9.7.6 服务器升级包

本包更新 Next-Phi-Backend 图片接口、P30 独立路由、Caddy 配置和简约成绩图模板，并发布 Pre-0.9.7.6 签名 Android 客户端与更新清单。

## 升级命令

将 `server-upgrade-Pre-0.9.7.6.zip` 放在服务器桌面，以管理员身份打开 PowerShell，可在任意目录执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'server-upgrade-Pre-0.9.7.6.zip'; $p=Join-Path $d 'server-upgrade-Pre-0.9.7.6'; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-Pre-0.9.7.6.ps1')
```

脚本会校验包内文件，备份当前后端、Caddy、成绩图模板、更新清单与相关 APK，替换后端并启用 P30 独立接口，通过健康检查后发布 APP；失败时自动回滚。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-WebRequest -Method Head $latest.apkUrl | Select-Object StatusCode, Headers
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
```

预期 `versionCode` 为 `34`、`versionName` 为 `Pre-0.9.7.6`，APK 请求状态为 `200`，后端健康检查正常。

## 本版本更新说明

- 从定数表进入单曲详情后，返回时会保留原来的位置。
- 优化 B30、P30 和单曲成绩图的生成速度。
- 图片页新增 P30 成绩图，可横向滑动切换 B30 和 P30。
