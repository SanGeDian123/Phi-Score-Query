# Pre-0.9.7.4 服务器升级包

本包发布 Pre-0.9.7.4 签名 Android 客户端与更新清单，不修改或重启 Next-Phi-Backend 和 Caddy。

## 升级步骤

1. 将 `server-upgrade-Pre-0.9.7.4.zip` 与同名 `.sha256` 放到 Windows 服务器桌面并解压 ZIP。
2. 以管理员身份打开 PowerShell，执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
& (Join-Path ([Environment]::GetFolderPath('Desktop')) 'server-upgrade-Pre-0.9.7.4\scripts\Deploy-Pre-0.9.7.4.ps1')
```

脚本会校验包内文件，备份现有更新清单与同名 APK，再原子发布 Pre-0.9.7.4；失败时自动回滚。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-WebRequest -Method Head $latest.apkUrl | Select-Object StatusCode, Headers
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
```

预期 `versionCode` 为 `32`、`versionName` 为 `Pre-0.9.7.4`，APK 请求状态为 `200`，后端健康检查保持正常。

## 本版本更新说明

- 新增并默认启用全新单曲成绩图样式，保留可切换的 Legacy 样式并通过渲染版本隔离旧缓存。
- 左侧导航新增“更多”页面，以 16:9 比例展示装修中占位图和简约提示。
- B30、Best N 与 P30 的谱面成绩使用定数表同款依次展开动画。
