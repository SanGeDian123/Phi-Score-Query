[CmdletBinding()]
param(
    [string] $OutputRoot = 'D:\PhigrosAppBuild',
    [string] $CargoTargetDir = 'D:\PhiAdminWebCargoTarget',
    [switch] $SkipBuild,
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-only-upgrade-UserManagement-20261006-Fix3'
$stage = Join-Path $OutputRoot ($name + '-stage')
$zip = Join-Path $OutputRoot ($name + '.zip')
$desktopZip = Join-Path ([Environment]::GetFolderPath('Desktop')) ($name + '.zip')
foreach ($path in @($stage, $zip, $desktopZip)) {
    if (Test-Path -LiteralPath $path) { throw "Delivery already exists: $path" }
}
if (-not $SkipBuild) {
    $env:CARGO_TARGET_DIR = $CargoTargetDir
    $arguments = @('build', '--release', '--locked')
    if ($Offline) { $arguments += '--offline' }
    Push-Location (Join-Path $repo 'backend-source')
    try {
        & cargo @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Backend release build failed.' }
    } finally { Pop-Location }
}
$binary = Join-Path $CargoTargetDir 'release\phi-backend.exe'
if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw 'Missing built backend.' }
foreach ($directory in @('backend', 'caddy', 'scripts', 'source', 'admin-console\psq-admin-web')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $directory) -Force | Out-Null
}
$utf8 = [Text.UTF8Encoding]::new($false)
$bom = [Text.UTF8Encoding]::new($true)
Copy-Item -LiteralPath $binary -Destination (Join-Path $stage 'backend\phi-backend.exe')
Copy-Item -LiteralPath (Join-Path $repo 'server\caddy\UserModeration.caddy') -Destination (Join-Path $stage 'caddy')
foreach ($script in @('Deploy-UserManagement.ps1', 'UserModeration-Caddy.ps1')) {
    [IO.File]::WriteAllText((Join-Path $stage ('scripts\' + $script)), [IO.File]::ReadAllText((Join-Path $PSScriptRoot $script)), $bom)
}
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\LICENSE') -Destination (Join-Path $stage 'LICENSE')
Copy-Item -LiteralPath (Join-Path $repo 'THIRD_PARTY_NOTICES.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $repo 'SOURCE_OFFER.md') -Destination $stage

function Selected-Files([string] $Base, [string[]] $Trees, [string[]] $Files) {
    $selected = @()
    foreach ($tree in $Trees) {
        $selected += @(Get-ChildItem -LiteralPath (Join-Path $Base $tree) -Recurse -File -Force | Where-Object {
            $_.FullName -notmatch '[\\/](node_modules|target|\.git|\.wrangler|__pycache__)[\\/]' -and
            $_.Name -notmatch '^\.env|^config\.toml$|^latest\.json$|\.apk$|\.db($|-)|\.sqlite($|-)|\.log$|^secrets\.'
        })
    }
    foreach ($file in $Files) { $selected += Get-Item -LiteralPath (Join-Path $Base $file) }
    return @($selected | Sort-Object FullName -Unique)
}
function Relative-Name([string] $Base, [string] $Path) {
    return $Path.Substring($Base.TrimEnd([char[]]'\/').Length + 1).Replace([IO.Path]::DirectorySeparatorChar, [char]'/')
}
$backendBase = Join-Path $repo 'backend-source'
$backendFiles = Selected-Files $backendBase @('src', 'crates', 'info', 'resources\fonts', 'resources\templates', 'tests') `
    @('Cargo.toml', 'Cargo.lock', 'config.example.toml', 'LICENSE', 'MODIFICATIONS.md')
$sourceZip = [IO.Compression.ZipFile]::Open((Join-Path $stage 'source\backend-source-Pre-0.9.7.9.zip'), [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $backendFiles) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($sourceZip, $file.FullName,
            (Relative-Name $backendBase $file.FullName), [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
    foreach ($relative in @('server\scripts\Build-UserManagementPackage.ps1', 'server\scripts\Deploy-UserManagement.ps1',
        'server\scripts\UserModeration-Caddy.ps1', 'server\caddy\UserModeration.caddy', 'server\caddy\Caddyfile')) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($sourceZip, (Join-Path $repo $relative),
            $relative.Replace([char]'\', [char]'/'), [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $sourceZip.Dispose() }
$adminBase = Join-Path $repo 'psq-admin-web'
$adminFiles = Selected-Files $adminBase @('app', 'components', 'hooks', 'lib', 'public') `
    @('package.json', 'package-lock.json', 'tsconfig.json', 'vite.config.ts', 'next.config.ts', 'components.json',
        'README.md', 'Start-AdminWeb.ps1', 'next-env.d.ts', '.openai\hosting.json')
foreach ($file in $adminFiles) {
    $target = Join-Path (Join-Path $stage 'admin-console\psq-admin-web') (Relative-Name $adminBase $file.FullName)
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    if ($file.Name -eq 'Start-AdminWeb.ps1') {
        [IO.File]::WriteAllText($target, [IO.File]::ReadAllText($file.FullName), $bom)
    } else { Copy-Item -LiteralPath $file.FullName -Destination $target }
}
Copy-Item -LiteralPath (Join-Path $repo '启动APP后台网页.cmd') -Destination (Join-Path $stage 'admin-console')
$manifest = [ordered]@{
    package = $name; kind = 'server-only'; createdAt = (Get-Date -Format 'yyyy-MM-ddTHH:mm:sszzz')
    includesApk = $false; publishesAppUpdate = $false; modifiesAppUpdate = $false
    appealUpgradeHint = '如需申诉，请将APP更新至最新版本'
    legacySuspension = 'Home refresh closes the functional shell; session refresh clears legacy logins. Offline built-in data and local caches cannot be disabled by a server-only package.'
    publicHiddenLoginNotice = 'Legacy APP uses its bottom snackbar on the first score refresh; the next refresh succeeds. Login and other private features remain available.'
    replaces = @('current/backend/phi-backend.exe', 'source/installed-backend-source.zip')
    patches = @('current/caddy/Caddyfile: user management and appeal handlers; suggestion images use no-store')
    adminConsole = 'Updated source and local launcher included; update the independent management website separately.'
    database = 'Existing stats database is retained. New restriction and appeal tables are created on backend startup.'
    source = 'Built from current backend-source; corresponding source is included.'
}
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_ONLY_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
$readme = @'
# 用户管理服务器升级包（不发布 APP 更新）

本包包含：新后端 Release 程序、用户管理与图文申诉接口的 Caddy 增补规则、对应后端源码、更新后的管理台网页源码及本地启动入口。

## 本次更新

- 管理后台按用户名或昵称搜索，显示头像、RKS、课题模式等级、排行榜位次；可停止公开展示、限时或长期暂停账户，以及解除限制。
- 支持私有图文申诉、后台审核与通过后解除限制；每项限制只能有一份待处理申诉，防止旧申诉解除新限制。
- 登录签发、续期、已有会话及旧式凭据接口均由服务端检查账户暂停；旧客户端同样受到限制。
- 暂停或封禁的旧客户端在主页联网刷新收到限制提示后关闭功能主界面；会话续期返回 401，触发旧客户端清除登录会话。支持限制专页的新客户端仍保留 403 和申诉凭据。
- 过期但签名有效的 Bearer 同样检查暂停，JSON 旧式凭据在统一入口检查，存档与成绩图的服务端缓存不能绕过暂停。
- 被禁止公开展示的旧版 APP 在登录后第一次成绩刷新收到一次底部提示，包含限制原因、UTC+8 恢复时间和升级申诉说明。此次刷新先显示提醒，下一次刷新正常读取成绩；不会退出登录，其他私人功能继续可用。
- 底部提醒按会话与限制更新时间去重，限制修改或会话续期后会再次提醒；缓存有容量上限，服务器重启或缓存淘汰后也可能再次提醒。旧式 JSON 凭据按账号与限制更新时间去重。
- 两类限制均隐藏 RKS、签到及猜猜乐排行榜记录与公开资料；隐藏历史求建议帖子、评论和图片，并禁止新增求建议与建议评论。
- 谱面评级达成率的样本总数、各评级人数及个人百分位均排除受限用户；解除限制或到期后恢复，原始成绩与帖子保留。
- 建议图片的原图、预览和条件请求均检查展示权限，直接使用旧图片地址也会被服务端拒绝，响应不再设置长期缓存。
- 封禁、暂停及公开展示限制的提示追加：“如需申诉，请将APP更新至最新版本”。
- 封停提示的恢复时间转换为 UTC+8 并标明时区，旧版 APP 也能收到转换后的提示；实际到期时刻、数据库与状态接口时间戳保持不变。
- 管理台完整显示课题等级，如彩52、金48，包含用户搜索及申诉列表。
- 管理台包含同一框内的日活签到率与总签到率，已去掉下方详细备注。

本包不包含 APK、APP 更新清单或发布脚本，不修改服务器 app-update 目录，不发布更新通知。旧客户端会按既有错误处理关闭主界面或返回登录页；新版限制专页与图文申诉入口需要支持该功能的客户端。

仅升级服务器不能强制把已安装旧版的主页变成空白，也不能删除其内置定数表、已缓存 B30/P30 或手机本地生成的单曲成绩图。关闭自动刷新、尚未联网收到限制或完全离线的旧客户端可能继续查看本地内容。要完全阻止这些离线功能，需要安装支持账户限制的客户端；本包不宣称已经实现旧版离线禁用。

## 部署后端

默认已有服务目录：C:\Services\PhigrosScore；计划任务：PhigrosScore-Backend、PhigrosScore-Caddy。
将 ZIP 上传服务器桌面，以管理员身份打开 PowerShell，整段执行。部署脚本内置校验，校验失败即停止，成功后自动备份和部署：

```powershell
& {
    $ErrorActionPreference = 'Stop'
    Set-ExecutionPolicy -Scope Process Bypass -Force
    $deployDesktop = [Environment]::GetFolderPath('Desktop')
    $deployZip = Join-Path $deployDesktop 'server-only-upgrade-UserManagement-20261006-Fix3.zip'
    $deployDir = Join-Path $deployDesktop ('PSQ-UserManagement-Fix3-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Expand-Archive -LiteralPath $deployZip -DestinationPath $deployDir
    & (Join-Path $deployDir 'scripts\Deploy-UserManagement.ps1') -InstallRoot 'C:\Services\PhigrosScore'
}
```

安装路径不同，修改 `-InstallRoot '实际安装目录'`。仅校验时增加 `-ValidateOnly`。
脚本只替换后端程序、公开对应源码，在已安装 Caddyfile 中增补用户管理与申诉路由，并将建议图片设为不缓存，保留其他路由、曲绘缓存、曲库、谱面、运行配置和 APP 更新目录。源码目标文件名从现有 /source 路由读取。
部署前备份三个目标，成功后输出 DEPLOYED OK 与备份路径。失败时恢复这三个文件并重启原服务；数据库保留，新增表不会被删除。
升级会短暂重启后端和 Caddy，进行中的内存对局可能中断，请在空闲时段执行。

## 更新管理台网页

管理台与 Windows API 服务独立，部署后端不会替换已发布的管理台站点。

- 使用本地管理台：直接双击包内 `admin-console\启动APP后台网页.cmd`，首次自动安装依赖，需 Node.js 22.13.0 或更新版本。可先关闭原本地管理台，避免 3000 端口复用旧页面。
- 更新已有本地目录：关闭旧管理台，用 `admin-console\psq-admin-web` 中的源码覆盖原同名文件，保留原环境配置，再重新启动。依赖变化时运行 `npm ci`。
- 使用已发布的管理台：将 `admin-console\psq-admin-web` 的源码更新到该站点并重新构建发布。本包不自动发布该网站。

管理台继续使用现有 X-Admin-Token，无需新增管理员账号或密钥。

## 本地构建与交付状态

后端已执行 `cargo build --release --locked --offline`，管理台此前已执行 `npm run build`，本次管理台源码未改动。
本次未新增或运行测试，未打包 APK，未提交、推送、部署或发布。
包校验只读取包结构、二进制格式、对应源码和 Caddy 语法，不启动服务；不计算文件哈希。它不代表正式服务器已升级。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, $bom)
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)
Copy-Item -LiteralPath $zip -Destination $desktopZip
Write-Output $zip
Write-Output $desktopZip
Write-Output $stage
