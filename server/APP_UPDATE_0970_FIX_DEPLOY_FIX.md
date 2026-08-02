# Pre-0.9.7.0-Fix 部署脚本修正版

此修正版不改变 APP 版本和 APK 内容，仅修复云服务器 Windows PowerShell 5 对曲库接口中文 JSON 解码后，部署脚本使用中文曲目 ID 精确匹配而产生的误判。

原部署已经自动回滚，可以直接使用本修正版重新部署。

## 操作步骤

1. 上传并解压 `app-update-release-Pre-0.9.7.0-Fix-deploy-fix.zip`。
2. 以管理员身份打开 PowerShell。
3. 执行：

```powershell
Set-Location 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.7.0-Fix-deploy-fix'
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Deploy-Pre-0.9.7.0-Fix.ps1
```

修正版改用不会受中文编码影响的曲师 `S9ryne` 和曲名 ASCII 结尾 `feat. koi` 验证新曲。若仍失败，错误信息会直接显示接口实际返回的曲目数和新曲匹配数。

成功后执行：

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes

$catalogResponse = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog' -UseBasicParsing
$catalogText = [Text.Encoding]::UTF8.GetString($catalogResponse.RawContentStream.ToArray())
$catalog = $catalogText | ConvertFrom-Json

$catalog | Select-Object version, @{Name='songCount'; Expression={@($_.items).Count}}
$catalog.items |
    Where-Object composer -eq 'S9ryne' |
    Where-Object name -Match 'feat\. koi$' |
    Format-List id, name, composer, illustrator, chartConstants

Get-ScheduledTask 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期版本为 `Pre-0.9.7.0-Fix / versionCode 25`，曲库数量为 312，新曲匹配结果为一首。
