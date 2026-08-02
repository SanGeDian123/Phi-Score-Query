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
$caddyExe = Join-Path $current 'caddy\caddy.exe'
$caddyTarget = Join-Path $current 'caddy\Caddyfile'
$runCaddyTarget = Join-Path $current 'scripts\Run-Caddy.ps1'
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$avatarTarget = Join-Path $InstallRoot 'avatar'
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.2.apk'
$backendSource = Join-Path $bundleRoot 'phi-backend.exe'
$caddySource = Join-Path $bundleRoot 'Caddyfile'
$runCaddySource = Join-Path $bundleRoot 'Run-Caddy.ps1'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$avatarSource = Join-Path $bundleRoot 'avatar'

foreach ($required in @(
    $backendTarget,
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

$avatarFiles = @(Get-ChildItem -LiteralPath $avatarSource -File)
if ($avatarFiles.Count -ne 109) { throw "头像资源数量异常，应为 109，实际为 $($avatarFiles.Count)。" }
$invalidAvatarFiles = @($avatarFiles | Where-Object Name -NotMatch '^[0-9a-f]{64}\.png$')
if ($invalidAvatarFiles.Count -ne 0) {
    throw '头像资源文件名校验失败。'
}

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.2-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backup 'phi-backend.exe') -Force
Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backup 'Caddyfile') -Force
Copy-Item -LiteralPath $runCaddyTarget -Destination (Join-Path $backup 'Run-Caddy.ps1') -Force
Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
Get-ChildItem -Path (Join-Path $current 'backend\resources\usage_stats.db*') -ErrorAction SilentlyContinue |
    Copy-Item -Destination $backup -Force

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

$taskNames = @('PhigrosScore-Backend', 'PhigrosScore-Caddy')
$oldAvatarMoved = $false
try {
    foreach ($taskName in $taskNames) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }
    foreach ($taskName in $taskNames) {
        $deadline = (Get-Date).AddSeconds(20)
        while ((Get-ScheduledTask -TaskName $taskName).State -eq 'Running') {
            if ((Get-Date) -ge $deadline) { throw "等待任务停止超时: $taskName" }
            Start-Sleep -Milliseconds 250
        }
    }

    if (Test-Path -LiteralPath $avatarTarget) {
        Move-Item -LiteralPath $avatarTarget -Destination (Join-Path $backup 'avatar')
        $oldAvatarMoved = $true
    }
    Move-Item -LiteralPath $avatarStaging -Destination $avatarTarget

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
        -VersionCode 15 `
        -VersionName 'Pre-0.9.6.2' `
        -Changelog @( `
            '单击单曲成绩可进入曲目详情，查看完整曲绘、曲目信息以及各难度定数、物量和谱师。', `
            '新增玩家 RKS 排行榜，展示 Token 绑定昵称、游戏头像、课题模式等级和排名。', `
            '底部导航改为全局侧边导航，支持任意页面从屏幕左侧向右滑动打开，并加入首次使用引导。' `
        )

    Write-Output "Pre-0.9.6.2 已发布。备份位于: $backup"
    Write-Output '请继续执行部署说明中的公网验收命令。'
} catch {
    foreach ($taskName in $taskNames) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
    Copy-Item -LiteralPath (Join-Path $backup 'phi-backend.exe') -Destination $backendTarget -Force
    Copy-Item -LiteralPath (Join-Path $backup 'Caddyfile') -Destination $caddyTarget -Force
    Copy-Item -LiteralPath (Join-Path $backup 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
    Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force
    if (Test-Path -LiteralPath $avatarTarget) {
        Move-Item -LiteralPath $avatarTarget -Destination (Join-Path $backup 'failed-avatar')
    }
    if ($oldAvatarMoved -and (Test-Path -LiteralPath (Join-Path $backup 'avatar'))) {
        Move-Item -LiteralPath (Join-Path $backup 'avatar') -Destination $avatarTarget
    }
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端和 Caddy 配置。原始错误: $($_.Exception.Message)"
}
