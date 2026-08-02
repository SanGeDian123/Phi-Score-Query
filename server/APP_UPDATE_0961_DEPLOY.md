# Pre-0.9.6.1 云服务器部署步骤

以下操作均在 Windows 云服务器上以管理员身份执行。本次不需要重编译或重启 Next-Phi-Backend；部署脚本只会放行后端已有的歌曲别名搜索接口、重启 Caddy，并发布新 APK。

## 1. 上传并执行部署包

将 `app-update-release-Pre-0.9.6.1.zip` 上传到云服务器桌面，然后执行：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.1.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.1'

Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Deploy-Pre-0.9.6.1.ps1"
```

脚本会备份当前 Caddyfile、放行 `/api/v2/songs/search`、验证并重启 Caddy，最后发布 versionCode 13 的 APK 和更新清单。

## 2. 验证更新清单与 APK

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers

Get-ScheduledTask -TaskName 'PhigrosScore-Caddy' |
    Format-List TaskName, State
```

预期 `versionCode` 为 `13`、`versionName` 为 `Pre-0.9.6.1`，APK 状态码为 `200`，Caddy 状态为 `Running`。

## 3. 验证别名接口已穿过 Caddy

```powershell
try {
    Invoke-WebRequest `
      -Uri 'https://api.plc-liangpi-cup.xyz/api/v2/songs/search?q=Anomaly&limit=3' `
      -Headers @{ Authorization = 'Bearer invalid' } `
      -UseBasicParsing
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

预期返回 `401`，表示请求已经到达需要登录鉴权的 Next-Phi-Backend；如果仍为 `404`，说明 Caddy 尚未加载新配置。
