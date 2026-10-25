# PSQ 官网与 PWA · Pre-0.9.8.0

PSQ 的自托管网页版，可在 iPhone / iPad 的 Safari 中添加到主屏幕，也支持电脑、Android 和平板浏览器。产品版本与 Android `Pre-0.9.8.0` 对齐，当前网页构建为 `Pre-0.9.8.0-web.1+20261025`，共用原有 PSQ 后端。

## 本次更新

- 概览页点击大 RKS 数字进入趋势图，首次显示发光边框及“点击查看RKS趋势图”，进入后不再提示。近 7 / 30 / 90 天和全部记录自动分页加载完整，显示四位小数 RKS、变化量与记录时间；顶部返回键在滚动时保持可用。
- 成绩图片支持 Best N（1–200），沿用经典、简约、Phi-Plugin 样式及预览、保存、分享。
- 设置中进入时间回溯，按冻结的 2025-02-14 定数和 P1＋B19 重新计算；仅显示概览、B19、图片、定数表、设置，Phi-Plugin 生图使用旧版 B19 模板。普通成绩与缓存保持原始数据，刷新页面退出回溯。
- 官网展示最新功能及网页版本；Android 版本、下载地址和更新内容以服务器正式发布的 `app-update/latest.json` 为准。
- 保留 Phi-Plugin B30 等效 RKS 直方图、流式代理故障隔离、自动开放登录、旧书签迁移及原有功能。谱面练习、播放与资源管理仍为 Android 专属功能。

## 构建和部署

在此目录运行（Node.js 22.13.0 及以上）：

```powershell
npm ci
$env:PSQ_BUILD_DIR = 'D:\PhigrosAppBuild\Pre-0.9.8.0-web.1\dist'
$env:PSQ_WEB_BUILD = 'Pre-0.9.8.0-web.1+20261025'
npm run build
npm run verify
$env:PSQ_PACKAGE_DIR = 'D:\PhigrosAppBuild'
npm run package
```

整体发布使用 [`../server/RELEASE-Pre-0.9.8.0-v112.md`](../server/RELEASE-Pre-0.9.8.0-v112.md)，含 Android 更新、Rust 后端和官网/PWA。独立网页包仅含 `dist/`、Node 服务及部署脚本，不含 APK 或 Rust 后端。部署须保留 `.env`、整个 `.runtime/`、会话密钥与日志；现有计划任务默认为 `PSQ-Web`，安装目录为 `C:\Services\PSQWeb`。步骤见 [DEPLOY.md](DEPLOY.md)，页面清单见 [ROUTES.md](ROUTES.md)。

域名沿用 `phiscorequery.top`：`/` 为官网，`/app/` 为网页版，`/preview/` 为官网预览。当前开放时间已过，匿名用户可直接登录；原会话继续有效。不要重新关闭 access.config.json 的登录开关。

本地构建、模拟后端验证与 GitHub 发布不代表生产服务器已升级；iPhone / iPad 的 Safari 和主屏幕行为仍需真机验收。

## 功能

- 大陆版 TapTap 扫码、SessionToken 登录、会话自动续期。
- 概览、B30 / P30 / Best N、单曲搜索及详情、1–18 级定数表、前 1,500 名排行榜。
- 经典 / 简约 / Phi-Plugin BP30 图片、默认 / 旧版单曲成绩图、保存、分享和缩放查看。
- RKS 与分数 / ACC 计算、自定义 BP30 / P30 的 36 张谱面编辑及现有成绩导入、谱面评级达成率。
- 每日签到、Coin、日历、签到卡和累计签到排行。
- RKS 猜猜乐的单人及公开匹配、求建议 / 给建议、图文反馈、公告历史、限制状态与图文申诉。
- 深浅色主题、自适应导航、账户隔离缓存、离线查看已缓存成绩、页面内版本更新。

**不包含谱面练习、播放、资源管理及相关设置。** 猜曲绘、开字母沿用 Android 中尚未开放的状态。

## web.3 字体修正

沿用 Android APP 的思源黑体 / Saira 混合字体字形，转换为 WOFF2，统一网页字体内部名称，并将固定 Regular 字体声明为 400 字重，允许浏览器按界面字重加粗。数字和英文使用约 28 KB 的独立字集，完整字库约 3.9 MB；两者预加载并随 Service Worker 离线缓存，减少首次打开时显示系统替代字体的时间。Android 原始 TTF 不变。

生成字体的脚本为 `scripts/prepare-web-fonts.py`，需要 Python、FontTools 和 Brotli；仅在 APP 原字体变化时运行。常规网页构建和服务器运行直接使用已有 WOFF2，不需要 Python。字体许可继续随 `public/licenses.txt` 提供。

该修正基于已部署的 `Pre-0.9.7.12-web.2`。本地 Android 工作区已独立进入 `Pre-0.9.7.12-Fix`，web.3 不包含该 Android 修订的其他改动；本次保留网页产品版本，分别执行许可生成、TypeScript 编译及 Vite 生产构建。常规 `npm run build` 的版本一致性检查仍保留，重建旧产品修订应使用对应版本的 Android 源码。

## web.2 界面修正

按 Android Pre-0.9.7.12 的实际界面调整概览、B30 / P30、谱面评级达成率、RKS 猜猜乐、排行榜、自定义谱面选择及求建议 / 给建议，保留电脑、平板和手机适配。EZ / HD / IN / AT 使用 APP 的绿、蓝、红、灰四种主题色。

头像通过 Node 服务将标识转换成后端要求的 SHA-256 文件名；升级必须同时更新 `dist/` 和 `server/`。定数表默认显示全部，等级依次为全部、18、17……1，等级条支持触摸滑动和鼠标拖动。页面风格可在设置中独立选择白日 / 黑夜并保存。

Windows 已有安装可使用 `deploy/Upgrade-PSQWeb-Windows.ps1`，先验证新包，再备份并替换应用文件，保留 `.env`、整个 `.runtime/`、日志和已安装的任务启动脚本；升级步骤见 [DEPLOY.md](DEPLOY.md)。

## 运行预编译部署包

需要 Node.js **22.13.0 或更新版本**。部署包自带 `dist/`，运行时不需要安装 npm 依赖。

本地体验：Windows 双击 `Start-PSQWeb.cmd`；Linux 执行 `bash Start-PSQWeb.sh --local`，然后打开 <http://127.0.0.1:4173/>。本地模式仅供本机体验，不用于公网。

正式部署：复制 `.env.example` 为 `.env`，确认 `PSQ_PUBLIC_ORIGIN=https://phiscorequery.top`，通过 HTTPS 反向代理连接 Node 服务。步骤见 [DEPLOY.md](DEPLOY.md)。服务器部署在香港时，通常不要求中国大陆网站 ICP 备案；若引入大陆服务器或大陆 CDN，应按对应服务商的要求办理。

Windows 常驻部署使用 `deploy/Install-PSQWeb-Windows.ps1` 安装 `PSQ-Web` 开机计划任务；Linux 使用 systemd 模板。配置和 `.runtime` 登录密钥在切换与升级时保留。

Windows 网页出现 Caddy 502 时，可使用独立的 `deploy/Recover-PSQWeb-Windows.ps1` 检查任务、日志和本机 / 公网健康状态；加 `-Recover` 启动或重启匹配且不健康的网页任务。诊断与恢复步骤见 [DEPLOY.md](DEPLOY.md)。

## 开发与构建

在本目录执行（PowerShell）：

```powershell
npm ci
npm run dev
```

构建、验证、打包：

```powershell
$env:PSQ_BUILD_DIR = 'D:\PSQWebBuild\dist'
$env:PSQ_TEST_DIR = 'D:\PSQWebBuild'
$env:PSQ_PACKAGE_DIR = 'D:\PSQWebBuild\packages'
npm run build
npm run verify
npm run package
```

`package` 将已编译文件、Node 服务、启动脚本和部署文档组装为可部署目录；随后可压缩该目录。它不会包含 `.env`、`.runtime`、`node_modules`、测试数据和用户缓存。

## 网页与 APP 同步

两端共用原后端的存档、曲库、定数、排行榜、签到、小游戏、公告、求助、反馈和申诉接口。网页曲库使用 ETag / 304 校验，每 15 秒联网检查一次，也在回到前台时检查；成绩按用户刷新或启动自动刷新更新。后端图片模板同时服务两端。

`release.config.json` 是网页的产品版本、网页修订号、API 版本入口。构建前检查 Android `app/build.gradle.kts` 的 `versionName`；不一致会中止构建。每次构建另有独立 `webBuild`，用于可靠识别 Service Worker 更新。

这个检查保证版本标识一致，**不会自动把 Kotlin 界面或业务改动转成网页代码**。正式同步发布应先完成两端功能和接口适配，再同时更新产品版本、构建与验证，最后发布 Android 与 PWA。仅修复网页时保留产品版本，增加 `webRevision`；每次构建仍会生成新构建标识。

网页自动检查更新并下载完整新外壳；通常由用户确认后刷新，web.10 内测关闭与 web.11 自动开放修订会自动替换旧公开首页缓存。会话和编辑草稿保留。登录接口、存档和私有媒体不会进入 Service Worker HTTP 缓存；已获取的成绩、图片和草稿按账户保存在当前浏览器中。退出登录时清理该账户的本地缓存。浏览器清理站点数据或存储空间不足时，离线数据可能被移除。

离线支持已缓存页面和成绩浏览。扫码、最新存档、签到、排行榜、联网图片生成、互动及申诉需要网络。首次完整缓存包含 WOFF2 字体和少量章节图。正式安装及离线功能需要 HTTPS；localhost 是浏览器允许的例外。

## 验证边界

web.11 已通过 TypeScript、生产构建和 17 项自动测试，包含 UTC+8 开放边界、同一运行中服务从关闭到开放、匿名页面访问、SessionToken 登录、二维码生成与确认、原有会话及续期，以及匿名业务接口和私有媒体限制。本地浏览器确认倒计时到点后无手动刷新自动切换官网，且官网入口可打开含两种登录按钮的 `/app/` 登录页，无运行错误。测试使用隔离的本地模拟后端和配置，不使用真实账户，不写生产数据。此前 web.10 的电脑、平板和手机布局验证记录仍可参考；本次保持原有视觉样式。这些本地结果不代表公网升级或 iPhone 真机已经验收。升级包不含 Android APK 或后端升级文件。

已完成 TypeScript / 生产构建、14 项入口兼容、下载元数据、算法及代理集成测试（含头像映射和图片请求断连后服务存活），以及电脑 / 平板 / 手机尺寸的 Chromium 浏览器交互检查。完整写入流程使用独立本地测试后端，真实 API 预览由用户自行登录；本次未向真实服务器提交签到、反馈或社区内容。Windows 升级脚本在独立本地目录验证文件备份、配置保留及失败回退；这不等于真实服务器计划任务已验证。

web.7 已通过 TypeScript、生产构建和 14 项自动测试。独立本地测试后端验证了 393 px 手机、820 px 平板、1440 px 电脑下的标签切换、成绩统计展开收起、弹窗关闭及焦点恢复、主题切换、定数筛选、排行榜定位、图片双击缩放还原和小游戏回合切换。图片慢加载及重试使用独立测试图片；这些结果不代表 iPhone 真机帧率或触摸手势已经验收。

web.9 已通过 TypeScript、生产构建和现有 14 项自动测试。Chromium 的 390 px 手机和 768 px 平板尺寸下，官网循环动画数量为 0，首屏、导航和返回顶部的背景模糊均关闭，滚动显现与弹窗动画正常；1440 px 电脑保留完整动效。768 px 和 1440 px 横排下载按钮的顶部坐标一致，高度均为 52 px；三个尺寸均无横向溢出。这是本地浏览器验证，未测量 iPhone 真机帧率，也未更新公网服务。

web.3 已核对全部 30,888 个字形轮廓和水平度量与 APP 原字体一致；Chromium 实际加载两个 WOFF2 字体，400 / 500 / 700 字重请求成功，数字和中文的字形度量与系统字体区分。独立预览已验证 web.2 到 web.3 的页面内更新及登录保留；新字体包含在离线预缓存中。

2026-10-07 此前香港服务器的公网健康检查记录为 web.3，产品版本为 `Pre-0.9.7.12`，完整构建标识为 `Pre-0.9.7.12-web.3+20261007T052740273Z`。当前 Pre-0.9.8.0 已完成本地构建与模拟接口验证，生产部署仍须执行升级包。升级后须核对新的完整 `webBuild`，并验收 iPhone 上的主屏幕启动与更新。

**未进行 iPhone / iPad 真机或 Safari 验证。** 在正式开放前应借用 iPhone 验证扫码、添加到主屏幕、更新、图片保存和触摸缩放。

## 许可与素材

网页版原创代码采用 Apache-2.0，见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。React、Lucide、QR 库、混合字体及倒计时 Manrope 回退字体的声明随网页提供，见 `public/licenses.txt`。原有后端独立采用 AGPL-3.0，其源码见仓库 `backend-source/`；本部署包只代理现有服务，不包含后端实现。

Phigros、TapTap、曲绘、曲目信息及游戏素材仍归其各自权利人所有，详见项目根目录 `ASSETS.md`。本项目为非官方玩家项目，与南京鸽游网络有限公司及《Phigros》官方不存在授权、合作或运营关系。

官网 Android 下载按钮直接使用正式更新清单中的 APK 文件地址；更新信息获取失败时在页面内重试，不跳转 GitHub 发布页。
