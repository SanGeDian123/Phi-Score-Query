# Pre-0.9.6.8 服务器部署

本次更新需要替换 Next-Phi-Backend、安装新的简约 B30 模板、更新水印配置并发布 APK。Caddy 配置无需修改。

1. 将 `app-update-release-Pre-0.9.6.8.zip` 上传到服务器并解压。
2. 以管理员身份打开 Windows PowerShell。
3. 进入解压目录。
4. 执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.6.8.ps1
```

部署脚本会先备份旧后端、配置、模板、更新脚本和更新清单；随后仅重启 `PhigrosScore-Backend`。任一步骤失败都会自动恢复备份。

部署成功后执行：

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$apkHead | Select-Object StatusCode, Headers

Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'

Get-ScheduledTask 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期版本为 `versionCode 21`、`versionName Pre-0.9.6.8`，APK SHA-256 为 `f64e8e2cdb883ba6549080a943ade8d134aed927bfa611584013ca10e0921092`。
