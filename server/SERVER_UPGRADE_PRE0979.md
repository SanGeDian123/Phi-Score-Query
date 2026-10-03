# Pre-0.9.7.9 服务器升级包

本包 `server-only-upgrade-Pre-0.9.7.9-CatalogSync-20260905-Fix1.zip` 用于 `versionCode 41`、`Pre-0.9.7.9` 内部测试环境的服务器升级，包含 RKS 猜猜乐服务端修复、公告历史与管理接口、实时曲库热同步，并修复“给建议”刷新按历史队列回退、可能再次显示已看帖的问题。本包不发布 APP 更新，也不修改 `app-update/latest.json`。

## 部署

将 `server-only-upgrade-Pre-0.9.7.9-Suggestion-Random-Fix.zip` 放在服务器桌面，以管理员身份打开 PowerShell，可在任意目录执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'server-only-upgrade-Pre-0.9.7.9-CatalogSync-20260905-Fix1.zip'; $p=Join-Path $d 'server-only-upgrade-Pre-0.9.7.9-CatalogSync-20260905-Fix1'; if(Test-Path -LiteralPath $p){Remove-Item -LiteralPath $p -Recurse -Force}; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-ServerOnly-Pre-0.9.7.9.ps1')
```

脚本会先验证包内 SHA-256 和 Caddy 配置，再备份现有后端、Caddy、脚本、更新清单和源码归档；部署失败时自动回滚。与本包匹配的 `versionCode 41` 清单允许原样保留，只有高于 41 的 APP 更新会被拦截。更新公告不会自动发布。

## 曲库同步内容

- 完整曲库从 312 首更新为 314 首。
- 新增：`NWAD.Knighthood`、`DevastatingHistory.NAMV`。
- 47 首既有歌曲的定数已更新；6 项 AT 发生变化，`Archidoxen.Se_IRA` 新增 AT 16.9。
- 后端每 5 秒检查 `difficulty.csv`、`info.csv`、`nicklist.yaml`，解析成功后原子替换定数与歌曲索引；远端资料下载后先落盘，避免重启回退。

## 验证

- `/health` 应返回成功。
- 未登录访问 `/api/v2/suggestions/random` 应返回 `401`。
- 访问 `/api/v2/songs/achievement-rates?song_id=probe&difficulty=BAD` 应返回后端参数校验 `422`，而不是 Caddy `404`。
- 已有 APP 更新清单应保持原内容不变；服务器端 RKS 猜猜乐接口应由 Caddy 转发到 `/api/v2/games/rks-guess/*`。
- 未登录访问本机及公网 `/api/v2/games/rks-guess/probe` 均应返回 `401`，不得返回 `502` 或停用时的 `503`。
- `/source` 应下载 `backend-source-Pre-0.9.7.9.zip`。

## 本次建议区随机刷新修复

- 客户端不再维护帖子历史队列，也不再显示右上角 `x/x` 统计。
- 每次随机刷新只排除当前帖子，由服务器重新随机选择其他可用帖子。
- 当没有其他帖子可选时，服务器返回未找到，客户端保留当前帖子并提示“暂时没有新的帖子”。

## 本次崩溃修复

- 匿名题库查询不再 `fetch_all` 读取整张表，每次最多物化 16 份候选快照。
- 单人题库读取限制为 3 秒；超时或坏数据只让当前请求失败，不影响后端进程。
- 快照限制为 30 条线索、256 KiB，并拒绝非有限 RKS/ACC 等异常数值。
- 同一账号重复点击开局会返回原对局，不再重复复制整份线索。
- 进行中但无人继续的对局 10 分钟后回收，对局表最多保留 2048 局。

## 本次玩法与界面配套修复

- 后端增加独立回合结算状态；双方在结算页确认后才进入下一回合作答页。
- 60 秒未作答会直接结束整局并判负，不再创建下一回合。
- 服务器仍会计算双方答案差值并据此判定得分，但 API 不返回任何差值字段。
- 回合结算只公布双方提交答案、胜负和平局判定；真实 RKS 仍只在整局结束后公布。
- 每局按 B30 槽位记录已发放线索；单人 5 回合和公开匹配最多 6 回合内均不会重复出现同一槽位。
- 公开匹配状态只返回请求玩家自己的真实昵称，不再通过 API 返回对方昵称；客户端统一显示“对方玩家”。
