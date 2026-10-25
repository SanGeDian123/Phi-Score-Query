# 香港服务器部署

PSQ 官网与 PWA 的部署域名为 `phiscorequery.top`，香港服务器公网 IPv4 为 `64.90.3.88`。让现有 Caddy 反向代理到本机 Node 端口；当前 API 和 Android 更新服务继续使用原配置。Pre-0.9.8.0-web.1 的 `/` 在倒计时结束后自动显示完整官网，`/preview/` 保留同一官网，网页版位于 `/app/`；同属一个 Node 服务。资源、API、Manifest 和 Service Worker 仍按域名根路径运行，不支持把整个项目直接挂在 `/psq/` 子路径。

## web.11 自动开放

web.11 已完成本地验证，2026-10-11 公网健康接口也已核对到 web.11；后续浏览器启动修复和服务器流错误修复需另行应用。`access.config.json` 设置 `newWebLogins=true`、`openAfterCountdown=true`，开放时间为 `2026-10-11 12:00:00 UTC+8`。此前首页仅显示原倒计时，无隐藏页面链接；匿名访问 `/preview/` 或 `/app/` 回到 `/`，已有有效会话继续使用和续期，新登录接口返回 `403 WEB_LOGIN_CLOSED`。到点后首页自动展示原 `/preview/` 官网，匿名可打开 `/app/` 登录页，扫码与 SessionToken 登录恢复，`/preview/` 也可公开访问。若部署时已到开放时间，立即启用官网与登录，无需再次重启。Android APP 的原有后端接口保持可用。所有页面和导航路径见 [ROUTES.md](ROUTES.md)。

已有部署可以使用下面的 Windows 升级脚本或手动更新步骤，不需要调整 DNS、Caddy 或增加服务端口。必须整体复制 `dist/`、`server/`、`release.config.json` 与 `access.config.json`，保留原有 `.env`、整个 `.runtime/`、既有 `PSQ_COOKIE_SECRET` 与运行任务；不要只单独覆盖首页 HTML。密钥改变会导致已有用户会话失效。

升级后验证 `/api/health` 的 `webBuild` 为 `Pre-0.9.8.0-web.1+20261025`；`/api/auth/status` 提供服务器时间、开放目标、`websiteOpen` 和 `loginEnabled`。到点前两个开放标志为 false，到点后为 true。分别检查首页、匿名登录页和既有会话。本修订自动激活更新并刷新旧公开首页缓存；旧应用书签或从 `/` 独立启动的 PWA 保持迁移至 `/app/`。服务器时钟应准确，开放判断使用指定 UTC+8 时刻对应的时间戳，不依赖服务器当地时区。

本地构建与测试不代表已经修改公网服务器；iPhone / iPad 的主屏幕启动与更新仍需真机验收。

## 0. 设置域名解析

在域名服务商的 DNS 管理中添加以下记录：

| 主机记录 | 记录类型 | 记录值 | TTL |
|---|---|---|---|
| `@`（或留空，表示根域名） | `A` | `64.90.3.88` | 默认或 600 秒 |

如果服务商提供代理开关，首次部署先使用仅 DNS 解析。当前配置使用根域名，不要求添加 `www` 记录。仅在服务器已配置可用的公网 IPv6 时添加 AAAA 记录。

解析生效后，`phiscorequery.top` 的 A 记录应返回 `64.90.3.88`。Windows 可检查：

```powershell
Resolve-DnsName phiscorequery.top -Type A -Server 1.1.1.1
```

完成解析不等于网页已上线；还需要下面的 Node 服务与 Caddy HTTPS 配置。域名注册审核已通过，按当前香港托管方案通常无需中国大陆 ICP 备案。

## 1. 解压并配置

安装 Node.js 22.13.0+，将部署包解压到独立目录，例如 Linux `/opt/psq-web` 或 Windows `D:\PSQWeb`。复制 `.env.example` 为 `.env`：

```dotenv
PSQ_API_ORIGIN=https://api.plc-liangpi-cup.xyz
PSQ_HOST=127.0.0.1
PSQ_PORT=4173
PSQ_PUBLIC_ORIGIN=https://phiscorequery.top
```

`.env.example` 已填写该正式域名，复制后即可使用；`PSQ_PUBLIC_ORIGIN` 不能包含路径。API 应指向部署了 Pre-0.9.8.0 所需接口（含 RKS 历史、Best N 与时间回溯）的现有 PSQ 后端。前端通过同源 Node 代理访问 API，不需要在浏览器里配置 API 交换密钥或开放跨域权限。不要把 `.env` 和私钥提交到仓库。

首次运行会创建 `.runtime/cookie.key`，用于加密 HttpOnly 会话 Cookie。**升级时保留整个 `.runtime/` 和 `.env`**，否则已有登录会话需要重新登录。也可自行设置至少 32 个字符的持久 `PSQ_COOKIE_SECRET`；应使用足够随机的值，后续保持不变。

## 2. 启动

Linux：

```bash
cd /opt/psq-web
bash Start-PSQWeb.sh
```

Windows：

```powershell
cd D:\PSQWeb
.\Start-PSQWeb.ps1
```

预编译包不需要 `npm install`。正式服务只监听 `127.0.0.1`，外部请求由 HTTPS 反向代理进入。`PSQ_LOCAL_HTTP=1` 仅用于 localhost，正式环境不要启用。

## 3. 配置现有 Caddy

将 `deploy/Caddyfile.example` 中的独立站点块加入你现有配置；不要用示例文件替换整个现有 Caddyfile。下面的配置由 Caddy 自动申请和续期 HTTPS 证书。

```caddyfile
phiscorequery.top {
    encode zstd gzip
    reverse_proxy 127.0.0.1:4173
}
```

开放现有 HTTPS 所需的 80 / 443 端口，确保 DNS 正确，然后按你当前 Caddy 的安装方式验证与重新加载配置。浏览器打开 `https://phiscorequery.top/api/health`，应看到 `ok: true` 和本包 `webBuild`；再按当前是否到开放时间检查 `https://phiscorequery.top/` 与 `/app/`。不要在页面截图或日志中公开 SessionToken。

Windows 服务器如果沿用本项目安装脚本的默认路径 `C:\Services\PhigrosScore` 和计划任务 `PhigrosScore-Caddy`，可以在服务器的管理员 PowerShell 中执行 `deploy/Configure-PSQWeb-Caddy.ps1`。它先验证本机网页服务及候选配置，再备份原 Caddyfile、追加独立站点、开放 Windows 防火墙 TCP 80 / 443，并重启原 Caddy 计划任务；启动失败时尝试恢复原配置。原 API 会在 Caddy 重启时短暂中断。已有其他形式的新域名配置时，脚本会停止，避免重复添加。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\Configure-PSQWeb-Caddy.ps1
```

可加 `-ValidateOnly` 只检查候选配置，不替换原配置或重启任务；安装目录不同时通过 `-InstallRoot` 指定。原 Caddy 使用 `admin off`，不能直接使用默认 `caddy reload`。脚本不会开放云服务商的安全组，若启用了外层防火墙，仍需在那里放行 TCP 80 / 443。

## 4. 常驻运行

### Windows

将 `deploy/Install-PSQWeb-Windows.ps1` 复制到服务器桌面。先在原来运行 Node 的窗口按 `Ctrl+C`，然后在服务器的管理员 PowerShell 执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Desktop\Install-PSQWeb-Windows.ps1"
Get-ScheduledTask -TaskName 'PSQ-Web' | Select-Object TaskName, State
Invoke-RestMethod 'https://phiscorequery.top/api/health'
```

默认部署目录为 `C:\Services\PSQWeb`，可用 `-InstallRoot` 改为实际路径；自动寻找 Node，必要时用 `-NodePath` 指定 `node.exe` 的绝对路径。安装器创建 `PSQ-Web` 计划任务，以 SYSTEM 身份在 Windows 启动时运行，进程退出后每分钟重试，最多 20 次，不设运行时长限制。成功后可以关闭窗口或断开远程桌面，日志保存在部署目录的 `logs/psq-web.log`。它保留 `.env` 与 `.runtime`，不会替换已有同名计划任务，也不会终止占用端口的未知进程。

后续升级使用第 5 节的升级脚本，由它停止并重启匹配的计划任务，同时保留配置及密钥。

### Linux

`deploy/psq-web.service.example` 提供 systemd 模板。创建专用的 `psq` 用户，让它可以读取部署目录及 `.env`，并写入 `/opt/psq-web/.runtime`。确认模板中的路径、用户和 Node 路径适合你的系统，再安装为 `/etc/systemd/system/psq-web.service`：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now psq-web
sudo systemctl status psq-web
```

双击 CMD 是本地预览入口；Windows 正式部署使用上面的计划任务安装器。

## 5. 后续升级

### Windows 已有安装

将新包解压到独立目录，例如 `C:\PSQWebUpgrade`，该目录里面应直接包含 `dist/`、`server/` 和 `deploy/`，不能放在正在运行的安装目录里面。在服务器管理员 PowerShell 执行：

```powershell
$psqPackage = 'C:\PSQWebUpgrade'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$psqPackage\deploy\Upgrade-PSQWeb-Windows.ps1" -PackageRoot $psqPackage -InstallRoot 'C:\Services\PSQWeb' -ValidateOnly
if ($LASTEXITCODE -ne 0) { throw '升级包验证失败，停止升级。' }
```

看到 `Validation passed` 后，运行正式升级并核对公网版本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$psqPackage\deploy\Upgrade-PSQWeb-Windows.ps1" -PackageRoot $psqPackage -InstallRoot 'C:\Services\PSQWeb'
if ($LASTEXITCODE -ne 0) { throw '升级失败，请查看脚本输出。' }
Invoke-RestMethod 'https://phiscorequery.top/api/health'
Invoke-RestMethod 'https://phiscorequery.top/api/auth/status'
```

安装目录不是 `C:\Services\PSQWeb` 时修改 `-InstallRoot`；任务名称不是 `PSQ-Web` 时加 `-TaskName`。Node 不在默认位置时加 `-NodePath '实际的 node.exe 路径'`。脚本先确认任务动作属于该安装，再停止服务；原发行文件保存在安装目录的 `.backups/`。它保留 `.env`、整个 `.runtime/`、`logs/` 和 `deploy/Run-PSQWeb.ps1`，失败时尝试恢复旧发行文件。

尚未安装计划任务、仍在 PowerShell 前台运行 Node 时，先在原窗口按 `Ctrl+C`，再运行升级脚本；文件替换完成后，按脚本输出重启原服务或安装计划任务。脚本不会终止未知进程，也不会更改 Caddy 配置。

头像代理与到点自动开放均依赖 Node 服务及配置，**不能只复制前端 `dist/`**。公网 `/api/health` 的 `webBuild` 应与新包 `RELEASE.json` 一致。web.11 会自动更新旧公开首页缓存；已有用户仍可在应用设置里检查版本。

### Linux 或手动更新

1. 先准备新部署目录，保留旧部署目录供回退；停止该 PWA 的 Node 服务。
2. 将新包的 `dist/`、`server/`、`release.config.json`、`access.config.json` 和启动文件整体更新。保留旧的 `.env` 和 `.runtime/`，或在新目录中安全迁移它们。
3. 启动服务，核对 `/api/health` 的版本及 `/api/auth/status` 的开放状态。web.11 自动激活新版 Service Worker 并刷新旧首页；账户、设置和草稿继续保留。
4. 有问题时停止服务、恢复旧部署目录与原配置，再启动。已经激活新版的浏览器需检查更新并重新载入回退版本。

保持旧进程仍使用的目录完整，避免它在新文件复制一半时读到混合版本。不需要修改原 Next-Phi-Backend 数据目录、Android APK 或已有发布脚本。

## 网页返回 502 时

若首页、`/app/` 和 `/api/health` 均返回无内容的 Caddy 502，先检查 Node 网页服务及 Caddy 的上游连接。`/api/health` 不依赖原 APP 后端；原后端正常并不代表网页服务已运行。

`deploy/Recover-PSQWeb-Windows.ps1` 是独立的服务器诊断与恢复脚本，不需要重建或上传网页部署包。复制到香港服务器后，在管理员 PowerShell 中执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Desktop\Recover-PSQWeb-Windows.ps1" -Recover
```

不加 `-Recover` 时只检查。脚本优先从 `PSQ-Web` 任务的工作目录识别安装路径，也可指定 `-InstallRoot` 和 `-TaskName`。它显示任务状态、运行时长限制和最近日志；恢复时只启动或重启动作匹配且健康检查失败的网页任务，不替换部署文件或修改 Caddy。已经健康的服务保持运行。任务缺失、禁用、路径不匹配或端口仍被占用时会停止并报告原因；缺失任务可使用第 4 节的安装器建立常驻服务。

本机健康检查通过后，脚本分别检查公网健康接口、首页与 `/app/`，并核对公网版本。公网仍返回 502 时需要进一步检查 Caddy 上游地址及连接日志；本机成功不等于公网访问已经恢复。若任务显示非零运行时长限制，需结合停止时间和任务历史确认是否因此被终止，再修正常驻设置。

### 资源超时导致 Node 退出

若日志出现 `DOMException [TimeoutError]`、`Emitted 'error' event on Readable instance` 且网页任务退出，可单独应用 `deploy/Repair-PSQWeb-StreamTimeout.ps1`。复制该文件到香港服务器桌面，在管理员 PowerShell 执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Desktop\Repair-PSQWeb-StreamTimeout.ps1"
```

脚本只替换已确认格式的转发函数与部分响应错误处理，不需要上传其他文件或新的前端包。它保留 web.11、`.env`、整个 `.runtime/`、日志和任务设置，先备份原 `server/index.mjs` 至 `.backups/stream-timeout-*/index.mjs`，再短暂重启匹配的 `PSQ-Web` 任务并核对本机健康。启动失败时恢复原文件；端口未释放时停止，避免终止未知进程。原 APP API 和 Caddy 无需重启。

可加 `-ValidateOnly` 先检查补丁，不修改已安装文件或任务；路径、任务名称或 Node 路径不同时分别使用 `-InstallRoot`、`-TaskName`、`-NodePath`。已经安装同一补丁时不会重复重启。该修复不改变前端版本号，核对健康接口后还应实际打开网页版并检查资源加载；保留脚本输出及最近日志，供继续核实线上稳定性。

## iPhone / iPad 使用

开放时间之前仅已有网页登录会话可使用 `/app/`。到点后可在 Safari 访问 `/app/`，通过扫码或 SessionToken 登录，再通过分享菜单添加到主屏幕；登录页也提供手动安装说明。原 Safari / 主屏幕 PWA 的站点数据与登录状态继续保留，Safari 标签页与独立窗口的登录状态可能分开。图片优先通过页面“分享”保存；不支持文件分享的浏览器会退回下载。

本地验证使用独立模拟后端；iPhone / iPad 真机及 Safari 仍需实际验收。公网已安装 PWA 的访问能力取决于 HTTPS、原 API 可用性及设备存储策略。

## Android、后端与官网同步发布

使用整体升级包的 `Deploy-Release-Pre-0.9.8.0-v112.ps1`，先验证再执行。它先升级后端和官网/PWA，确认本机及公网网页版本，再最后发布 Android `latest.json`；失败时恢复修改过的文件。参见 [`../server/RELEASE-Pre-0.9.8.0-v112.md`](../server/RELEASE-Pre-0.9.8.0-v112.md)。默认仍使用现有 Caddy 域名配置和 `PSQ-Web` 计划任务。
