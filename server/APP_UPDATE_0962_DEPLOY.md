# Pre-0.9.6.2 云服务器部署步骤

以下操作均在 Windows 云服务器上以管理员身份执行。本次必须同时更新 Next-Phi-Backend、Caddy 路由和头像资源；只上传 APK 会导致排行榜资料无法完整显示。

## 1. 上传并执行部署包

将 `app-update-release-Pre-0.9.6.2.zip` 上传到云服务器桌面，然后执行：

```powershell
$zip = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.2.zip'
$deploy = 'C:\Users\Administrator\Desktop\app-update-release-Pre-0.9.6.2'

$zipHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($zipHash -ne '120356a078346986bfc9a6329336c060f9154a9331f8a07049105ebe13d90be4') {
    throw "部署包 SHA-256 不一致: $zipHash"
}

Expand-Archive -LiteralPath $zip -DestinationPath $deploy -Force
Set-ExecutionPolicy -Scope Process Bypass
& "$deploy\Deploy-Pre-0.9.6.2.ps1"
```

脚本会先备份当前后端、排行榜数据库、Caddyfile 和启动脚本；随后验证新 Caddy 配置，更新后端和 109 个头像资源，重启服务，最后发布 versionCode 15 的 APK 与 `latest.json`。如果启动或健康检查失败，脚本会自动恢复原后端和网关配置。

## 2. 验证在线更新和服务状态

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

- `versionCode` 为 `15`；
- `versionName` 为 `Pre-0.9.6.2`；
- `sha256` 为 `299dafa8c17418149b5e485f6e8feb92d5a0ccf412771857b2b2bd8653655962`；
- `sizeBytes` 为 `7169605`；
- APK、健康检查、排行榜和头像的状态码均为 `200`；
- 后端与 Caddy 两项计划任务均为 `Running`。

## 3. 用已登录 APP 做最终验收

安装或覆盖更新 APK 后：

1. 打开 APP 并刷新一次存档，让本账号的昵称、头像、课题等级和最新 RKS 写入排行榜；
2. 进入“排行榜”，确认当前玩家卡片和 Ranklist 正常显示；
3. 进入“单曲成绩”，点开任意曲目，确认完整曲绘、定数、物量与谱师信息；
4. 从屏幕左侧向右滑动，确认侧边导航可在任意页面打开。

历史排行榜记录会在对应玩家下一次刷新存档后逐步补齐昵称、头像与课题等级；升级不会伪造旧记录中原本没有的玩家资料。
