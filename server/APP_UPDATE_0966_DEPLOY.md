# Pre-0.9.6.6 云服务器部署步骤

本次只更新 Android 客户端。Next-Phi-Backend、Caddy、排行榜数据库和头像资源均不需要更新或重启。

## 1. 上传并发布 APK

将 `app-update-release-Pre-0.9.6.6.zip` 上传到云服务器桌面，以管理员身份打开 PowerShell：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.6.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.6'

$zipHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($zipHash -ne 'c8710ac65470a1de30fbc6148fcace607c2147c46b83fd264280f35cfa9a5a14') {
    throw "部署包 SHA-256 不一致: $zipHash"
}

Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Deploy-Pre-0.9.6.6.ps1"
```

脚本会校验 APK，备份原 `Publish-AppUpdate.ps1` 和 `latest.json`，发布 versionCode 19 的 APK，并以明确的 UTF-8 编码校验新更新清单。失败时会恢复原更新脚本和清单，不会停止后端或 Caddy。

## 2. 验证在线更新

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$health = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/health'

[pscustomobject]@{
    ApkStatus = $apkHead.StatusCode
    HealthStatus = $health.StatusCode
}

Get-ScheduledTask -TaskName 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期结果：

- `versionCode` 为 `19`
- `versionName` 为 `Pre-0.9.6.6`
- `sha256` 为 `0e045be6f3b65b0b1ab74bbc8894435cfc31bffc25c082cb2d5cbf586aec59c4`
- `sizeBytes` 为 `7186069`
- APK 和健康检查状态码均为 `200`
- 后端与 Caddy 计划任务均保持 `Running`
