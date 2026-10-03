# Next-Phi-Backend 修改说明

本目录是 Phi Score Query 服务所使用的 Next-Phi-Backend 对应源码。

## 来源

- 上游仓库：<https://github.com/Sczr0/Next-Phi-Backend>
- 基准提交：`3b167614e916b62b7f84606cb044c0590611a9d7`
- 上游许可证：GNU Affero General Public License v3.0
- 修改者：SanGeDian123 / Phi Score Query contributors
- 修改时间：2026 年 7 月至 9 月

## 主要修改

- 2026-09-24：Pre-0.9.7.10 发布版，整合当前后端功能、API、管理统计与自定义 B/P30 成绩图支持。

- 2026-09-22：自定义 P30 同样允许顶部 P1-P3 在下方列表再次出现；顶部三份与下方三十三份分别校验排序和重复，P4-P30 参与计算，P31-P36 为溢出。

- 2026-09-19：自定义 B30 允许 P1-P3 的 AP 谱面同时出现在 Best 列表；仍禁止 AP 组内或 Best 组内重复，综合 RKS 按 AP3 与 Best27 计算。

- 2026-09-16 图片性能修复：曲绘和建议图片支持 `?preview=1` 高质量 WebP 预览，保留原图；增加有界内存缓存、持久化预览缓存、相同请求合并及两路阻塞转码上限。预览失败回退原图，曲绘错误响应禁止缓存。
- 签到推荐优先复用同账号同区服最近十分钟的已解析存档；正常更新存档仍检查上游元数据并刷新快照。冷请求最多等待八秒，RKS 计算移至阻塞线程，新增阶段耗时日志。

- 2026-09-16：签到名句抽取范围由 8 条扩至 56 条，与客户端追加后的名句索引对应，保留历史签到索引。
- 公开匹配回合结算增加 60 秒确认期限；单方未确认判负，双方均未确认则结束且不计胜者。

- 新增 RKS 猜猜乐累计胜场榜：单人/公开匹配的最终胜利按游戏 ID 去重持久化，支持本人胜场、模式明细、前 100 名排序，并遵循资料公开及隐藏设置；历史内存对局不补算。

- 增加客户端使用的完整曲库目录接口及稳定版本标识。
- 扩展排行榜轻量返回字段和相关数据库查询。
- 扩展存档响应，提供客户端成绩比较需要的数据。
- 增加经典与简约 B30 图片模板、版本水印及渲染参数。
- 增加“求建议/给建议”成绩图发布、随机抽取、评论、作者删除、指定帖子、我的帖子、评论通知与持久化媒体接口。
- 将 Phi-Plugin B30/P30 的 P1-P3 卡片光晕改为金色，并保持原光晕范围和强度。
- 补充相关 API、渲染和排行榜回归测试。
- 更新部署所用歌曲资料与别名数据。
- 增加服务端权威的“RKS 猜猜乐”单人/公开匹配接口，支持 5 回合、60 秒限时、两位小数四舍五入和第 6 回合耗时决胜；单人题目改为排除当前账号的脱敏匿名存档快照，并修复答案提前公开、双超时、第 6 回合状态、准确命中判胜和线索历史保留。
- 修复 RKS 猜猜乐开局可能拖垮后端进程的问题：匿名题库改为有界抽样，增加读取超时、快照大小与数值校验、重复开局复用、遗留对局回收和全局对局数量上限。
- RKS 猜猜乐按 B30 槽位记录整局已发放线索，单人 5 回合和公开匹配最多 6 回合均不会重复；公开匹配 API 不再返回对方昵称。
- 修复 RKS 猜猜乐回合流程与公开数据：增加独立回合结算状态和下一回合确认接口；60 秒未作答直接判负；差值仅在服务端参与判定且不再序列化；多人返回官方昵称或服务器已保存的昵称/别名。
- 将两处一分钟回退值从 `Duration::from_mins(1)` 等价改写为 `Duration::from_secs(60)`，兼容部署构建工具链。
- 增加曲库运行时热同步：服务端每 5 秒检查本地 `difficulty.csv`、`info.csv`、`nicklist.yaml`，并跟踪远端 info 的 ETag；完整解析成功后以原子快照同时替换定数和歌曲索引，远端成功下载的资料会先持久化到本地再切换，重启后不会因旧 ETag 回退。
- 本次曲库资料来自上游提交 `ec00ae90b170565f72b8e209e6824ea6f0c3000e`：歌曲由 312 首增至 314 首，新增 `NWAD.Knighthood`、`DevastatingHistory.NAMV`；另有 47 首既有歌曲的定数更新，其中包含 6 项 AT 变化（含 `Archidoxen.Se_IRA` 新增 AT 16.9）。

完整逐行修改可以将本目录与上述基准提交进行比较。Pre-0.9.7.9 内测服务器升级包中的 `source/backend-source-Pre-0.9.7.9.zip` 与当前服务端二进制对应，部署后也可通过 `/source` 获取。

## 构建

```powershell
$env:CARGO_TARGET_DIR='D:\CodexBuild\PhigrosApp0978'
cargo test --locked
cargo build --locked --profile release-dist --target x86_64-pc-windows-msvc
```

运行时配置以 `config.example.toml` 和仓库 `server/backend/config.toml` 为模板。不得把生产密钥或用户数据写入公开配置。

## 许可证义务

本目录整体继续使用 GNU AGPL v3。分发二进制或通过网络提供修改版服务时，必须保留许可证、修改声明，并向用户提供该版本完整对应源码。

## 2026-09-06 Daily checkin

Add authenticated daily checkin, Shanghai date boundaries, atomic unique reward ledger (15-50 Coin, 0-100 luck), monthly history, privacy-aware top-100 cumulative leaderboard, personalized Best27 improvement suggestion and admin count/rate trend. Existing app-update metadata is preserved.

- 2026-09-08：增加私有反馈提交、图片存储、按账号查询、管理员处理、版本化未读状态和通知接口，沿用现有用户与管理员鉴权。

## Pre-0.9.7.11 practice catalog

- Added public `GET /api/v2/songs/catalog?practice=true` to enumerate valid RPE PEZ files in the installed practice directory without changing the normal song catalog or its ETag contract.
- File additions, removal, rename and changed archives are detected per request; unchanged archive metadata is cached by size and modification time. Returned charts resolve against the current song catalog and sort by difficulty.
- Added filesystem discovery tests and a public route/ETag integration test. No APP update is published by the server package.

- 2026-10-01: practice catalog includes size/revision and conditional ETag responses; thumbnail WebP previews and conditional GET/HEAD; preserve media cache headers through gateway.
