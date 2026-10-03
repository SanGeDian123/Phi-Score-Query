# 资源加载优化 / Pre-0.9.7.11 v92

APP：2–4 MiB 谱面使用 2 段下载，4 MiB 以上使用 4 段；小文件按目录提供的大小直接下载，省去额外 HEAD 请求。暂停续传保留分段；不支持 Range 时回退单连接。已经完整校验且本地文件和服务端版本均未变化时，打开谱面不再重复解压校验整个包。音画解压缓存也按源文件版本复用。

服务器目录新增 sizeBytes、revision，支持 ETag / 304。旧客户端仍可读取目录；新版客户端从无版本的旧缓存迁移时，可能重新下载一次以确认资源版本。

列表曲绘使用最大 720×405 的 WebP；单曲普通预览使用最大 1280×720 的 WebP。放大查看、保存和生成成绩图继续读取原图。服务器保留原图 URL，预览支持 ETag，并按源文件大小与修改时间更新磁盘缓存。公共资源连接池复用 TLS 连接；图片请求队列与 API 队列独立。

网关将全局 no-store 改为缺省值，保留后端资源缓存头。谱面、APK、ZIP 使用 must-revalidate，继续由 Caddy 静态文件服务提供 Range/ETag。部署脚本仅修补已安装 Caddyfile 中的对应缓存配置，保留服务器自定义路由。

升级包替换后端、配套源码归档和上述网关配置，短暂重启 Backend/Caddy；失败自动恢复。APK 单独安装，升级包不会发布 APP 更新，也不改动现有谱面文件。

将服务器 ZIP 上传至服务器桌面，在管理员 PowerShell 运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$dest = Join-Path $desktop ('PSQ-Resource-v92-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath (Join-Path $desktop 'server-only-upgrade-Pre-0.9.7.11-v92-ResourcePerformance-Fix1.zip') -DestinationPath $dest
Set-Location $dest
& .\scripts\Deploy-ResourcePerformance-v92.ps1 -ValidateOnly
# 校验通过后正式部署：
& .\scripts\Deploy-ResourcePerformance-v92.ps1
```

本地测试和构建不代表生产网速或真机效果。部署后需在实际设备比较首次下载、暂停续传、再次打开和滚动曲绘列表。

Fix1：写入临时 Caddy 配置前先创建系统临时目录，兼容远程桌面会话的 `Temp\2` 等目录缺失的情况。后端与 APK 均不需要重新构建。
