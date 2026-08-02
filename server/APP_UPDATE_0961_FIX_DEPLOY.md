# Pre-0.9.6.1-Fix 云服务器部署步骤

以下操作均在 Windows 云服务器上以管理员身份执行。本次只发布 APK 和 `latest.json`，不需要重编译或重启 Next-Phi-Backend，也不需要修改或重启 Caddy。

## 1. 上传并执行部署包

将 `app-update-release-Pre-0.9.6.1-Fix.zip` 上传到云服务器桌面，然后执行：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.1-Fix.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.1-Fix'

Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Deploy-Pre-0.9.6.1-Fix.ps1"
```

脚本会把 APK 发布到 `C:\Services\PhigrosScore\app-update`，并原子更新在线更新清单。旧 APK 不会被删除。

## 2. 验证在线更新

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers

Get-ScheduledTask -TaskName 'PhigrosScore-Caddy' |
    Format-List TaskName, State
```

预期结果：

- `versionCode` 为 `14`；
- `versionName` 为 `Pre-0.9.6.1-Fix`；
- `sha256` 为 `10fa1a8a255f6ec1b58509e82f51294bc38467938eb2359a0068617321c62915`；
- `sizeBytes` 为 `7128284`；
- APK 请求状态为 `200`；
- Caddy 任务状态为 `Running`。

## 3. 可选：核对服务器落盘文件

```powershell
$apk = 'C:\Services\PhigrosScore\app-update\Phi-Score-Query-Pre-0.9.6.1-Fix.apk'
Get-Item -LiteralPath $apk | Select-Object FullName, Length, LastWriteTime
Get-FileHash -LiteralPath $apk -Algorithm SHA256
Get-Content -LiteralPath 'C:\Services\PhigrosScore\app-update\latest.json' -Raw
```
