# Pre-0.9.7.2 服务器升级包

本版本只更新 Android 客户端排行榜体验，服务器现有 `lite=true` 排行榜接口已经支持前 1000 名，因此升级包只发布签名 APK 和 `latest.json`，不会重启 Next-Phi-Backend 或 Caddy。

## 升级步骤

1. 将 `server-upgrade-Pre-0.9.7.2.zip` 与同名 `.sha256` 上传到 Windows 服务器。
2. 核对 ZIP 的 SHA-256 后解压到新的临时目录。
3. 以管理员身份打开 PowerShell，在解压目录运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Deploy-Pre-0.9.7.2.ps1
```

脚本会先校验包内文件，备份现有 `app-update` 清单与同名 APK，再原子发布 Pre-0.9.7.2；发布失败时会自动恢复旧清单和 APK。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-WebRequest -Method Head $latest.apkUrl | Select-Object StatusCode, Headers
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
```

预期 `versionCode` 为 `28`、`versionName` 为 `Pre-0.9.7.2`，APK 请求状态为 `200`，后端健康检查保持正常。

升级包内每项更新说明均为一句话：

- 排行榜最多显示前 1000 名公开玩家，并固定排名序号为单行显示。
- 排行榜滚动后可通过顶部向上箭头回到开头，点击当前玩家信息栏可跳转到本人排名。
- 更新日志和新版本弹窗中的本版本功能均统一用一句话概述。
