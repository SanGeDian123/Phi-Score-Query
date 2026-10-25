# Next-Phi-Backend 修改说明

本目录是 Phi Score Query 服务所使用的 Next-Phi-Backend 对应源码。

## Pre-0.9.7.12-Fix 排行榜修复

- 排行榜按最新存档计算的 RKS 更新，允许下降和相同分数刷新，避免保留历史最高值导致与个人成绩不符。
- `/save` 在返回成功前完成榜单及文字详情写入；统计、评级样本与猜猜乐快照保留异步处理。写榜失败会向调用方报告。
- 按固定精度 UTC 请求时间防止延迟旧请求回写排行榜或文字详情；保留原隐藏状态、注册时间与公开限制。
- 没有公开名次时仍返回认证用户自己的 RKS；不解除公开限制、不修改历史用户数据。

## 来源

- 上游仓库：<https://github.com/Sczr0/Next-Phi-Backend>
- 基准提交：`3b167614e916b62b7f84606cb044c0590611a9d7`
- 上游许可证：GNU Affero General Public License v3.0
- 修改者：SanGeDian123 / Phi Score Query contributors
- 修改时间：2026 年 7 月至 9 月

## 主要修改

- 2026-10-25：修复 Best 200 长图的过高内存峰值。经典与 Phi-Plugin 长图先在有界分辨率处理背景模糊；栅格化按带重叠的条带处理，PNG 流式编码。image.max_parallel=0 改为 1，显式值最多 2，排队超过 10 秒返回 503；阻塞渲染任务在请求取消后仍保留许可，直到任务真正结束。限制图片宽度 1–2048、总像素不超过 32 Mi、最长边中的高度不超过 16383。保留时间回溯及此前功能。

- 2026-10-24 P1 修正：APP 与后端在 AP 旧定数相同时统一按曲目 ID、EZ/HD/IN/AT 顺序选取 P1，并升级 p1-v2 响应与缓存版本。回溯定数调整为 Desultory Signals IN 16.4、Exoplanetary Mirage IN 16.3，正常模式定数不变。

- 2026-10-24：新增时间回溯生图，使用整理后的 2025-02-14 旧定数与 P1＋Best 19 规则，独立缓存及响应头。旧版 Phi-Plugin B19 双列模板使用 PSQ 水印；经典、简约同步使用旧定数。回溯模式拒绝 Best N、P30 请求，正常模式保持原功能。服务器升级包不发布 APP 更新。

- 2026-10-18：新增 `/image/bestn`，按单谱 RKS 展示前 N 张成绩，复用经典、简约、Phi-Plugin 三种样式。Best N 不添加额外 P3、B30 溢出分区或直方图，并使用独立缓存标识。Best N/B30 的 N 在读取存档、查询图片缓存和排队渲染前校验为 1–200；用户自报成绩图也最多接受 200 条成绩。

- 2026-10-18：Phi-Plugin B30 增加等效 RKS 直方图，还原 PHI / Aldrich 字体、“OVER FLOW”与“B30 数据分析”分隔标题对齐；调整 BP30 卡片光照、遮罩和光晕范围。APP 与 PWA 复用同一模板，P30 不添加直方图。以 Pre-0.9.7.12 服务器样式升级包交付，不修改 APP 发布版本。

- 2026-10-11：新增管理员曲目别名管理 `GET/PUT /admin/songs/aliases`，沿用 `X-Admin-Token`；按歌曲 ID 管理完整别名列表，以内容 revision 防止覆盖其他管理员修改。保存原子写入 `info/song-alias-overrides.json` 后立即替换搜索快照，启动、本地热加载与远端曲库同步均重新应用覆盖；每次升级保留运行时曲库和人工别名数据。

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

## 2026-10-06 用户管理与申诉

- 新增按用户名搜索、公开展示限制、限时账户暂停、解除限制，以及文字和私有图片申诉与管理员审核接口；限制、申诉及审核记录持久化到现有统计数据库。
- 在登录签发、会话续期、已有 Bearer 会话和旧式凭据请求中检查账户暂停；停止公开展示时隐藏公开资料与排行榜，并禁止建议评论。
- 封禁、暂停和公开展示限制的错误提示追加“如需申诉，请将APP更新至最新版本”，供没有图文申诉入口的旧客户端展示。
- 配套 Caddy 路由支持用户管理及申诉附件，部署时仅增补这些路由，保留现有资源与 APP 更新配置。
- 公开展示限制与账户暂停统一排除公开排行榜、求建议帖子、评论与图片，以及谱面评级达成率的统计样本；解除限制后自动恢复，保留原始数据。
- 建议图片原图和预览在读取前校验所属用户及帖子可见性，包括条件请求，禁止通过旧图片地址绕过限制；建议图片改为 no-store。
- 封停提示中的暂停结束时间固定按 UTC+8 展示并附时区标记，供旧版 APP 显示；数据库和状态接口继续使用原始时间戳，实际限制到期时刻保持不变。
- 暂停与封禁提示增加解除限制后重新登录的说明，触发旧客户端在主页刷新失败后关闭功能主界面；旧客户端续期返回 401 以清除登录会话，支持限制专页的新客户端通过 `X-PSQ-Moderation: 1` 保留 403 和申诉凭据。该标记只影响错误处理，不改变服务端限制。
- 对已过期但签名有效的 Bearer 仍检查账户暂停，旧式 JSON 凭据在统一入口检查暂停，读取存档或成绩图缓存之前拒绝访问。服务端不能清除旧客户端内置数据、已缓存文件或强制修改离线界面；完全禁止离线使用需要客户端支持。
- 被禁止公开展示的旧客户端在登录后第一次刷新成绩时返回一次包含限制原因、UTC+8 恢复时间及升级申诉说明的 403，沿用旧版页面底部提示；不包含重新登录触发文案，不清除登录会话，下一次刷新正常读取成绩。提醒按签名会话与限制更新时间去重，旧式 JSON 凭据按账号与限制更新时间去重；有界内存缓存会在服务重启或淘汰后再次提醒。
- 支持限制状态接口的新客户端在 SessionToken 和 TapTap 扫码登录后先读取限制状态，通过现有底部提示显示公开展示限制，继续保留顶部申诉入口，不需要制造成绩读取错误。

## 2026-10-07 受限用户列表

- 新增管理员鉴权的 `GET /api/v2/admin/users/management/restricted`，提供当前有效的公开展示限制与账户暂停用户列表、分类型人数、用户名搜索及每页 20 位的分页；同一用户同时受两类限制时仅返回一条资料。
- 列表和计数共用同一数据库快照与到期判断时刻，排除已解除和到期的限制；按最近限制更新时间排序，末页用户移除后自动调整页码。
- 管理台新增统一列表，沿用搜索用户的资料卡片和限制操作弹窗，支持修改时长、解除公开展示限制和恢复账户；限制变更、申诉审核和定时刷新同步更新列表，所有管理台时间统一显示为 UTC+8。

## 2026-10-08 反馈提交者资料

- 管理员反馈列表关联提交者的当前昵称、RKS、课题模式等级和公开 RKS 排行榜位次；历史反馈无需重新提交，也不需要新增数据库字段。
- 位次沿用公开排行榜的可见性限制和同分稳定排序；私密、隐藏或受限玩家的资料仍可由管理员查看，其公开位次为空。未保存资料使用空值，与真实零分区分。
- 新资料仅通过管理员反馈接口返回，普通用户的反馈提交、查询、附件和通知接口保持原有鉴权及响应结构；新增资料更新、同分排序、历史反馈和受限玩家的回归验证。
