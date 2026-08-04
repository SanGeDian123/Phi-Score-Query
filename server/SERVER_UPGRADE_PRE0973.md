# Pre-0.9.7.3 服务器升级包

本版本发布签名 Android 客户端：新增单曲成绩图、优化返回与生图体验，并让二维码登录优先在本地完成；服务器仍用于最终 SessionToken 交换和其他查分接口，因此本包只更新 `app-update` 清单与 APK，不重启 Next-Phi-Backend 或 Caddy。

## 升级步骤

1. 将 `server-upgrade-Pre-0.9.7.3.zip` 与同名 `.sha256` 上传到 Windows 服务器。
2. 核对 ZIP 的 SHA-256 后解压到桌面目录 `server-upgrade-Pre-0.9.7.3`。
3. 以管理员身份打开 PowerShell，执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
& (Join-Path ([Environment]::GetFolderPath('Desktop')) 'server-upgrade-Pre-0.9.7.3\scripts\Deploy-Pre-0.9.7.3.ps1')
```

脚本会先校验包内文件，备份现有 `app-update` 清单与同名 APK，再原子发布 Pre-0.9.7.3；发布失败时会自动恢复旧清单和 APK。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-WebRequest -Method Head $latest.apkUrl | Select-Object StatusCode, Headers
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
```

预期 `versionCode` 为 `29`、`versionName` 为 `Pre-0.9.7.3`，APK 请求状态为 `200`，后端健康检查保持正常。

## 本版本更新说明

- 单曲详情新增严格按设计图生成的单曲成绩图，首次进入自动生成并支持更新、保存、分享和放大查看。
- 单曲页返回会回到上一层，二维码登录优先在本地完成，B30 生图保留旧图并减少重复请求。
- 关于页新增作者板块并将开源信息放在其下方。
