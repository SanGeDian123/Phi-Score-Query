# Pre-0.9.6.4 云服务器部署步骤

本次更新同时包含 Android 客户端和 Next-Phi-Backend。排行榜头像的根因位于后端，因此不能只上传 APK。

## 1. 上传并执行完整更新包

将修正版 `app-update-release-Pre-0.9.6.4-deploy-fix.zip` 上传到云服务器桌面。它只修复部署脚本的 Windows PowerShell 5 UTF-8 读取问题，APP 版本仍为 `Pre-0.9.6.4`。然后以管理员身份打开 PowerShell，执行：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.4-deploy-fix.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.4-deploy-fix'

$zipHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($zipHash -ne '2489ba4bebd33c6c247d2c55ebdf906a79ffa027ac234487466ea097476399bc') {
    throw "部署包 SHA-256 不一致: $zipHash"
}

Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Deploy-Pre-0.9.6.4.ps1"
```

部署脚本会先校验 APK、后端程序、Caddy 配置及 109 个头像文件；停止后端和 Caddy 后，备份原后端、`usage_stats.db` 及其 WAL/SHM 文件、Caddy 配置、更新清单和头像资源；随后替换后端并进行本机健康检查，最后发布 versionCode 17 的 APK 与 `latest.json`。任何一步失败都会自动恢复原后端、排行榜数据库、Caddy 配置、头像和更新清单。

## 2. 验证在线更新与服务状态

部署脚本成功后，在服务器 PowerShell 中执行：

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes

$apkHead = Invoke-WebRequest -Method Head $latest.apkUrl
$health = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/health'
$ranklist = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/leaderboard/rks/top?limit=3&lite=true'

$avatarName = 'Glaciaxion'
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $avatarKey = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($avatarName)))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha.Dispose()
}
$avatarHead = Invoke-WebRequest -Method Head "https://api.plc-liangpi-cup.xyz/avatar/$avatarKey.png"

[pscustomobject]@{
    ApkStatus = $apkHead.StatusCode
    HealthStatus = $health.StatusCode
    RanklistStatus = $ranklist.StatusCode
    AvatarStatus = $avatarHead.StatusCode
}

Get-ScheduledTask -TaskName 'PhigrosScore-Backend', 'PhigrosScore-Caddy' |
    Select-Object TaskName, State
```

预期结果：

- `versionCode` 为 `17`
- `versionName` 为 `Pre-0.9.6.4`
- `sha256` 为 `95fb447febe6819a386c1c6e72e4277498d3bb80ea9b9521b2eca9809cefc4f4`
- `sizeBytes` 为 `7186069`
- APK、健康检查、排行榜和头像状态码均为 `200`
- 后端与 Caddy 两项计划任务均为 `Running`

## 3. 在 APP 中做最终验收

1. 覆盖安装新 APK，进入排行榜并刷新一次存档；确认当前玩家头像和详细排名头像能够显示。
2. 从排行榜、单曲成绩或设置页按一次手机返回键，确认回到“成绩概览”；在主页按一次出现退出提示，2 秒内再按一次才退出。
3. 打开更新日志，确认 Pre-0.9.6.4 标记为“当前版本”，Pre-0.9.6.2 显示“曲目详情与排行榜”。
4. 使用尚未触发过该功能的账号登录并等待成绩刷新；确认后台生成一次 B30 成绩图，并在成绩概览最下方显示。
5. 点击保存 B30 成绩图；确认不再弹出路径选择器，且图片直接出现在系统相册中。

旧排行榜记录中的非法头像值无法凭空恢复。新后端会先隐藏非法值并显示默认头像；对应玩家下次刷新存档后，后端会从 `user.dat` 写入经过官方头像表校验的正确头像。
