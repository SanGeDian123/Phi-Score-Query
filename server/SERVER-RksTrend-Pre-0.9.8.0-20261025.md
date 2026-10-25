# Pre-0.9.8.0 RKS 趋势图配套服务器升级包

仅升级服务器，不发布 APP 更新。APK 单独交付；本包不包含 APK、`latest.json`、APP 发布脚本、私有配置或用户数据库。尚未部署生产服务器。

## 包含内容

- 补齐现有 Caddy 白名单中的 `/api/v2/rks/history`，沿用后端 Bearer 认证。
- 历史接口提供 RKS、变化量、同步记录时间、当前值、最高值及游标分页；不伪造过去的达成时间。
- 当前完整后端及匹配源码、字体和模板，保留时间回溯 P1 v2、Best N 及 Best 200 内存修复。
- APP 版本名为 Pre-0.9.8.0，versionCode 111；进入过趋势图后，概览页不再显示额外的文字入口，大 RKS 数字仍可点击。

## 部署

将 `server-only-upgrade-Pre-0.9.8.0-RksTrend-20261025.zip` 上传到服务器桌面，以管理员身份打开 PowerShell，粘贴执行：

```powershell
& {
    $ErrorActionPreference = 'Stop'
    $zipPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'server-only-upgrade-Pre-0.9.8.0-RksTrend-20261025.zip'
    if (!(Test-Path -LiteralPath $zipPath -PathType Leaf)) { throw "未找到升级包：$zipPath" }
    $upgradeDir = Join-Path ([Environment]::GetFolderPath('Desktop')) ('PSQ-RksTrend-' + [Guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $zipPath -DestinationPath $upgradeDir
    $deployScript = Join-Path $upgradeDir 'scripts\Deploy-RksTrend-Pre-0.9.8.0.ps1'
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File $deployScript -InstallRoot 'C:\Services\PhigrosScore' -ValidateOnly
    if ($LASTEXITCODE -ne 0) { throw '校验失败，未执行部署。' }
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File $deployScript -InstallRoot 'C:\Services\PhigrosScore'
    if ($LASTEXITCODE -ne 0) { throw '部署失败，请查看上方报错及回滚结果。' }
    Write-Host '部署完成，未发布 APP 更新。' -ForegroundColor Green
}
```

默认安装目录为 `C:\Services\PhigrosScore`；默认计划任务为 `PhigrosScore-Backend` 与 `PhigrosScore-Caddy`。如实际不同，可使用 `-InstallRoot`、`-BackendTaskName`、`-CaddyTaskName` 指定。

脚本先检查包与匹配源码、实际 Caddy 配置语法，再备份受影响文件并短暂重启后端和 Caddy。检查本机及公网健康、图片请求限制，以及匿名历史查询是否返回预期的 401；失败恢复备份。`-ValidateOnly` 不替换服务文件或停止计划任务。

保留 APP 更新清单、APK 与公告、私有配置、数据库、运行时曲库与定数、别名、曲绘、谱面、已有模板布局 JSON、PWA 与管理网页。对应源码下载沿用现有 `/source` 文件名。

本地构建、脚本演练和 `-ValidateOnly` 仅证明本地校验结果，不代表已经完成生产部署、真实账号历史查询或真机安装验证。
