# Pre-0.9.6.3 云服务器部署步骤

本次更新只涉及 Android 客户端。Next-Phi-Backend、Caddy 和头像资源均不需要再次更新或重启。

## 1. 上传并校验部署包

将 `app-update-release-Pre-0.9.6.3.zip` 上传到云服务器桌面，以管理员身份打开 PowerShell：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.3.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.3'

$zipHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($zipHash -ne '5e30fd3483bc847b95db3d9e1226c1b14abf740d567eb1c623f3f809c484c082') {
    throw "部署包 SHA-256 不一致: $zipHash"
}

Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Deploy-Pre-0.9.6.3.ps1"
```

脚本会发布 APK 并原子更新 `latest.json`。旧 APK 不会被删除，后端与 Caddy 计划任务也不会被停止。

## 2. 验证在线更新

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes

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

- `versionCode` 为 `16`；
- `versionName` 为 `Pre-0.9.6.3`；
- `sha256` 为 `77322935ceebbfa4943b21b91e9c33d713d5ae84e6781f9bc85c39784a281286`；
- `sizeBytes` 为 `7186017`；
- APK 与健康检查状态码均为 `200`；
- 后端与 Caddy 两项计划任务保持 `Running`。
