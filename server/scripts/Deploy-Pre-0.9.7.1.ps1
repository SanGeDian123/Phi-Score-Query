[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path $PSScriptRoot -Parent
$currentRoot = Join-Path $InstallRoot 'current'
$backendRoot = Join-Path $currentRoot 'backend'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$sourceRoot = Join-Path $InstallRoot 'source'
$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$sourceUrl = 'https://github.com/SanGeDian123/Phi-Score-Query/tree/pre-0.9.7.1/backend-source'

if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "缺少升级包校验清单: $manifestPath"
}
$manifest = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
foreach ($entry in $manifest.files.psobject.Properties) {
    $filePath = Join-Path $bundleRoot $entry.Name
    if (-not (Test-Path -LiteralPath $filePath)) { throw "缺少升级文件: $($entry.Name)" }
    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne [string] $entry.Value) {
        throw "升级文件 SHA-256 校验失败: $($entry.Name) / $actualHash"
    }
}

$caddyExe = Join-Path $currentRoot 'caddy\caddy.exe'
$caddySource = Join-Path $bundleRoot 'caddy\Caddyfile'
$apkSource = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.7.1.apk'
$publishTarget = Join-Path $currentRoot 'scripts\Publish-AppUpdate.ps1'
$newPublishedApk = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.7.1.apk'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'

foreach ($required in @($caddyExe, $caddySource, $apkSource, $publishTarget)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少服务器现有文件或升级文件: $required" }
}
foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "缺少计划任务: $taskName"
    }
}

if (Test-Path -LiteralPath (Join-Path $InstallRoot 'secrets.env')) {
    Get-Content -LiteralPath (Join-Path $InstallRoot 'secrets.env') | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
$env:APP_LOG_DIR = ($InstallRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($InstallRoot -replace '\\', '/') + '/app-update'
$env:APP_AVATAR_DIR = ($InstallRoot -replace '\\', '/') + '/avatar'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw '新 Caddy 配置验证失败，尚未修改线上文件。' }

$operations = @(
    @{ Source = 'backend\phi-backend.exe'; Target = (Join-Path $backendRoot 'phi-backend.exe') },
    @{ Source = 'backend\info\info.csv'; Target = (Join-Path $backendRoot 'info\info.csv') },
    @{ Source = 'backend\info\difficulty.csv'; Target = (Join-Path $backendRoot 'info\difficulty.csv') },
    @{ Source = 'backend\info\nicklist.yaml'; Target = (Join-Path $backendRoot 'info\nicklist.yaml') },
    @{ Source = 'backend\resources\templates\image\bn\minimal.json'; Target = (Join-Path $backendRoot 'resources\templates\image\bn\minimal.json') },
    @{ Source = 'backend\resources\templates\image\bn\minimal.svg.jinja'; Target = (Join-Path $backendRoot 'resources\templates\image\bn\minimal.svg.jinja') },
    @{ Source = 'caddy\Caddyfile'; Target = (Join-Path $currentRoot 'caddy\Caddyfile') },
    @{ Source = 'scripts\Publish-AppUpdate.ps1'; Target = $publishTarget },
    @{ Source = 'licenses\AGPL-3.0.txt'; Target = (Join-Path $backendRoot 'LICENSE') },
    @{ Source = 'SOURCE_OFFER.md'; Target = (Join-Path $currentRoot 'SOURCE_OFFER.md') },
    @{ Source = 'source\backend-source-pre-0.9.7.1.zip'; Target = (Join-Path $sourceRoot 'backend-source-pre-0.9.7.1.zip') }
)

$backup = Join-Path $InstallRoot ("backup\pre-0.9.7.1-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$filesBackup = Join-Path $backup 'files'
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $filesBackup, $appUpdateBackup | Out-Null
$backupState = @()
$previousApkName = $null
$hadLatest = Test-Path -LiteralPath $latestTarget
$hadNewPublishedApk = Test-Path -LiteralPath $newPublishedApk
$backupReady = $false

function Stop-AppTask([string] $TaskName) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "等待任务停止超时: $TaskName" }
        Start-Sleep -Milliseconds 250
    }
}

function Wait-Endpoint([string] $Uri, [int] $TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            return Invoke-RestMethod -Uri $Uri -TimeoutSec 8
        } catch {
            if ((Get-Date) -ge $deadline) { throw "接口未能在 $TimeoutSeconds 秒内通过检查: $Uri" }
            Start-Sleep -Seconds 2
        }
    } while ($true)
}

try {
    foreach ($operation in $operations) {
        $target = [string] $operation.Target
        $relativeBackup = ($target.Substring($InstallRoot.Length).TrimStart('\\') -replace '[:]', '_')
        $backupPath = Join-Path $filesBackup $relativeBackup
        $existed = Test-Path -LiteralPath $target
        if ($existed) {
            New-Item -ItemType Directory -Force -Path (Split-Path $backupPath -Parent) | Out-Null
            Copy-Item -LiteralPath $target -Destination $backupPath -Force
        }
        $backupState += [pscustomobject]@{ Target = $target; Backup = $backupPath; Existed = $existed }
    }
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
        $oldLatest = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8) | ConvertFrom-Json
        if (-not [string]::IsNullOrWhiteSpace([string] $oldLatest.apkUrl)) {
            $previousApkName = Split-Path ([Uri] [string] $oldLatest.apkUrl).AbsolutePath -Leaf
            $previousApkPath = Join-Path $appUpdateRoot $previousApkName
            if (Test-Path -LiteralPath $previousApkPath) {
                Copy-Item -LiteralPath $previousApkPath -Destination (Join-Path $appUpdateBackup $previousApkName) -Force
            }
        }
    }
    if ($hadNewPublishedApk) {
        Copy-Item -LiteralPath $newPublishedApk -Destination (Join-Path $appUpdateBackup 'preexisting-Pre-0.9.7.1.apk') -Force
    }
    $backupReady = $true

    Stop-AppTask 'PhigrosScore-Caddy'
    Stop-AppTask 'PhigrosScore-Backend'

    foreach ($operation in $operations) {
        $source = Join-Path $bundleRoot ([string] $operation.Source)
        $target = [string] $operation.Target
        New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
        Copy-Item -LiteralPath $source -Destination $target -Force
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Endpoint 'http://127.0.0.1:3939/health' | Out-Null
    $localCatalog = Wait-Endpoint 'http://127.0.0.1:3939/api/v2/songs/catalog'
    if (@($localCatalog.items).Count -ne 312 -or [string]::IsNullOrWhiteSpace([string] $localCatalog.version)) {
        throw "本机后端曲库校验失败：实际曲目数 $(@($localCatalog.items).Count)。"
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy 任务启动失败。'
    }

    $publicHealth = Invoke-WebRequest -Uri 'https://api.plc-liangpi-cup.xyz/health' -UseBasicParsing -TimeoutSec 15
    if ([int] $publicHealth.StatusCode -ne 200 -or [string] $publicHealth.Headers['X-Source-Code'] -ne $sourceUrl) {
        throw '公网健康检查或 AGPL 源码响应头校验失败。'
    }
    if ([string] $publicHealth.Headers['Link'] -notmatch [regex]::Escape($sourceUrl)) {
        throw '公网响应缺少 AGPL Link 源码入口。'
    }

    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $client = New-Object System.Net.Http.HttpClient($handler)
    try {
        $sourceResponse = $client.GetAsync('https://api.plc-liangpi-cup.xyz/source').GetAwaiter().GetResult()
        if ([int] $sourceResponse.StatusCode -ne 302 -or [string] $sourceResponse.Headers.Location.AbsoluteUri -ne $sourceUrl) {
            throw '公网 /source 源码跳转校验失败。'
        }
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }

    & $publishTarget `
        -ApkPath $apkSource `
        -VersionCode 27 `
        -VersionName 'Pre-0.9.7.1' `
        -PublishedAt '2026-08-02' `
        -InstallRoot $InstallRoot `
        -Changelog @(
            '项目进入公开测试阶段，公开客户端、服务器工具与实际使用的后端修改源码。',
            '设置页新增“关于”页面，可访问 Phi-Score-Query 与 Next-Phi-Backend 的 GitHub 仓库。',
            '后端响应新增 AGPL 源码入口，并提供固定版本的完整对应源码。'
        )

    $latest = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8) | ConvertFrom-Json
    $apkHash = (Get-FileHash -LiteralPath $newPublishedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $expectedApkHash = [string] $manifest.files.'Phi-Score-Query-Pre-0.9.7.1.apk'
    if ([int] $latest.versionCode -ne 27 -or [string] $latest.versionName -ne 'Pre-0.9.7.1') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedApkHash -or $apkHash -ne $expectedApkHash) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.7.1 已发布。备份位于: $backup"
    Write-Output "曲库版本: $($localCatalog.version) / 曲目数: $(@($localCatalog.items).Count)"
    Write-Output "AGPL 对应源码: $sourceUrl"
    Write-Output '后端、Caddy、源码入口和应用更新清单均已通过检查。'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if ($backupReady) {
        foreach ($state in $backupState) {
            if ([bool] $state.Existed) {
                New-Item -ItemType Directory -Force -Path (Split-Path ([string] $state.Target) -Parent) | Out-Null
                Copy-Item -LiteralPath ([string] $state.Backup) -Destination ([string] $state.Target) -Force
            } elseif (Test-Path -LiteralPath ([string] $state.Target)) {
                Remove-Item -LiteralPath ([string] $state.Target) -Force
            }
        }
        if (Test-Path -LiteralPath $latestTarget) { Remove-Item -LiteralPath $latestTarget -Force }
        if ($hadLatest) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'latest.json') -Destination $latestTarget -Force
        }
        if (Test-Path -LiteralPath $newPublishedApk) { Remove-Item -LiteralPath $newPublishedApk -Force }
        if ($hadNewPublishedApk) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'preexisting-Pre-0.9.7.1.apk') -Destination $newPublishedApk -Force
        }
        if (-not [string]::IsNullOrWhiteSpace($previousApkName)) {
            $previousBackup = Join-Path $appUpdateBackup $previousApkName
            if (Test-Path -LiteralPath $previousBackup) {
                Copy-Item -LiteralPath $previousBackup -Destination (Join-Path $appUpdateRoot $previousApkName) -Force
            }
        }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端、Caddy、源码说明和更新清单。原始错误: $($_.Exception.Message)"
}
