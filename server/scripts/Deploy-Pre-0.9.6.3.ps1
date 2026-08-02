[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$publishTarget = Join-Path $InstallRoot 'current\scripts\Publish-AppUpdate.ps1'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.3.apk'

foreach ($required in @($publishTarget, $publishSource, $apkPath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.3-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force

& $publishTarget `
    -ApkPath $apkPath `
    -VersionCode 16 `
    -VersionName 'Pre-0.9.6.3' `
    -Changelog @( `
        '优化曲绘与头像加载，加入共享内存缓存、磁盘缓存、首屏预加载和低清占位。', `
        '左侧导航箭头支持上下拖动，并可在设置中选择显示或隐藏。', `
        '课题模式等级改用紧凑单字颜色标记，例如绿12、黄21、红49、彩51。' `
    )

Write-Output "Pre-0.9.6.3 已发布。脚本备份位于: $backup"
Write-Output '本次无需重启 Next-Phi-Backend 或 Caddy。'
