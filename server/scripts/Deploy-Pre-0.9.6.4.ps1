[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($InstallRoot.Length -lt 10) { throw 'InstallRoot 路径异常。' }

$current = Join-Path $InstallRoot 'current'
$backendTarget = Join-Path $current 'backend\phi-backend.exe'
$backendResources = Join-Path $current 'backend\resources'
$caddyExe = Join-Path $current 'caddy\caddy.exe'
$caddyTarget = Join-Path $current 'caddy\Caddyfile'
$runCaddyTarget = Join-Path $current 'scripts\Run-Caddy.ps1'
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$avatarTarget = Join-Path $InstallRoot 'avatar'
$appUpdateTarget = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateTarget 'latest.json'
$publishedApkTarget = Join-Path $appUpdateTarget 'Phi-Score-Query-Pre-0.9.6.4.apk'

$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.4.apk'
$backendSource = Join-Path $bundleRoot 'phi-backend.exe'
$caddySource = Join-Path $bundleRoot 'Caddyfile'
$runCaddySource = Join-Path $bundleRoot 'Run-Caddy.ps1'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$avatarSource = Join-Path $bundleRoot 'avatar'

foreach ($required in @(
    $backendTarget,
    $backendResources,
    $caddyExe,
    $caddyTarget,
    $runCaddyTarget,
    $publishTarget,
    $backendSource,
    $caddySource,
    $runCaddySource,
    $publishSource,
    $avatarSource,
    $apkPath
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "缺少计划任务: $taskName"
    }
}

$expectedApkHash = '95fb447febe6819a386c1c6e72e4277498d3bb80ea9b9521b2eca9809cefc4f4'
$expectedBackendHash = 'fcc393677f14dd9a6c65bdd4a96f3365305f57f81931514307b34db7958d4bb6'
$actualApkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$actualBackendHash = (Get-FileHash -LiteralPath $backendSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualApkHash -ne $expectedApkHash) { throw "APK SHA-256 校验失败: $actualApkHash" }
if ($actualBackendHash -ne $expectedBackendHash) { throw "后端 SHA-256 校验失败: $actualBackendHash" }

$avatarFiles = @(Get-ChildItem -LiteralPath $avatarSource -File)
if ($avatarFiles.Count -ne 109) { throw "头像资源数量异常，应为 109，实际为 $($avatarFiles.Count)。" }
$invalidAvatarFiles = @($avatarFiles | Where-Object Name -NotMatch '^[0-9a-f]{64}\.png$')
if ($invalidAvatarFiles.Count -ne 0) { throw '头像资源文件名校验失败。' }

$avatarStaging = Join-Path $InstallRoot ("avatar-staging-" + [Guid]::NewGuid().ToString('N'))
Copy-Item -LiteralPath $avatarSource -Destination $avatarStaging -Recurse

if (Test-Path -LiteralPath (Join-Path $InstallRoot 'secrets.env')) {
    Get-Content -LiteralPath (Join-Path $InstallRoot 'secrets.env') | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
$env:APP_LOG_DIR = ($InstallRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($InstallRoot -replace '\\', '/') + '/app-update'
$env:APP_AVATAR_DIR = ($avatarStaging -replace '\\', '/')
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw '新 Caddy 配置验证失败，尚未修改线上文件。' }

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.4-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$databaseBackup = Join-Path $backup 'database'
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $databaseBackup, $appUpdateBackup | Out-Null

$taskNames = @('PhigrosScore-Backend', 'PhigrosScore-Caddy')
$oldAvatarMoved = $false
$newAvatarInstalled = $false
$backupReady = $false

function Wait-TaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "等待任务停止超时: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

try {
    foreach ($taskName in $taskNames) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }
    foreach ($taskName in $taskNames) {
        Wait-TaskStopped $taskName
    }

    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backup 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backup 'Caddyfile') -Force
    Copy-Item -LiteralPath $runCaddyTarget -Destination (Join-Path $backup 'Run-Caddy.ps1') -Force
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
    Get-ChildItem -LiteralPath $backendResources -Filter 'usage_stats.db*' -File -ErrorAction SilentlyContinue |
        Copy-Item -Destination $databaseBackup -Force
    if (Test-Path -LiteralPath $latestTarget) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
    }
    if (Test-Path -LiteralPath $publishedApkTarget) {
        Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.4.apk') -Force
    }
    $backupReady = $true

    if (Test-Path -LiteralPath $avatarTarget) {
        Move-Item -LiteralPath $avatarTarget -Destination (Join-Path $backup 'avatar')
        $oldAvatarMoved = $true
    }
    Move-Item -LiteralPath $avatarStaging -Destination $avatarTarget
    $newAvatarInstalled = $true

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $runCaddySource -Destination $runCaddyTarget -Force
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    $backendReady = $false
    $backendDeadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
            $backendReady = $true
        } catch {
            if ((Get-Date) -ge $backendDeadline) { throw '新后端未能在 60 秒内通过健康检查。' }
        }
    } until ($backendReady)

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy 任务启动失败。'
    }

    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 17 `
        -VersionName 'Pre-0.9.6.4' `
        -Changelog @( `
            '修复排行榜详细排名中部分玩家头像不显示的问题；异常旧记录会先显示默认头像，玩家刷新存档后自动补全。', `
            '返回键改为先回到成绩概览，主页需在提示后连续按两次返回键才退出 APP。', `
            '修复更新日志中 Pre-0.9.6.2 被错误标记为“当前版本”的问题。', `
            '首次登录成功并刷新成绩后，在后台自动生成一次 B30 成绩图，并显示在成绩概览底部。', `
            '保存 B30 成绩图时直接写入系统相册，不再要求选择保存路径。' `
        )

    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    # Windows PowerShell 5 默认会按系统 ANSI 代码页读取无 BOM 的 UTF-8 文件。
    # latest.json 由 Publish-AppUpdate.ps1 以 UTF-8（无 BOM）写入，必须显式指定 UTF-8，
    # 否则中文更新日志可能吞掉 JSON 引号并导致 ConvertFrom-Json 报“数组无效”。
    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    if ($publishedHash -ne $expectedApkHash) { throw "发布后的 APK 校验失败: $publishedHash" }
    if ([int] $latest.versionCode -ne 17 -or [string] $latest.versionName -ne 'Pre-0.9.6.4') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash) { throw 'latest.json SHA-256 校验失败。' }

    Write-Output "Pre-0.9.6.4 已发布。备份位于: $backup"
    Write-Output '请继续执行部署说明中的公网验收命令。'
} catch {
    foreach ($taskName in $taskNames) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2

    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backup 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Caddyfile') -Destination $caddyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force

        Get-ChildItem -LiteralPath $backendResources -Filter 'usage_stats.db*' -File -ErrorAction SilentlyContinue |
            Remove-Item -Force
        Get-ChildItem -LiteralPath $databaseBackup -File -ErrorAction SilentlyContinue |
            Copy-Item -Destination $backendResources -Force

        if (Test-Path -LiteralPath $publishedApkTarget) {
            Remove-Item -LiteralPath $publishedApkTarget -Force
        }
        if (Test-Path -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.4.apk')) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.4.apk') -Destination $publishedApkTarget -Force
        }
        if (Test-Path -LiteralPath (Join-Path $appUpdateBackup 'latest.json')) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'latest.json') -Destination $latestTarget -Force
        }
    }

    if ($newAvatarInstalled -and (Test-Path -LiteralPath $avatarTarget)) {
        Move-Item -LiteralPath $avatarTarget -Destination (Join-Path $backup 'failed-avatar')
    }
    if ($oldAvatarMoved -and (Test-Path -LiteralPath (Join-Path $backup 'avatar'))) {
        Move-Item -LiteralPath (Join-Path $backup 'avatar') -Destination $avatarTarget
    }
    if (Test-Path -LiteralPath $avatarStaging) {
        Remove-Item -LiteralPath $avatarStaging -Recurse -Force
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端、排行榜数据库、Caddy 配置和更新清单。原始错误: $($_.Exception.Message)"
}
