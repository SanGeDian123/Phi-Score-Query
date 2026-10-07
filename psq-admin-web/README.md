# PSQ Server Console

PSQ 项目组内部使用的服务器数据与 APP 公告管理网页。

## 功能

- 使用现有 `X-Admin-Token` 连接 PSQ 管理接口；令牌仅保存在当前页面内存中。
- 展示排行榜已登记用户总数、今日活跃与请求数、用户活跃率、请求成功率，以及近 14 日 DAU 和总用户数趋势。
- 数据每 5 秒自动刷新，同时保留手动刷新；指标变化时按增减方向切换数字。
- 按发布时间最新优先查看全部公告，并通过页面内二次确认快捷新增、编辑和删除公告。
- 使用 APP 同款 `source_han_sans_saira_hybrid.ttf` 字体和 PSQ 浅色主题。
- 适配电脑、平板与手机，包含完整的加载、错误、空数据和发布结果状态。

## 本地运行

Windows 下直接双击项目根目录的 **`启动APP后台网页.cmd`**。脚本会自动进入网页目录，首次运行时安装缺失的依赖，启动本地服务，并在网页就绪后通过默认浏览器打开 `http://localhost:3000/`。需要安装 Node.js 22.13.0 或更新版本。

运行期间保留命令窗口，按 `Ctrl+C` 停止服务。重复运行时，若 3000 端口已是本管理台，会直接打开现有网页；若被其他程序占用，则提示错误。

也可以在此目录通过 PowerShell 运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-AdminWeb.ps1
```

可用参数：

- `-NoBrowser`：启动后不自动打开浏览器。
- `-LocalOnly`：只监听本机，禁止手机访问（默认监听局域网，供手机使用）。

## 手机访问

后台服务默认监听 `0.0.0.0`，因此手机与电脑连接同一个 Wi-Fi 后即可访问同一份数据：

1. 电脑双击 `启动APP后台网页.cmd`，命令窗口会打印手机可用的地址，例如 `http://192.168.1.8:3000/`。
2. 在手机上打开该地址，或打开电脑上的网页后点击右上角/导航栏的 **「手机访问」**，用手机相机扫描页面上的二维码。
3. 手机上仍需输入一次管理员令牌：令牌不会写入本地存储，也不会随地址传递。

页面显示的地址由构建期注入的本机局域网 IPv4 地址生成，虚拟网卡（Hyper-V / WSL）地址会排在真实网卡之后。手机与电脑不在同一网段时无法访问；若公司或校园网络启用了终端隔离，请改用电脑开热点。

默认通过站点服务端代理连接 `https://api.plc-liangpi-cup.xyz`，浏览器不会直接跨域访问 PSQ。开发环境如需连接测试服务器，可设置服务端环境变量 `PSQ_API_ORIGIN`。

## 构建

```powershell
npm run build
```

构建输出位于 `dist`。管理站点发布前，PSQ 后端需要包含：

- `GET /api/v2/admin/announcements`
- `POST /api/v2/admin/announcements`
- `PUT /api/v2/admin/announcements/{id}`
- `DELETE /api/v2/admin/announcements/{id}`
- `GET /api/v2/admin/dashboard`
- 运行进程环境变量 `APP_ANNOUNCEMENT_DIR`

公告接口沿用 `leaderboard.admin_tokens` 与 `X-Admin-Token`，不会增加另一套管理员凭据。旧版 `GET/POST /api/v2/admin/announcement` 继续由后端保留兼容。

每日签到统计：显示签到人数、日活签到率（签到用户 ÷ 日活用户）、总签到率（签到用户 ÷ 总用户数）和近 14 日签到人数折线图。依赖新版 `/api/v2/admin/dashboard` 的 `checkinTrend`、`dau` 与 `totalUsers` 字段，统计日期按北京时间计算。

## 用户反馈

管理台新增用户反馈列表，支持分页、查看私有附件、修改待处理/处理中/已处理状态和填写管理员回复。已处理必须填写回复；并发更新通过 revision 检查，防止覆盖其他管理员的处理结果。

依赖配套后端接口：
- `GET /api/v2/admin/feedback?offset=0`
- `POST /api/v2/admin/feedback/{id}`，正文包含 `status`、`reply`、`revision`
- `GET /api/v2/admin/feedback/{id}/images/{position}`

上述接口均沿用 `X-Admin-Token`。附件通过鉴权代理读取，不公开静态文件地址。应先升级后端，再发布管理台和客户端。

## 用户管理与申诉

按用户名或昵称搜索玩家，显示头像、RKS、课题模式等级和当前排行榜位次。可分别设置停止公开展示和暂停账户使用，选择预设或自定义分钟数，也可设置长期限制；两项限制都支持单独解除。

新增“已禁止公开展示 / 暂停账号用户列表”，统一查看当前仍有效的限制。支持全部、禁止公开展示、暂停账号三种筛选，显示对应人数，并可按用户名或昵称搜索；每页 20 位，同一用户同时受到两类限制时只显示一条记录。列表显示用户资料、限制原因及 UTC+8 恢复时间，可直接修改限制、解除展示限制或恢复账户。

限制变更与申诉处理后同步刷新列表；页面每分钟自动更新，也可手动刷新。到期或已解除的限制不再列入对应列表，某用户仍有另一项有效限制时会继续显示。

用户搜索与申诉列表的课题等级沿用 APP 的颜色档位和等级组合，如“彩52”“金48”；服务端编码 552 对应彩52、448 对应金48。颜色档位依次为绿、蓝、红、金、彩，未取得有效课题记录时显示“—”。

停止公开展示和暂停账户使用均会从公开排行榜与公开资料中移除该用户，隐藏其已有求建议帖子、评论及图片，并从谱面评级达成率的样本总数、评级人数与百分位计算中排除该用户。停止公开展示期间不能发布求建议或建议评论；暂停账户使用还会由后端拒绝会话签发、续期和已有会话的服务请求，旧版客户端同样受服务端检查约束。原始成绩和帖子保留，到期或手动解除后恢复。

旧客户端通过服务端错误提示收到限制原因和“如需申诉，请将APP更新至最新版本”。仅服务器升级不会替换客户端界面，也不会发布 APP 更新清单；图文申诉入口需安装支持该功能的客户端。

限制状态查询与图文申诉接口通过 SessionToken 识别账号，不要求有效的应用 Access Token，因此账户暂停后仍能调用申诉接口。两项限制的申诉状态分别返回；每项限制只能有一份待处理申诉。管理员通过申诉后自动解除对应限制，更新限制时会关闭旧申诉，防止旧申诉解除新限制。

配套后端新增接口：

- `GET /api/v2/admin/users/management/search?query=用户名`
- `GET /api/v2/admin/users/management/restricted?restrictionType=all&query=用户名&page=1`（`restrictionType` 可为 `all`、`public_hidden` 或 `account_suspended`，`query` 可为空）
- `POST /api/v2/admin/users/management/restriction`
- `GET /api/v2/admin/users/management/appeals?status=pending`
- `POST /api/v2/admin/users/management/appeals/{id}`
- `GET /api/v2/admin/users/management/appeals/{id}/images/{position}`
- `POST /api/v2/users/me/moderation/status`
- `POST /api/v2/users/me/moderation/appeals`

管理接口沿用 `X-Admin-Token`。申诉图片最多 3 张，每张不超过 1 MB，仅支持 JPG、PNG、WebP，经鉴权后才能查看。部署须先更新 Rust 后端，再更新管理台；客户端申诉入口需要支持该功能的版本。
