# 时间回溯 P1 修正版服务器升级包 · Pre-0.9.7.13

仅升级服务器，不发布 APP 更新。APK 单独交付，包内不含 APK、latest.json 或更新发布脚本。本包尚未部署。

## 内容

- 修复文字 B19 与图片 P1 选择不一致：AP 取旧定数最高者；同定数按曲目 ID、难度 EZ→HD→IN→AT 排序，不受存档顺序或显示曲名影响。
- 回溯定数修正：Desultory Signals IN 16.4，Exoplanetary Mirage IN 16.3；正常模式定数不变。
- APP 与服务器均更新图片缓存版本；新 APP 要求 p1-v2 规则响应头，请配套升级服务器与 APK。

- 327 首、1037 张谱面的 2025-02-14 整理定数，与 APP 使用同一份 difficulty_20250214.csv。
- 回溯成绩按 P1＋Best 19 计算，支持经典、简约和旧版 Phi-Plugin B19 图片；PSQ 水印后标注“时间回溯模式，仅供娱乐”。
- 回溯图片使用独立缓存，并返回 X-PSQ-Time-Rewind 响应头；回溯模式拒绝 Best N 和 P30 图片请求。
- 保留正常模式功能；必要时仅向现有 Caddy allowlist 补充 Best N 路由。
- 包含对应后端源码、字体、模板、许可证与部署脚本。旧定数编译进后端，resources 下同时保留对应 CSV。

## 部署

将 ZIP 解压到服务安装目录以外，例如 C:\PSQ-TimeRewind-Upgrade。以管理员身份运行 PowerShell，先校验：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\PSQ-TimeRewind-Upgrade\scripts\Deploy-TimeRewind-Pre-0.9.7.13.ps1" -ValidateOnly
```

显示 VALIDATION OK 后升级：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\PSQ-TimeRewind-Upgrade\scripts\Deploy-TimeRewind-Pre-0.9.7.13.ps1"
```

默认服务目录为 C:\Services\PhigrosScore；可通过 -InstallRoot 指定。默认计划任务为 PhigrosScore-Backend 和 PhigrosScore-Caddy，可通过 -BackendTaskName 和 -CaddyTaskName 指定。

脚本备份受影响文件，短暂重启后端和 Caddy，检查本机与公开健康接口，以及回溯模式对 Best N、P30 的拒绝响应；失败自动恢复备份。现有布局 JSON、运行时曲库及定数、私有配置、用户数据库、别名、曲绘、网页后台、APP 更新清单与公告均保留。源码下载沿用原 /source 文件名。

ValidateOnly 只验证本地包和 Caddy 配置，不部署、不停止服务；本地构建与校验不代表生产部署或真机运行验证。

## ZIP 在桌面且尚未解压：一体命令

在服务器管理员 PowerShell 中整行执行：

```powershell
& { $ErrorActionPreference='Stop'; $desktop=[Environment]::GetFolderPath('Desktop'); $zipPath=Join-Path $desktop 'server-only-upgrade-Pre-0.9.7.13-TimeRewind-P1Fix-20261024.zip'; if (!(Test-Path -LiteralPath $zipPath -PathType Leaf)) { throw "未找到升级包：$zipPath" }; $upgradeDir=Join-Path $desktop ('PSQ-TimeRewind-P1Fix-'+[Guid]::NewGuid().ToString('N')); Expand-Archive -LiteralPath $zipPath -DestinationPath $upgradeDir; $deployScript=Join-Path $upgradeDir 'scripts\Deploy-TimeRewind-Pre-0.9.7.13.ps1'; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $deployScript -InstallRoot 'C:\Services\PhigrosScore' -ValidateOnly; if ($LASTEXITCODE -ne 0) { throw '校验失败，未执行部署。' }; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $deployScript -InstallRoot 'C:\Services\PhigrosScore'; if ($LASTEXITCODE -ne 0) { throw '部署失败，请查看上方报错及回滚结果。' }; Write-Host '部署完成，未发布 APP 更新。' -ForegroundColor Green }
```
