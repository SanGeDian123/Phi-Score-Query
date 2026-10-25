# Pre-0.9.8.0 / versionCode 112 发布升级包

此包会发布 APP 更新，含签名 Release APK、`app-update/latest.json`、Rust 后端与匹配源码、官网/PWA `Pre-0.9.8.0-web.1+20261025` 和网页源码。与此前“只升级服务器，不发布更新”的包不同，执行完成后老客户端将收到此版本更新。

## 更新内容

- “谱面播放与练习”功能新增打击音效调整设置；
- 支持Best N图片生成，上限200张谱面；
- 新增“时间回溯”模式，开启后，可查看旧定数体系下的RKS及Best 19。
- 新增RKS趋势图，在概览页面点击RKS数字即可进入。
- 优化了使用体验。

PWA 同步 RKS 趋势图、Best N 图片和时间回溯，并更新官网功能说明及网页版本。官网 Android 下载卡片读取发布后的正式更新清单。谱面播放与练习仍为 Android 专属。

## 部署

在原 Windows 服务器的管理员 PowerShell 执行。默认后端目录 `C:\Services\PhigrosScore`，网页目录 `C:\Services\PSQWeb`；原计划任务 `PhigrosScore-Backend`、`PhigrosScore-Caddy`、`PSQ-Web` 必须存在，Node.js >=22.13.0。沿用原 Caddy 域名配置；仅补充 RKS 历史与 Best N 认证 API 路由。ZIP 放到服务器桌面后，下面是一整行“解压、验证、部署”命令：

```powershell
$ErrorActionPreference='Stop'; $z=Join-Path ([Environment]::GetFolderPath('Desktop')) 'server-upgrade-Pre-0.9.8.0-v112-20261025.zip'; $d=Join-Path $env:TEMP ('PSQ-Release-v112-'+[Guid]::NewGuid().ToString('N')); Expand-Archive -LiteralPath $z -DestinationPath $d; $s=Join-Path $d 'scripts\Deploy-Release-Pre-0.9.8.0-v112.ps1'; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $s -ValidateOnly; if($LASTEXITCODE -ne 0){throw 'Validation failed'}; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $s; if($LASTEXITCODE -ne 0){throw 'Upgrade failed'}
```

验证阶段不改文件、不停止任务、不发布更新。正式执行先备份后端、模板、源码、Caddy、官网/PWA 与原更新清单，升级后端与网页，校验本机及公网网页版本，再复制 APK 并最后原子发布 `latest.json`。同时验证公网 APK 体积、更新清单和官网 Android 下载版本。`mandatory=false`，不会强制升级；拒绝覆盖同版本或更高版本。

失败时恢复本次涉及的后端、官网及 APP 更新文件并重启服务，备份保留在后端目录 `backup\release-v112-*`；子升级脚本也保留各自备份。如回退时端口仍被占用，脚本会停止并报告备份位置，不终止未知进程。

保留数据库、用户数据、公告、私密配置、曲库与别名、曲绘、已有模板 JSON、自托管管理后台，以及 PWA 的 `.env`、整个 `.runtime/`、会话密钥、日志和已安装任务启动脚本。包不含服务器秘密、数据库、依赖目录或 APK 签名私钥。

本地构建、模拟接口、部署/失败回退验证和 GitHub 上传均不等于生产部署。Android 真机、iPhone/iPad Safari 和原服务器的公网结果需部署后验收。

## 重新生成

先构建签名 APK、后端和 PWA，再运行 `psq-web/scripts/package-release.mjs`，最后运行 `server/scripts/Build-ReleasePackage-Pre-0.9.8.0-v112.ps1`。默认复用已构建的 RKS 后端升级 ZIP，必须使用与当前源码对应的输入。构建与分发默认输出到 D 盘，正式 APK 单独提供。
