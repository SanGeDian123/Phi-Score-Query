# Windows 离线部署

本目录及其部署脚本使用 GNU Affero General Public License v3.0。线上修改版后端的完整对应源码位于：

<https://github.com/SanGeDian123/Phi-Score-Query/tree/pre-0.9.7.1/backend-source>

生产 Caddy 配置会在响应头中提供源码地址，并通过 `/source` 路由跳转至该固定版本。分发服务器二进制时必须同时保留 `LICENSE` 和源码提供说明。

服务器不需要访问 GitHub，也不需要安装 Rust、Git、Node.js 或 Java。构建机完成编译并把后端、曲绘、歌曲资料、Caddy、Microsoft Visual C++ x64 运行库和脚本装进 ZIP；服务器仅负责解压、校验与运行。安装器会在系统缺少 `VCRUNTIME140.dll` 时静默安装随包附带的官方运行库。

## 1. DNS

在 `plc-liangpi-cup.xyz` 的 DNS 控制台新增一条 A 记录：

- 主机记录：`api`
- 记录值：Windows 服务器公网 IPv4
- TTL：600 秒或控制台默认值

不要修改根域名现有的 Vercel 记录。

## 2. 本机构建离线包

准备三项本地文件：Next-Phi-Backend 源码、完整 `phi-plugin-ill` 曲绘仓库、Windows x64 的 `caddy.exe`。然后在项目根目录运行：

```powershell
.\server\scripts\New-OfflinePackage.ps1 `
  -BackendSource 'D:\source\Next-Phi-Backend' `
  -IllustrationSource 'D:\source\phi-plugin-ill' `
  -CaddyExe 'D:\tools\caddy.exe'
```

脚本生成 `server\dist\phigros-score-server-<版本>.zip` 与同名 `.sha256`。通过远程桌面传到服务器，先比对 SHA256，再解压。

构建产物默认放在 `D:\PhigrosScoreBuild`，避免占用 C 盘。若本机 Rust 版本尚未稳定支持 `Duration::from_mins`，脚本会在 D 盘构建副本中把两处一分钟回退值等价改写为 `Duration::from_secs(60)`；不会修改传入的上游源码目录。

## 3. 服务器安装

以管理员身份打开 PowerShell，在解压目录运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Install-Server.ps1
```

脚本会进行必要域名连通性检测，生成仅 SYSTEM/管理员可读的随机密钥，注册两个开机任务，开放 443，并验证本地后端健康状态。Caddy 在 DNS 生效后自动申请和续期证书。

## 4. 验收

```powershell
Invoke-RestMethod 'http://127.0.0.1:3939/health'
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
Get-ScheduledTask 'PhigrosScore-*' | Select-Object TaskName, State
```

服务端不会保存原始 SessionToken。统计数据库仅使用带盐哈希用户标识，热数据保留 30 天；日志不得输出 Authorization、SessionToken 或共享密钥。

## 5. 发布应用更新

将签名后的 Release APK 上传到服务器，然后以管理员身份运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& 'C:\Services\PhigrosScore\current\scripts\Publish-AppUpdate.ps1' `
  -ApkPath 'C:\Users\Administrator\Desktop\Phi-Score-Query-Pre-0.9.6-Fix.apk' `
  -VersionCode 12 `
  -VersionName 'Pre-0.9.6-Fix' `
  -Changelog @( `
    '新增应用内联网更新。', `
    '设置页新增自动检查开关与手动检查更新入口。' `
  )
```

脚本会复制 APK、计算 SHA-256，并最后原子替换 `latest.json`，避免客户端读到未上传完整的版本。发布后验证：

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes
Invoke-WebRequest -Method Head $latest.apkUrl | Select-Object StatusCode, Headers
```
