[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-upgrade-Pre-0.9.7.9-v53-with-apk-20260912'
$outputRoot = 'D:\PhigrosAppBuild'
$stage = Join-Path $outputRoot $name
$zip = Join-Path $outputRoot ($name + '.zip')
$apkName = 'Phi Score Query Pre-0.9.7.9.apk'
$apk = Join-Path $outputRoot $apkName
foreach ($target in @($stage,$zip,$apk)) {
    if (Test-Path -LiteralPath $target) { throw "Output already exists: $target" }
}
New-Item -ItemType Directory -Path $stage | Out-Null
foreach ($dir in @('backend','caddy','scripts','source')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $dir) | Out-Null
}
Copy-Item -LiteralPath 'D:\PhigrosAppBuild\ui-release-Pre-0.9.8.0\build\app\outputs\apk\release\app-release.apk' -Destination $apk
Copy-Item -LiteralPath $apk -Destination (Join-Path $stage $apkName)
Copy-Item -LiteralPath 'D:\PhiAdminWebCargoTarget\release\phi-backend.exe' -Destination (Join-Path $stage 'backend\phi-backend.exe')
Copy-Item -LiteralPath (Join-Path $repo 'server\caddy\Caddyfile') -Destination (Join-Path $stage 'caddy\Caddyfile')
Copy-Item -LiteralPath (Join-Path $repo 'backend-source\LICENSE') -Destination (Join-Path $stage 'LICENSE')
$bom = New-Object Text.UTF8Encoding($true)
$utf8 = New-Object Text.UTF8Encoding($false)
$deploy = Join-Path $repo 'server\scripts\Deploy-Release-Pre-0.9.7.9.ps1'
[IO.File]::WriteAllText((Join-Path $stage 'scripts\Deploy-Release-Pre-0.9.7.9.ps1'),[IO.File]::ReadAllText($deploy),$bom)
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
$metadata = [ordered]@{
    versionCode=53
    versionName='Pre-0.9.7.9'
    publishedAt='2026-09-12'
    apkUrl=('https://api.plc-liangpi-cup.xyz/app-update/releases/53/' + [Uri]::EscapeDataString($apkName))
    sha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    sizeBytes=(Get-Item -LiteralPath $apk).Length
    mandatory=$false
    changelog=@(
        '求建议随机选帖取消无评论帖子优先，其他推送规则保持不变。',
        '定数表大定数分界标题恢复为大数字。',
        '求建议收到评论、反馈有新进展时，相关功能入口增加红点提示。'
    )
}
[IO.File]::WriteAllText((Join-Path $stage 'latest.json'),($metadata | ConvertTo-Json -Depth 4),$utf8)
$manifest = [ordered]@{
    package=$name; kind='server-and-app-update'; versionCode=53; versionName='Pre-0.9.7.9'
    includesApk=$true; publishesAppUpdate=$true
    replaces=@('current/backend/phi-backend.exe','current/caddy/Caddyfile','source/backend-source-Pre-0.9.7.9.zip',('app-update/releases/53/'+$apkName),'app-update/latest.json')
    preserves=@('secrets.env','databases','resources','service scripts','scheduled task definitions','previous APK downloads','announcements','admin website')
}
[IO.File]::WriteAllText((Join-Path $stage 'RELEASE_MANIFEST.json'),($manifest | ConvertTo-Json -Depth 4),$utf8)
$readme = @'
# Pre-0.9.7.9 / versionCode 53 发布升级包

本包包含签名 APK、后端程序、对应后端源码、Caddyfile 和 APP 更新清单。
求建议随机选帖仅取消“优先推送无评论的帖子”，保留有效状态筛选和排除帖子规则。
客户端包含定数表大数字标题和求建议评论、反馈状态更新的入口红点。
正式部署会发布 APP 更新（非强制更新），不会发布独立公告或管理台网页。

适用于已有服务的 Windows 服务器，默认安装目录 C:\Services\PhigrosScore。
以管理员身份运行 PowerShell，将本 ZIP 上传到服务器桌面，先校验：

    Set-ExecutionPolicy -Scope Process Bypass -Force
    $desktop = [Environment]::GetFolderPath('Desktop')
    $zip = Join-Path $desktop 'server-upgrade-Pre-0.9.7.9-v53-with-apk-20260912.zip'
    $dest = Join-Path $desktop ('PSQ-v53-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Expand-Archive -LiteralPath $zip -DestinationPath $dest -ErrorAction Stop
    Set-Location $dest
    & .\scripts\Deploy-Release-Pre-0.9.7.9.ps1 -ValidateOnly

校验成功后，在同一窗口正式部署：

    & .\scripts\Deploy-Release-Pre-0.9.7.9.ps1

安装目录不同时，两条脚本命令都需指定 -InstallRoot。
-ValidateOnly 不停止服务、不替换已安装文件；校验成功不代表已上线。
脚本备份全部替换目标，停止精确匹配的后端/Caddy 服务，替换文件后检查本机与公网健康、接口鉴权、更新清单和 APK 下载。
失败时恢复旧文件并重启服务；保留数据库、配置、曲库、曲绘、字体、运行脚本和计划任务定义。
APK 使用独立 v53 下载目录，保留旧版下载文件。只为 APP 更新协议计算 APK SHA-256，不生成全包哈希清单。
本地 APK 文件名为 Phi Score Query Pre-0.9.7.9.apk。
'@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'),$readme,$bom)
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
Write-Output $apk
Write-Output $zip
