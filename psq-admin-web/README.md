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

每日签到统计：显示签到人数、签到率和近 14 日签到人数折线图。依赖新版 `/api/v2/admin/dashboard` 的 `checkinTrend` 字段。签到率按北京时间，以当日签到人数除以截至当日排行榜与签到用户去重总数。

## 用户反馈

管理台新增用户反馈列表，支持分页、查看私有附件、修改待处理/处理中/已处理状态和填写管理员回复。已处理必须填写回复；并发更新通过 revision 检查，防止覆盖其他管理员的处理结果。

依赖配套后端接口：
- `GET /api/v2/admin/feedback?offset=0`
- `POST /api/v2/admin/feedback/{id}`，正文包含 `status`、`reply`、`revision`
- `GET /api/v2/admin/feedback/{id}/images/{position}`

上述接口均沿用 `X-Admin-Token`。附件通过鉴权代理读取，不公开静态文件地址。应先升级后端，再发布管理台和客户端。
