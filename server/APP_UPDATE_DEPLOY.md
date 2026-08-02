# 应用内更新服务部署步骤

以下操作均在 Windows 云服务器上以管理员身份执行。

## 首次启用

1. 将 `app-update-bootstrap-Pre-0.9.6-Fix.zip` 上传到云服务器桌面。
2. 以管理员身份打开 PowerShell，运行：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-bootstrap-Pre-0.9.6-Fix.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-bootstrap-Pre-0.9.6-Fix'
Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Install-AppUpdateSupport.ps1"
```

安装脚本会备份现有 `Caddyfile` 与 `Run-Caddy.ps1`，验证新配置后重启 `PhigrosScore-Caddy`，最后发布 APK 和更新清单。

## 公网验收

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes
$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers
Get-ScheduledTask 'PhigrosScore-Caddy' | Select-Object TaskName, State
```

预期结果：`versionCode` 为 `12`、`versionName` 为 `Pre-0.9.6-Fix`，APK 请求状态为 `200`，Caddy 任务状态为 `Running`。

## 后续发布新版本

后续只需上传新的 Release APK，然后执行发布脚本，不需要再次修改或重启 Caddy。`VersionCode` 必须严格递增：下一版至少使用 `13`。

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& 'C:\Services\PhigrosScore\current\scripts\Publish-AppUpdate.ps1' `
  -ApkPath 'C:\Users\Administrator\Desktop\Phi-Score-Query-Pre-0.9.7.apk' `
  -VersionCode 13 `
  -VersionName 'Pre-0.9.7' `
  -Changelog @( `
    '第一项更新内容。', `
    '第二项更新内容。' `
  )
```

发布脚本会自动计算 APK 的 SHA-256 和文件大小，并在 APK 完整复制后才替换 `latest.json`。

## 版本衔接说明

Pre-0.9.5-Fix 没有更新检查代码，无法自动发现本次更新。现有用户需要手动安装一次 Pre-0.9.6-Fix；从 Pre-0.9.6-Fix 开始，后续版本即可使用应用内更新。
