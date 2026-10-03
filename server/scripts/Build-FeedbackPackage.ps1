$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-only-upgrade-Pre-0.9.7.9-Unread-Dots-20260912'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = Join-Path 'D:\PhigrosAppBuild' ($name + '.zip')
if (Test-Path -LiteralPath $zip) { throw "Package already exists: $zip" }
if (Test-Path -LiteralPath $stage) { throw "Stage already exists: $stage" }
New-Item -ItemType Directory -Path $stage | Out-Null
foreach ($dir in @('backend','caddy','scripts','source','admin-console')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $dir) | Out-Null
}
Copy-Item -LiteralPath 'D:\PhiAdminWebCargoTarget\release\phi-backend.exe' -Destination (Join-Path $stage 'backend\phi-backend.exe')
Copy-Item -LiteralPath (Join-Path $repo 'server\caddy\Caddyfile') -Destination (Join-Path $stage 'caddy\Caddyfile')
$deploySource = Join-Path $repo 'server\scripts\Deploy-Feedback-Pre-0.9.7.9.ps1'
$bom = New-Object Text.UTF8Encoding($true)
[IO.File]::WriteAllText((Join-Path $stage 'scripts\Deploy-Feedback-Pre-0.9.7.9.ps1'), [IO.File]::ReadAllText($deploySource), $bom)
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\LICENSE') -Destination (Join-Path $stage 'LICENSE')

function Create-SourceZip([string] $Base, [string] $Target, [string[]] $Trees, [string[]] $Files) {
    $archive = [IO.Compression.ZipFile]::Open($Target, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $selected = @()
        foreach ($tree in $Trees) {
            $selected += @(Get-ChildItem -LiteralPath (Join-Path $Base $tree) -Recurse -File -Force | Where-Object {
                $_.FullName -notmatch '[\\/](node_modules|target|\.git|\.wrangler|__pycache__)[\\/]' -and
                $_.Name -notmatch '^\.env|^config\.toml$|^latest\.json$|\.apk$|\.db($|-)|\.log$|^secrets\.'
            })
        }
        foreach ($file in $Files) { $selected += Get-Item -LiteralPath (Join-Path $Base $file) }
        foreach ($file in ($selected | Sort-Object FullName -Unique)) {
            $relative = $file.FullName.Substring($Base.TrimEnd('\').Length).TrimStart('\').Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Fastest) | Out-Null
        }
    } finally { $archive.Dispose() }
}
Create-SourceZip (Join-Path $repo 'backend-source') (Join-Path $stage 'source\backend-source-Pre-0.9.7.9.zip') @('src','crates','info','resources\fonts','resources\templates','tests') @('Cargo.toml','Cargo.lock','config.example.toml','LICENSE','MODIFICATIONS.md')
Create-SourceZip (Join-Path $repo 'psq-admin-web') (Join-Path $stage 'admin-console\psq-admin-web-source.zip') @('app','components','hooks','lib','public') @('package.json','package-lock.json','tsconfig.json','vite.config.ts','next.config.ts','components.json','README.md','.openai\hosting.json')

$manifest = @{
    package=$name; kind='server-only'; versionName='Pre-0.9.7.9'; clientVersionCode=52
    includesApk=$false; publishesAppUpdate=$false; modifiesAppUpdate=$false
    updates=@('durable RKS Guess win leaderboard with idempotent per-game recording','feedback live updates','Phi-Plugin OVER FLOW spacing','backend source offer')
    clientChanges=@('constant level headings restored to large numbers','in-app unread dots for suggestion comments and feedback updates; requires the separately distributed APK')
    adminConsole='Current source included for separate website deployment; no website is published by this package.'
    database='Adds rks_guess_wins ledger and index. Existing stats, checkin and feedback data are preserved. Historical in-memory games cannot be backfilled.'
}
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_ONLY_MANIFEST.json'),($manifest | ConvertTo-Json -Depth 5),$bom)
$readme = @'
# APP 红点提醒配套服务器升级包（不发布更新）

配套客户端：Pre-0.9.7.9，versionCode 52。本包不含 APK，不修改 APP 更新目录，也不会发布版本更新通知。
本次大定数标题和界面红点属于客户端修改，须另外安装配套 APK；仅部署本包不会改变已安装 APP 的界面。
服务端保持现有求建议评论通知、反馈长轮询及累计胜场榜能力，本次未新增服务端业务逻辑。
新增累计胜场榜，单人和公开匹配的最终胜利各计一场，平局与取消匹配不计；每场游戏只入库一次。
榜单显示前 100 名，同胜场按达到当前胜场的时间排序，未公开资料的玩家匿名显示，隐藏用户不展示。
旧版本没有持久化对局胜利记录，不能补算历史胜场。胜场从升级后开始累计，后台重启不会清除已记录胜场。
包含反馈提交、附件、管理接口及状态长轮询；管理员修改状态后主动唤醒等待请求。
包含 Phi-Plugin 成绩图 OVER FLOW 分隔区域上移 25px 的修复。

在已运行 PSQ 后端的 Windows 服务器上，以管理员身份打开 PowerShell。
保留现有 secrets.env、数据库、曲库、曲绘、字体、运行脚本和计划任务。
本次只替换后端程序、Caddyfile 和对应后端源码归档。数据库启动时自动新增胜场表及必要索引，无需手工执行 SQL。

先校验（不会停止服务或替换文件）：
```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
& .\scripts\Deploy-Feedback-Pre-0.9.7.9.ps1 -ValidateOnly
```

校验通过后部署：
```powershell
& .\scripts\Deploy-Feedback-Pre-0.9.7.9.ps1
```

默认安装目录 C:\Services\PhigrosScore，可通过 -InstallRoot 指定已有服务目录。
脚本自动备份三个替换目标，检查本机健康、胜场及反馈接口鉴权、公网路由。失败时自动恢复旧文件并重启服务。
回滚保留新增表及数据，不恢复或删除数据库。备份目录显示在命令输出中。
按要求不计算哈希；-ValidateOnly 校验包结构、禁入文件及 Caddy 配置，不代表已上线。

## 管理台网页须单独发布

现有管理台是独立 Sites 站点，不由这台 Windows 后端服务器托管。
admin-console/psq-admin-web-source.zip 是包含反馈管理界面的完整源码。
若原管理台尚未包含“用户反馈”，还需单独部署此网页源码；已有该功能则无需重复部署网页。
本部署脚本不会自动提交、推送或发布该站点。

新版 APP 存活并连接时，使用最长 25 秒的挂起请求等待更新，管理员保存后立即唤醒，无需等满超时。
跨后端进程的数据库变更有最多约 5 秒的兜底检查间隔。APP 被系统挂起或关闭时仍受 Android 后台限制，WorkManager 约 15 分钟检查作为兜底，并非系统推送。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'),$readme,$bom)
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
Write-Output $zip
