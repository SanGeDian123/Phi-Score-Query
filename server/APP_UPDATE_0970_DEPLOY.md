# Pre-0.9.7.0 服务器部署

本版本需要同时更新 Next-Phi-Backend、Caddy 配置和 APP。部署完成后，APP 会从 `/api/v2/songs/catalog` 同步服务器曲库，并以本地缓存支持离线使用。

## 部署步骤

1. 将 `app-update-release-Pre-0.9.7.0.zip` 上传到云服务器桌面。
2. 解压到 `C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.7.0`。
3. 以管理员身份打开 PowerShell。
4. 执行：

```powershell
Set-Location 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.7.0'
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.7.0.ps1
```

脚本会先校验 APK、后端、Caddyfile 和发布脚本的 SHA-256，再验证新 Caddy 配置；之后备份并替换后端和 Caddyfile，检查本机与公网曲库接口，最后发布 versionCode 24 的 APK 和 `latest.json`。任何一步失败都会恢复原后端、Caddy 配置和更新清单。

## 验收

部署脚本成功结束后执行：

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog

$catalogResponse = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog'
$catalog = $catalogResponse.Content | ConvertFrom-Json
$catalog | Select-Object version, @{Name='songCount'; Expression={@($_.items).Count}}
$catalogResponse.Headers | Select-Object ETag, 'Cache-Control'

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers

Get-ScheduledTask 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期结果：

- `versionCode` 为 `24`；
- `versionName` 为 `Pre-0.9.7.0`；
- APK SHA-256 为 `9be5373cf62c052a355148e61b37fcac6c2b08d1bf3b7e7cb881818f089a0975`；
- 曲库 `version` 为 64 位十六进制字符串，`songCount` 大于 0；
- APK HEAD 状态码为 200；
- 两个计划任务均为 `Running`。

部署完成后，所有已安装旧版本的用户都可以通过自动检查更新或设置中的手动检查更新获取 `Pre-0.9.7.0`。新版本首次启动会同步服务器曲库；以后只在曲库内容变化时下载新目录。
