# 签到名句与 RKS 猜猜乐服务器升级包

配套 APK：Pre-0.9.7.9（versionCode 57），请单独安装。
本包不含 APK，不修改 app-update/latest.json，不发布 APP 更新通知。

## 本次变化

- 签到名句随机范围从 8 条扩至 56 条，旧签到索引保持不变。名句正文由新版 APK 提供。
- 包含上一轮公开匹配回合结算 60 秒确认期限：单方未确认判负，已确认方获胜；双方均未确认则无胜者。
- 自定义 BP30 固定右上角 RKS 属于客户端功能，需要安装配套 APK。

本包由当前工作区后端源码构建，包含此前自定义 BP30、反馈、胜场榜等已有功能。
只替换后端程序及配套源码归档；保留 Caddy 配置、运行脚本、数据库、凭据、曲库和图片资源。
部署会短暂重启后端，进行中的内存对局会中断，请选择空闲时段。
默认已有服务目录：C:\Services\PhigrosScore；计划任务：PhigrosScore-Backend。
脚本会备份替换目标、检查本地和公网接口；失败时恢复旧程序及源码归档。

## 部署

将 ZIP 上传至服务器桌面，在服务器管理员 PowerShell 执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Expand-Archive -LiteralPath "$env:USERPROFILE\Desktop\server-only-upgrade-Pre-0.9.7.9-RksCheckin-20260916.zip" -DestinationPath "$env:USERPROFILE\Desktop\server-only-upgrade-Pre-0.9.7.9-RksCheckin-20260916"
Set-Location "$env:USERPROFILE\Desktop\server-only-upgrade-Pre-0.9.7.9-RksCheckin-20260916"
& .\scripts\Deploy-RksCheckin-Pre-0.9.7.9.ps1 -ValidateOnly
```

校验通过后执行：

```powershell
& .\scripts\Deploy-RksCheckin-Pre-0.9.7.9.ps1
```

-ValidateOnly 仅检查包结构、程序格式、源码及禁入文件，不停止服务，也不代表已部署。
本次按要求跳过文件哈希检查。本包没有执行提交、推送、部署或更新发布。