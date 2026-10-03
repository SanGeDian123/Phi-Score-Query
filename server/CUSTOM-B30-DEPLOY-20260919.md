# 自定义 B30 算法修复配套服务器包

配套 APK：Pre-0.9.7.9（versionCode 59），请单独安装。
本包不含 APK，不修改 app-update/latest.json，不发布 APP 更新通知。

## 本次变化

- 自定义 B30 允许 P1-P3 的 AP 谱面同时进入 Best1-27；AP 组和 Best 组内仍各自禁止重复。
- 综合 RKS 保持 AP3 与 Best27 之和除以 30；Best28-33 溢出不计入。
- 配套 APK 自动映射 AP、按填写情况实时排序，并包含数字双向动画、反馈必填提示。
- 未部署本包时，旧后端会将 APK 提交的 AP/Best 重叠成绩拒绝为重复谱面。
- 本包包含此前图片预览缓存、签到名句与公开匹配确认期限等已实现功能。

本包由当前工作区后端源码构建，包含此前自定义 BP30、反馈、胜场榜等已有功能。
只替换后端程序及配套源码归档；保留 Caddy 配置、运行脚本、数据库、凭据、曲库和图片资源。
部署会短暂重启后端，进行中的内存对局会中断，请选择空闲时段。
默认已有服务目录：C:\Services\PhigrosScore；计划任务：PhigrosScore-Backend。
脚本会备份替换目标、检查本地和公网接口；失败时恢复旧程序及源码归档。

## 部署

将 ZIP 上传至服务器桌面，在服务器管理员 PowerShell 执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Expand-Archive -LiteralPath "$env:USERPROFILE\Desktop\server-only-upgrade-Pre-0.9.7.9-CustomB30-20260919.zip" -DestinationPath "$env:USERPROFILE\Desktop\server-only-upgrade-Pre-0.9.7.9-CustomB30-20260919"
Set-Location "$env:USERPROFILE\Desktop\server-only-upgrade-Pre-0.9.7.9-CustomB30-20260919"
& .\scripts\Deploy-RksCheckin-Pre-0.9.7.9.ps1 -ValidateOnly
```

校验通过后执行：

```powershell
& .\scripts\Deploy-RksCheckin-Pre-0.9.7.9.ps1
```

-ValidateOnly 仅检查包结构、程序格式、源码及禁入文件，不停止服务，也不代表已部署。
本次按要求跳过文件哈希检查。本包没有执行提交、推送、部署或更新发布。