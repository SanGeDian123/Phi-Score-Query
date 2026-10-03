# Pre-0.9.7.9 仅服务器升级包

这是给 `Phi-Score-Query-Pre-0.9.7.9` 内部测试环境使用的 `CatalogSync-20260905-Fix1` 服务器端升级包。该版本及其全部功能均处于内部测试阶段。

Fix1 将部署脚本保存为带 BOM 的 UTF-8，兼容 Windows PowerShell 5.1；如果你手上的旧包报 `$Scope`、`Public` 或“字符串缺少终止符”，请改用 Fix1 包。

## 安全边界

- 本包不包含 APK。
- 本包不包含 `Publish-AppUpdate.ps1`。
- 部署脚本不会发布、复制或覆盖 APP 更新文件。
- `C:\Services\PhigrosScore\app-update\latest.json` 会在部署前后做存在性和 SHA-256 校验；内容必须保持不变。
- 如果现有 `latest.json` 已经宣告高于 `versionCode 41` 的 APP 更新，脚本会在修改任何服务器文件前停止；与本包匹配的 `versionCode 41 / Pre-0.9.7.9` 清单可以原样保留。
- `latest.json` 必须是合法 JSON；如果服务器上已有损坏的更新清单，也会在修改前停止，请先从备份恢复它。

## 部署

请使用管理员 PowerShell，在解压后的包根目录运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
& .\scripts\Deploy-ServerOnly-Pre-0.9.7.9.ps1
```

如果只是执行 `-ValidateOnly`，而 Caddy 不在当前服务目录，可显式指定一个 Caddy 可执行文件用于配置校验：

```powershell
& .\scripts\Deploy-ServerOnly-Pre-0.9.7.9.ps1 -CaddyExecutable 'D:\tools\caddy.exe'
```

正式部署时，服务目录仍必须存在 `C:\Services\PhigrosScore\current\caddy\caddy.exe`；本包不替换 Caddy 二进制文件。

先只校验包而不修改服务器：

```powershell
& .\scripts\Deploy-ServerOnly-Pre-0.9.7.9.ps1 -ValidateOnly -CaddyExecutable 'D:\tools\caddy.exe'
```

部署内容包括 Pre-0.9.7.9 后端、RKS 猜猜乐崩溃与玩法修复、完整 314 首曲目资料（`info.csv`、`difficulty.csv`、`nicklist.yaml` 等）、Phi-Plugin 字体与评级素材、Caddy 路由、运行脚本和后端源码归档。曲库基线为 312 首，本次新增 `NWAD.Knighthood`、`DevastatingHistory.NAMV`，并更新 47 首既有歌曲的定数；其中 6 项 AT 发生变化，包含 `Archidoxen.Se_IRA` 新增 AT 16.9。服务端每 5 秒检查三份 info 文件并原子切换完整快照，远端 info 下载成功后先持久化再切换，前端下一次同步即可获得同一版本。

单人模式从排除当前账号的匿名存档快照中固定抽题；抽题采用有界读取，并保留超时、坏数据校验、重复开局复用、遗留对局回收和对局总量上限。整局按 B30 槽位去重，单人 5 回合和公开匹配最多 6 回合内不会重复。公开匹配只返回我方真实昵称，对方统一匿名显示为“对方玩家”；60 秒未作答直接判负，差值只在服务端计算且永不通过 API 公布。公告模块会把旧版 `latest.json` 自动迁移为历史 `index.json`，并提供按发布时间倒序的管理列表、新增、编辑和历史删除；最新公告保留为弹窗兼容入口且不可删除。本包不会产生 APP 更新提示。

本次服务器修复同时调整“给建议”随机刷新：每次请求严格排除当前帖子，不再通过客户端历史队列回退；若没有其他可用帖子，保持当前页面并提示“暂时没有新的帖子”，不会把原帖当作一次新刷新返回。

失败时只回滚服务器文件，绝不回滚或删除 `app-update` 下的文件。

Safe 包会先在 Caddy 尚未启动时检查本机健康、认证边界并持续观察后端 30 秒，确认稳定后才开放 Caddy；随后检查公网健康、曲库和小游戏认证路由。任何部署后检查失败时，脚本会等待新进程完全停止、恢复部署前文件、确认旧后端健康并稳定运行后才重新启动 Caddy。详细失败步骤与日志末尾会写入 `C:\Services\PhigrosScore\logs\server-only-deploy-failure-*.log`。

服务器的 `app-update` 目录可能包含体积很大的历史 APK；Fix2 不再对整个目录执行部署前后全量 SHA-256。隔离边界改为：包内硬性禁止 APK、`latest.json` 和 `Publish-AppUpdate.ps1`，部署前后只校验实际控制 APP 发布状态的 `app-update/latest.json` 内容哈希。部署脚本仍没有任何写入 `app-update` 的代码路径。
