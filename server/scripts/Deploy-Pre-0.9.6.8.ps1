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
$configTarget = Join-Path $current 'backend\config.toml'
$templateTargetRoot = Join-Path $current 'backend\resources\templates\image\bn'
$minimalJsonTarget = Join-Path $templateTargetRoot 'minimal.json'
$minimalSvgTarget = Join-Path $templateTargetRoot 'minimal.svg.jinja'
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.6.8.apk'

$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.8.apk'
$backendSource = Join-Path $bundleRoot 'phi-backend.exe'
$minimalJsonSource = Join-Path $bundleRoot 'resources\templates\image\bn\minimal.json'
$minimalSvgSource = Join-Path $bundleRoot 'resources\templates\image\bn\minimal.svg.jinja'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'

foreach ($required in @(
    $backendTarget,
    $configTarget,
    $templateTargetRoot,
    $publishTarget,
    $apkPath,
    $backendSource,
    $minimalJsonSource,
    $minimalSvgSource,
    $publishSource
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}
if (-not (Get-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue)) {
    throw '缺少计划任务: PhigrosScore-Backend'
}

$expectedHashes = @{
    $apkPath = 'f64e8e2cdb883ba6549080a943ade8d134aed927bfa611584013ca10e0921092'
    $backendSource = '5ef498468566902aebaff49952b0b9bb754476a6a4f6932beba72296a90508df'
    $minimalJsonSource = 'd8ea659cbc095c64134421e2eb1f1b142ea54009987ad908df89e4d94312e018'
    $minimalSvgSource = 'ffc95b7a3acaa5fd0d10ce8bc38f4907322fb9238729110a3e986a3593f91e09'
}
foreach ($entry in $expectedHashes.GetEnumerator()) {
    $actualHash = (Get-FileHash -LiteralPath $entry.Key -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $entry.Value) { throw "文件 SHA-256 校验失败: $($entry.Key) / $actualHash" }
}

$backup = Join-Path $InstallRoot ("backup\pre-0.9.6.8-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $backup, $appUpdateBackup | Out-Null

$hadMinimalJson = Test-Path -LiteralPath $minimalJsonTarget
$hadMinimalSvg = Test-Path -LiteralPath $minimalSvgTarget
$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
$backupReady = $false

function Wait-BackendTaskStopped {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName 'PhigrosScore-Backend').State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw '等待后端任务停止超时。' }
        Start-Sleep -Milliseconds 250
    }
}

try {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-BackendTaskStopped

    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backup 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $configTarget -Destination (Join-Path $backup 'config.toml') -Force
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
    if ($hadMinimalJson) {
        Copy-Item -LiteralPath $minimalJsonTarget -Destination (Join-Path $backup 'minimal.json') -Force
    }
    if ($hadMinimalSvg) {
        Copy-Item -LiteralPath $minimalSvgTarget -Destination (Join-Path $backup 'minimal.svg.jinja') -Force
    }
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.8.apk') -Force
    }
    $backupReady = $true

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    $configText = [IO.File]::ReadAllText($configTarget, [Text.Encoding]::UTF8)
    $footerPattern = '(?m)^footer_text\s*=\s*".*"\s*$'
    $footerRegex = [Text.RegularExpressions.Regex]::new($footerPattern)
    if (-not $footerRegex.IsMatch($configText)) {
        throw '线上 config.toml 中未找到 branding.footer_text，已停止部署。'
    }
    $updatedConfig = $footerRegex.Replace(
        $configText,
        'footer_text = "Phi Score Query · Pre-0.9.6.8"',
        1
    )
    $configTemp = "$configTarget.pre0968"
    [IO.File]::WriteAllText($configTemp, $updatedConfig, (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $configTemp -Destination $configTarget -Force
    Copy-Item -LiteralPath $minimalJsonSource -Destination $minimalJsonTarget -Force
    Copy-Item -LiteralPath $minimalSvgSource -Destination $minimalSvgTarget -Force
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

    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 21 `
        -VersionName 'Pre-0.9.6.8' `
        -InstallRoot $InstallRoot `
        -Changelog @( `
            '统一 APP 主题强调色：白日模式为深蓝色，黑夜模式为绿色；成绩概览顶部改为列表式玩家信息栏。', `
            'B30 成绩图新增“简约”样式，可在设置中与“经典”样式切换，黑白配色自动跟随 APP 当前主题。', `
            '简约 B30 使用横向曲绘与玩家头像，完整展示 P3+B27 的曲名、难度、得分、定数、单曲 RKS、当前 ACC 与推分 ACC。', `
            'B30 成绩图水印更新为 Phi Score Query · Pre-0.9.6.8。' `
        )

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 21 -or [string] $latest.versionName -ne 'Pre-0.9.6.8') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedHashes[$apkPath] -or $publishedHash -ne $expectedHashes[$apkPath]) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.6.8 已发布。备份位于: $backup"
    Write-Output '后端已重启并通过本机健康检查；本次未修改或重启 Caddy。'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backup 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'config.toml') -Destination $configTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force

        if ($hadMinimalJson) {
            Copy-Item -LiteralPath (Join-Path $backup 'minimal.json') -Destination $minimalJsonTarget -Force
        } elseif (Test-Path -LiteralPath $minimalJsonTarget) {
            Remove-Item -LiteralPath $minimalJsonTarget -Force
        }
        if ($hadMinimalSvg) {
            Copy-Item -LiteralPath (Join-Path $backup 'minimal.svg.jinja') -Destination $minimalSvgTarget -Force
        } elseif (Test-Path -LiteralPath $minimalSvgTarget) {
            Remove-Item -LiteralPath $minimalSvgTarget -Force
        }

        if (Test-Path -LiteralPath $latestTarget) {
            Remove-Item -LiteralPath $latestTarget -Force
        }
        if ($hadLatest) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'latest.json') -Destination $latestTarget -Force
        }
        if (Test-Path -LiteralPath $publishedApkTarget) {
            Remove-Item -LiteralPath $publishedApkTarget -Force
        }
        if ($hadPublishedApk) {
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.6.8.apk') -Destination $publishedApkTarget -Force
        }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端、配置、B30 模板和更新清单。原始错误: $($_.Exception.Message)"
}
