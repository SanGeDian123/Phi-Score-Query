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
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.1.apk'

foreach ($required in @(
    $caddyExe,
    (Join-Path $bundleRoot 'Caddyfile'),
    (Join-Path $bundleRoot 'Publish-AppUpdate.ps1'),
    $apkPath
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.1-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backup 'Caddyfile') -Force

Copy-Item -LiteralPath (Join-Path $bundleRoot 'Caddyfile') -Destination $caddyTarget -Force
Copy-Item -LiteralPath (Join-Path $bundleRoot 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force

$env:APP_LOG_DIR = ($InstallRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($InstallRoot -replace '\\', '/') + '/app-update'
& $caddyExe validate --config $caddyTarget --adapter caddyfile
if ($LASTEXITCODE -ne 0) {
    Copy-Item -LiteralPath (Join-Path $backup 'Caddyfile') -Destination $caddyTarget -Force
    throw 'Caddy 配置验证失败，已恢复原配置。'
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
    -VersionCode 13 `
    -VersionName 'Pre-0.9.6.1' `
    -Changelog @( `
        '单曲成绩接入 Next-Phi-Backend 别名搜索，并在曲目信息右侧加入渐隐曲绘。', `
        'B30 与 P30 补充第 28 至 30 名，并以 OVER FLOW 分割线区分。', `
        '成绩排行支持一键回到当前板块顶部，所有曲目名称均完整换行显示。' `
    )

Write-Output "Pre-0.9.6.1 已发布。Caddy 配置备份位于: $backup"
Write-Output '请继续执行部署说明中的公网验收命令。'
