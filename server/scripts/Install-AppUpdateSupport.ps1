[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$current = Join-Path $InstallRoot 'current'
$caddyExe = Join-Path $current 'caddy\caddy.exe'
$caddyTarget = Join-Path $current 'caddy\Caddyfile'
$runCaddyTarget = Join-Path $current 'scripts\Run-Caddy.ps1'
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$publishAnnouncementTarget = Join-Path $current 'scripts\Publish-AppAnnouncement.ps1'
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6-Fix.apk'

foreach ($required in @(
    $caddyExe,
    (Join-Path $bundleRoot 'Caddyfile'),
    (Join-Path $bundleRoot 'Run-Caddy.ps1'),
    (Join-Path $bundleRoot 'Publish-AppUpdate.ps1'),
    (Join-Path $bundleRoot 'Publish-AppAnnouncement.ps1'),
    $apkPath
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$backup = Join-Path $InstallRoot ("backup\app-update-bootstrap-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup, (Join-Path $InstallRoot 'app-update'), (Join-Path $InstallRoot 'app-announcement') | Out-Null
Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backup 'Caddyfile') -Force
Copy-Item -LiteralPath $runCaddyTarget -Destination (Join-Path $backup 'Run-Caddy.ps1') -Force

Copy-Item -LiteralPath (Join-Path $bundleRoot 'Caddyfile') -Destination $caddyTarget -Force
Copy-Item -LiteralPath (Join-Path $bundleRoot 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
Copy-Item -LiteralPath (Join-Path $bundleRoot 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force
Copy-Item -LiteralPath (Join-Path $bundleRoot 'Publish-AppAnnouncement.ps1') -Destination $publishAnnouncementTarget -Force

$env:APP_LOG_DIR = ($InstallRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($InstallRoot -replace '\\', '/') + '/app-update'
$env:APP_ANNOUNCEMENT_DIR = ($InstallRoot -replace '\\', '/') + '/app-announcement'
& $caddyExe validate --config $caddyTarget --adapter caddyfile
if ($LASTEXITCODE -ne 0) {
    Copy-Item -LiteralPath (Join-Path $backup 'Caddyfile') -Destination $caddyTarget -Force
    Copy-Item -LiteralPath (Join-Path $backup 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
    throw 'Caddy 配置验证失败，已恢复原文件。'
}

Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
$stopDeadline = (Get-Date).AddSeconds(15)
while ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -eq 'Running') {
    if ((Get-Date) -ge $stopDeadline) { throw '等待 Caddy 任务停止超时。' }
    Start-Sleep -Milliseconds 250
}
Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'

& $publishTarget `
    -ApkPath $apkPath `
    -VersionCode 12 `
    -VersionName 'Pre-0.9.6-Fix' `
    -Changelog @( `
        '新增应用内联网更新：启动时自动检查、展示更新内容并安全下载安装包。', `
        '设置页新增自动检查开关与手动检查更新入口。', `
        '安装前校验安装包的 SHA-256、包名、版本号与签名。' `
    )

Write-Output "自动更新服务已启用。配置备份位于: $backup"
Write-Output "请访问 https://api.plc-liangpi-cup.xyz/app-update/latest.json 完成公网验收。"
