# PSQ 官网与网页版页面路径

以下是 Pre-0.9.8.0-web.1 的页面路径。生产域名为 `https://phiscorequery.top`，本地升级包尚未部署。开放时刻为 `2026-10-11 12:00:00 UTC+8`；到点自动切换，不需要服务重启。

## 首页与开放阶段

| 路径 | 开放前 | 开放后 |
| --- | --- | --- |
| `/` | 唯一公开界面：原倒计时，仅数字及 DAYS / HOURS / MINS / SECS，无按钮与隐藏页面链接，每秒刷新 | 原 `/preview/` 的完整官网直接显示在首页 |
| `/preview`、`/preview/` | 已有有效网页登录会话可访问；匿名跳转到 `/` | 完整官网的兼容入口，匿名也可访问 |
| `/app`、`/app/` | 已有有效网页登录会话可使用；匿名跳转到 `/` | 未登录时显示扫码与 SessionToken 登录页，登录后进入网页版 |

## 完整官网导航

开放后首页具备下列锚点；开放前已有会话可以在 `/preview/` 使用对应锚点。`/preview/#...` 开放后继续有效。

| 路径 | 内容 |
| --- | --- |
| `/`、`/preview/` | 原官网完整内容 |
| `/#download` | 下载与网页版入口 |
| `/#features` | 功能介绍 |
| `/#news` | 公告列表 |
| `/#faq` | 常见问题 |

公告详情、全部公告、Android 更新内容、安装说明和隐私说明仍为页面内弹窗，没有单独网址。官网品牌入口回到 `/`；网页版入口指向 `/app/`，反馈入口指向 `/app/#feedback`。GitHub 与 Android APK 链接在完整官网中提供。

## 网页版导航

下列功能页面要求登录。开放前仅原有有效会话可使用；开放后新用户可在 `/app/` 登录，二维码生成、确认与 SessionToken 输入均恢复。匿名访问带锚点的应用入口会显示登录页，登录后继续进入对应功能。原有会话自动续期、用户数据和离线缓存保持原机制。

| 路径 | 页面 |
| --- | --- |
| `/app/`、`/app/#home` | 成绩概览 |
| `/app/#scores` | B30 / P30 / Best N 成绩列表 |
| `/app/#song` | 单曲搜索 |
| `/app/#song/<songId>` | 单曲详情，songId 使用 URL 编码 |
| `/app/#constants` | 定数表 |
| `/app/#leaderboard` | 排行榜 |
| `/app/#image` | 成绩图生成与管理 |
| `/app/#more` | 更多功能 |
| `/app/#rks-trend` | RKS 趋势图，概览页点击 RKS 数字进入 |
| `/app/#calculator` | RKS / 成绩计算器 |
| `/app/#custom` | 自定义 BP30 |
| `/app/#achievement` | 推分目标 |
| `/app/#checkin` | 每日签到 |
| `/app/#guess` | RKS 猜猜乐 |
| `/app/#suggestions` | 求建议 / 给建议 |
| `/app/#suggestions/<postId>` | 指定建议帖子 |
| `/app/#settings` | 设置 |
| `/app/#feedback` | 我的反馈 |
| `/app/#feedback/new` | 提交反馈 |
| `/app/#moderation` | 限制状态与申诉 |

主导航指向概览、成绩、单曲、定数表、排行榜、成绩图、更多与设置；工具与互动页从“更多”进入，反馈与限制状态从“设置”进入。图片查看、公告、更新和安装指引为页面内弹窗。

已登录用户的旧 `/#scores`、`/#song/...` 等应用书签，以及旧 PWA 从 `/` 的独立启动，会迁移到对应 `/app/#...`。开放后匿名的旧应用入口也迁移至 `/app/` 登录页。网络不可用且尚未开放时，仅存在原账户与成绩缓存的旧安装迁移到离线应用。未知的无扩展名路径在开放前显示倒计时、开放后显示官网；不存在的静态文件仍返回 404。

## 支持文件与接口（不属于页面导航）

- `/api/auth/status`：查询会话与服务器开放状态，包含 `serverTime`、`countdownTarget`、`websiteOpen`、`loginEnabled`；到点前两个开放标志为 false，到点后为 true。
- `/api/auth/login`、`/api/auth/qr`、`/api/auth/qr/<id>`：开放前返回 `403 WEB_LOGIN_CLOSED`，不请求上游登录服务；到点后启用原登录流程。
- `/api/health`、`/api/version`：网页服务版本与健康状态。
- `/manifest.webmanifest`、`/sw.js`：PWA 清单与缓存更新，新安装入口为 `/app/`。web.11 自动激活并刷新旧首页；开放前倒计时不展示安装入口。
- `/robots.txt`、`/sitemap.xml`：仅首页可收录，排除 `/app`、`/preview` 和 `/api`。
- `/licenses.txt` 与字体许可文件：随网页分发的许可说明，开放后的官网提供许可链接。

仅修改网页 Node 服务与网页界面；Android APP 使用原有后端，不受网页开放规则影响。升级须保留 `.env`、整个 `.runtime/` 与既有 `PSQ_COOKIE_SECRET`，否则原会话会失效。网页版业务接口、存档与私有媒体在开放后仍要求登录，不会因公开官网而开放账户数据。

时间回溯通过 `/app/#settings` 的入口开启，关闭页面或刷新后退出，仅保留概览、B19、图片、定数表和设置。趋势查询使用认证的 `POST /api/backend/rks/history`；Best N 图片使用 `POST /api/backend/image/bestn`；B19 图片沿用 `image/bn?time_rewind=true`。
