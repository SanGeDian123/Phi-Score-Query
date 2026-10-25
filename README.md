# Phi Score Query

本项目为非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。

Phi Score Query 是一个面向 Android 8.0 及以上系统的非官方 Phigros 成绩查询客户端。当前客户端源码版本为 **Pre-0.9.8.0（versionCode 112）**。

项目提供 Android 客户端、可自托管的 PSQ PWA，以及线上服务实际使用的 Next-Phi-Backend 修改版对应源码。

网页版与 Android `Pre-0.9.8.0` 同步，支持 RKS 趋势图、Best N 图片生成与时间回溯，排除谱面练习、播放和资源管理。官网 `/` 展示功能、正式 Android 更新信息及网页版本，`/app/` 为网页版，`/preview/` 为官网预览；当前已过自动开放时间，可直接登录。部署保留原会话与缓存。页面清单见 [psq-web/ROUTES.md](psq-web/ROUTES.md)，源码及部署说明见 [psq-web/README.md](psq-web/README.md)。本地验证与 GitHub 发布不代表生产服务器已部署；iPhone / Safari 仍需真机验证。

Pre-0.9.8.0 保留原 Pre-0.9.7.13 的全部更新内容，并新增 RKS 趋势图，在概览页面点击 RKS 数字即可进入。经典、简约、Phi-Plugin 三种样式的 Best N 图片生成支持 N 为 1–200，生成时沿用设置中选定的样式，并支持预览、保存与分享；谱面播放与练习支持打击音效音量调整。保留此前排行榜修复及全部功能与优化。正式签名 APK（versionCode 112）单独交付，并提供[发布更新的整体服务器升级包](server/RELEASE-Pre-0.9.8.0-v112.md)，包含后端、官网/PWA 和 Android 更新清单；生产服务器须自行执行部署命令。

Best 200 内存修复采用分段渲染、PNG 流式编码及长背景预处理；默认单任务、最多两个任务同时渲染，排队超过 10 秒返回繁忙。阻塞渲染在请求中断后继续持有许可。图片宽度支持 1–2048。配套[服务器修复包](server/SERVER-BestN-MemoryFix-20261025.md)保留时间回溯等现有功能，不发布 APP 更新，尚未部署生产服务器。

> 请勿在 Issue、日志、截图或其他公开位置提交 SessionToken、Access Token 或其他账号凭据。

## 功能

- 大陆版 TapTap 扫码和 SessionToken 登录
- B30、P30、Best N等成绩查询
- 单曲查询、定数表和玩家 RKS 排行榜（展示前 1,500 名）
- RKS 趋势图：点击概览页大 RKS 数字进入，首次使用以页面内提示引导；支持近 7 / 30 / 90 天及全部记录，所选区间内的数据自动加载完整，无需手动展开。支持点选与横向滑动查看节点、四位小数 RKS 与变化量、记录时间及历史最高；顶部返回键固定，滚动明细时仍可随时返回。沿用日夜主题、项目字体及原生过渡动画。数据来自现有 `/api/v2/rks/history`，时间为同步记录时间；时间回溯模式不混入现代 RKS 历史。
- 趋势图首次引导完成后恢复概览原有数字布局，不保留额外文字入口。配套 [Pre-0.9.8.0 服务器升级包](server/SERVER-RksTrend-Pre-0.9.8.0-20261025.md) 补齐历史接口的 Caddy 白名单，不发布 APP 更新。
- 时间回溯：从设置页“清除缓存”上方进入，携带当前存档按整理后的 2025-02-14 旧定数与 P1＋B19 规则重新计算；仅保留概览、B19、图片、定数表、设置，成绩文字与图片均仅支持 B19，不支持 Best N 和 P30，Phi-Plugin 使用旧版双列 B19 模板；文字与图片的同定数 P1 按曲目 ID、难度统一选择。确认页面适配手机、平板及横屏，打开、取消、进入模式和退出模式均带原生过渡动画。可从设置退出，重启 APP 自动退出；回溯生图需要配套[时间回溯服务器升级包](server/SERVER-TimeRewind-Pre-0.9.7.13-20261024.md)，历史独立包不发布 APP 更新；当前整体发布包已包含此功能。
- 谱面播放与分段练习，支持预览、变速、手动游玩、AUTOPLAY 和谱面资源管理
- 支持保留 blockAreaList 的谱面噪域、噪域兼容模式和首次打开提示；修复官谱 Hold 长度与部分设备的显示问题
- 打击特效采用 APK 内的 30 帧动画与粒子配置，独立随机散射；拖曳用共享积分表近似，加载时预计算
- 多种样式（经典、简约、Phi-Plugin）的 B30/P30/Best N 图片生成、保存与分享；Best N 图片按单谱 RKS 排序展示前 N 张成绩（1–200），不额外添加 P3
- RKS 计算器，支持真实 B30 替换线提升估算和分数/ACC 计算
- “RKS 猜猜乐”小游戏，支持单人模式与两人公开匹配；主页累计胜场榜汇总两种模式，按整场胜利去重入库，并遵循资料公开与隐藏设置（从配套后端升级后开始累计）
- 每日签到支持 Coin 和累计天数逐位跳动；签到卡曲绘采用平滑模糊，图片详情复用成绩图的双指缩放与拖动查看
- 谱面评级达成率，可按定数选择谱面或使用曲目别名搜索，查看 AP 至 F 的独立样本分布与我的成绩百分位
- 自定义 BP30/P30 成绩图，支持导入当前账号的 B30/P30 后继续编辑、P1-P3 自动映射和 RKS 预览；输入法收起后再排序，外接键盘在输入框失焦后排序
- 服务器曲库同步与离线缓存
- 管理后台支持按曲目添加、编辑和删除别名，支持按行粘贴批量添加；保存后立即同步服务器搜索，别名持久化并支持并发修改检查。服务器升级及部署命令见 [曲目别名管理](server/SERVER-SongAliases-20261011.md)。
- 管理后台支持按用户名搜索玩家资料、停止公开展示、限时暂停账户与解除限制，并提供受限用户列表、类型筛选、用户名搜索及分页管理。
- 用户端支持封禁申诉。
- 被禁止公开展示的用户通过 SessionToken 或 TapTap 扫码首次登录时收到页面底部限制提醒，顶部保留详情与图文申诉入口，其他功能仍可使用
- 轻量化自适应界面、舒缓动效与深浅色主题；白日蓝色，统一首页数字字体
- 应该能用的在线更新系统(我自己能用😋)

## 项目结构

- `app/`：Kotlin、Jetpack Compose Android 客户端
- `psq-web/`：PSQ 官网、React PWA 与同源 Node 代理，含预编译包构建和 HTTPS 部署说明
- `server/`：Caddy 配置、构建和服务器部署脚本
- `backend-source/`：线上后端对应源码，基于 [Sczr0/Next-Phi-Backend](https://github.com/Sczr0/Next-Phi-Backend) 修改
- `psq-admin-web/`：公告、数据、用户反馈、用户管理、申诉处理与曲目别名管理网页

## 构建客户端

需要 JDK 17 和 Android SDK：

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Release 构建需要自行创建 `keystore.properties` 和签名密钥；这些文件不会提交到仓库。

## 后端对应源码

`backend-source/` 基于上游提交 `3b167614e916b62b7f84606cb044c0590611a9d7`，包含当前部署所需的功能修改、测试、模板和曲库接口。具体修改见 [backend-source/MODIFICATIONS.md](backend-source/MODIFICATIONS.md)。

公网 API 通过响应头和 `/source` 路由向用户提供该目录的固定版本链接。完整 AGPL 源码提供说明见 [SOURCE_OFFER.md](SOURCE_OFFER.md)。

## 许可证

本仓库不是单一许可证项目：

- Android 客户端及未另行声明的项目原创内容：Apache License 2.0
- `server/`：GNU Affero General Public License v3.0
- `backend-source/`：GNU Affero General Public License v3.0，并保留上游许可和版权声明
- 字体、图标、曲绘、头像和游戏资料：不自动纳入上述代码许可证，详见 [ASSETS.md](ASSETS.md)
- 第三方软件声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)

使用或分发前请同时阅读根目录、`server/` 和 `backend-source/` 中的许可证文件。

## 隐私与安全

- [隐私说明](PRIVACY.md)
- [安全策略](SECURITY.md)
- [贡献指南](CONTRIBUTING.md)

发现安全问题时请不要创建公开 Issue，按照安全策略中的方式联系相关维护者。

### 每日签到（Pre-0.9.7.10）

更多页顶部提供每日签到：北京时间每日随机获得 15–50 Coin 和 0–100 幸运值，支持签到卡自动生成、保存与分享、月历、累计天数及前 100 名排行榜。签到账本保存在服务端，重复请求不会重复发币。管理后台展示签到人数、日活签到率（签到用户 ÷ 日活用户）、总签到率（签到用户 ÷ 总用户数）及近 14 日折线图；管理网页需独立发布。

### 资源下载与曲绘加载优化（v92）

谱面支持 2／4 段续传、按资源版本复用完整性校验与解压缓存；曲绘列表和普通预览使用 WebP，查看和保存原图保持原始资源。配套服务器升级与验证步骤见 [资源优化部署说明](server/RESOURCE-PERFORMANCE-v92.md)。
