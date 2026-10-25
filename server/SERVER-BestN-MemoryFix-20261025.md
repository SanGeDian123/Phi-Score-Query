# Best 200 服务器内存修复包 · 2026-10-25

仅升级服务器，不发布 APP 更新。保留时间回溯、统一 P1 选择和此前功能。未部署生产服务器。

## 问题与修复

本地独立进程使用 200 张不同真实曲绘、1440 宽度、Release 构建复现：Phi-Plugin 原版峰值约 1355 MiB。长背景裁剪及模糊产生很大的中间缓冲；PNG 曾额外预分配一张完整 RGBA 画布。旧默认并发按 CPU 核数设置，客户端超时重试会放大压力；HTTP 取消后阻塞任务也可能继续执行，而请求持有的并发许可提前释放。

修复后，长背景先在有界分辨率裁剪并模糊，再按原布局输出；长图使用带重叠的分段栅格化和 PNG 流式编码，保留阴影、水印与所有成绩卡片。

- 默认并发从 CPU 核数改为 1；显式设置最多 2。现有 config.toml 无需修改。
- 排队超过 10 秒返回 HTTP 503、IMAGE_RENDER_BUSY 与 Retry-After，不无限堆积。
- 已启动的阻塞任务一直持有许可，直到实际完成，即使请求已中断。
- 图片宽度 1–2048，高度最多 16383，总像素最多 32 Mi；超限返回 422。
- 保留所有时间回溯与 Best N 功能；不降低 Best N 的 200 张上限。

## 本地实测

以下是独立探针进程的采样峰值，不是生产服务器日志。每次 200 条成绩，实际生成并解码 PNG；随机背景固定为同一曲绘以便对比。耗时取决于服务器性能。

| 样式 | 峰值私有内存 | 总耗时 | 输出尺寸 |
| --- | ---: | ---: | --- |
| Phi-Plugin 修复前 | 1354.8 MiB | 26.7 秒 | 1440×10314 |
| Phi-Plugin 修复后 | 152.3 MiB | 35.6 秒 | 1440×10314 |
| 简约修复后 | 82.3 MiB | 10.0 秒 | 1440×9472 |
| 经典修复后 | 120.6 MiB | 33.3 秒 | 1440×10894 |

Phi-Plugin 峰值降低约 89%。分段渲染以较小内存换取部分耗时；图片卡片保持原输出分辨率。线上具体退出原因仍需结合当时的后端日志和系统事件确认。

## 部署

ZIP 上传到服务器桌面后，在管理员 PowerShell 整行执行：

```powershell
& { $ErrorActionPreference='Stop'; $desktop=[Environment]::GetFolderPath('Desktop'); $zipPath=Join-Path $desktop 'server-only-upgrade-Pre-0.9.7.13-BestN-MemoryFix-20261025.zip'; if (!(Test-Path -LiteralPath $zipPath -PathType Leaf)) { throw "未找到升级包：$zipPath" }; $upgradeDir=Join-Path $desktop ('PSQ-BestN-MemoryFix-'+[Guid]::NewGuid().ToString('N')); Expand-Archive -LiteralPath $zipPath -DestinationPath $upgradeDir; $deployScript=Join-Path $upgradeDir 'scripts\Deploy-BestNMemoryFix-Pre-0.9.7.13.ps1'; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $deployScript -InstallRoot 'C:\Services\PhigrosScore' -ValidateOnly; if ($LASTEXITCODE -ne 0) { throw '校验失败，未执行部署。' }; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $deployScript -InstallRoot 'C:\Services\PhigrosScore'; if ($LASTEXITCODE -ne 0) { throw '部署失败，请查看上方报错及回滚结果。' }; Write-Host '部署完成，未发布 APP 更新。' -ForegroundColor Green }
```

脚本校验包内容、对应源码及 Caddy 配置后，备份并替换后端、字体和模板，短暂重启后端及 Caddy。检查本机/公开健康、过大图片宽度拒绝、回溯模式拒绝 Best N/P30；失败恢复备份。支持后端已停止的状态。

保留私有配置、数据库、运行时曲库定数、别名、曲绘、现有布局 JSON、网页、APP 更新和公告。包内不含 APK、latest.json、APP 发布脚本或私有运行数据。-ValidateOnly 不停止服务或部署文件。
