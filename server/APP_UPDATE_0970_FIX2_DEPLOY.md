# Pre-0.9.7.0-Fix2 服务器部署

本版本补全《星拂云锦 feat. koi》的章节、谱师和谱面物量。服务器曲库与曲绘已经由 `Pre-0.9.7.0-Fix` 发布，本次只发布新 APK 和更新清单，不修改或重启后端与 Caddy。

## 部署

1. 上传并解压 `app-update-release-Pre-0.9.7.0-Fix2.zip`。
2. 以管理员身份打开 PowerShell。
3. 执行：

```powershell
Set-Location 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.7.0-Fix2'
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.7.0-Fix2.ps1
```

脚本会校验 APK 和发布脚本，备份原更新清单，发布 `versionCode 26`，再校验 `latest.json`、APK 哈希、文件大小和公网下载地址。失败会恢复原更新清单。

## 验收

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl -UseBasicParsing
$apkHead | Select-Object StatusCode, Headers

Get-ScheduledTask 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期结果：

- `versionCode` 为 `26`；
- `versionName` 为 `Pre-0.9.7.0-Fix2`；
- APK SHA-256 为 `bffa299806f2dd9f452117828f330cbac8e9b9cfdbafe4218d170230801160e5`；
- APK HEAD 状态码为 200；
- 后端和 Caddy 计划任务继续保持 `Running`。
