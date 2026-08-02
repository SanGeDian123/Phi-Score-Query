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
$publishTarget = Join-Path $current 'scripts\Publish-AppUpdate.ps1'
$infoTargetRoot = Join-Path $current 'backend\info'
$illustrationTargetRoot = Join-Path $current 'backend\resources\ill'
$infoTarget = Join-Path $infoTargetRoot 'info.csv'
$difficultyTarget = Join-Path $infoTargetRoot 'difficulty.csv'
$nicklistTarget = Join-Path $infoTargetRoot 'nicklist.yaml'
$newSongFileName = '星拂云锦featkoi.S9ryne.png'
$illTarget = Join-Path $illustrationTargetRoot "ill\$newSongFileName"
$illLowTarget = Join-Path $illustrationTargetRoot "illLow\$newSongFileName"
$illBlurTarget = Join-Path $illustrationTargetRoot "illBlur\$newSongFileName"
$appUpdateRoot = Join-Path $InstallRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$publishedApkTarget = Join-Path $appUpdateRoot 'Phi-Score-Query-Pre-0.9.7.0-Fix.apk'

$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.7.0-Fix.apk'
$backendSource = Join-Path $bundleRoot 'phi-backend.exe'
$caddySource = Join-Path $bundleRoot 'Caddyfile'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$infoSource = Join-Path $bundleRoot 'info\info.csv'
$difficultySource = Join-Path $bundleRoot 'info\difficulty.csv'
$nicklistSource = Join-Path $bundleRoot 'info\nicklist.yaml'
$illSource = Join-Path $bundleRoot "resources\ill\ill\$newSongFileName"
$illLowSource = Join-Path $bundleRoot "resources\ill\illLow\$newSongFileName"
$illBlurSource = Join-Path $bundleRoot "resources\ill\illBlur\$newSongFileName"

foreach ($required in @(
    $backendTarget,
    $caddyExe,
    $caddyTarget,
    $publishTarget,
    $infoTarget,
    $difficultyTarget,
    $nicklistTarget,
    (Join-Path $illustrationTargetRoot 'ill'),
    (Join-Path $illustrationTargetRoot 'illLow'),
    (Join-Path $illustrationTargetRoot 'illBlur'),
    $apkPath,
    $backendSource,
    $caddySource,
    $publishSource,
    $infoSource,
    $difficultySource,
    $nicklistSource,
    $illSource,
    $illLowSource,
    $illBlurSource
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少部署文件: $required" }
}
foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "缺少计划任务: $taskName"
    }
}

$expectedHashes = @{
    $apkPath = 'feff60cc2cfa432ecd7056903546a21441cb8793d820528890f2f73ca28fdbe1'
    $backendSource = 'de8f9801887e74936a5948d6116c36f7934bb5de92cb5a4846a67a28cec95f12'
    $caddySource = '5808cd8ab9c6a34aa39573c0e5b11f0d13fa56a31fb37f92d55e4c7a9b7e8200'
    $publishSource = '549dd7efd4a06b6d9151256ac00718451ffd04a0a4314a8f4e17ee4973e2e27d'
    $infoSource = 'd1c008c7eee4de587ddc5854699c601555c1050c0e8ff831ce30ffcffb4d0b69'
    $difficultySource = 'f8a492b129857142288cd6905a4da9a5f70fbbe7234a964de09e11dd6d1f5711'
    $nicklistSource = '9c285275b6997c775e5dc7636977ab07d74ed25053e5b2c6c1ea378bd1d58df5'
    $illSource = '9d16f4e0ef3cafc3631421ea179cecf7a50862a51cab83d0af415f4c3a3af5eb'
    $illLowSource = '9080ef395903b6644b2491f75819e4a92e5601b3808af87b6710240e31ad920a'
    $illBlurSource = '89156d91b80cdc7f57b81aca7672881d589a3598b5c96f6d77964ed502872e1d'
}
foreach ($entry in $expectedHashes.GetEnumerator()) {
    $actualHash = (Get-FileHash -LiteralPath $entry.Key -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $entry.Value) {
        throw "文件 SHA-256 校验失败: $($entry.Key) / $actualHash"
    }
}
$sourceInfo = @(Import-Csv -LiteralPath $infoSource -Encoding UTF8)
$sourceDifficulty = @(Import-Csv -LiteralPath $difficultySource -Encoding UTF8)
if (
    $sourceInfo.Count -ne 312 -or
    $sourceDifficulty.Count -ne 312 -or
    @($sourceInfo | Where-Object id -eq '星拂云锦featkoi.S9ryne').Count -ne 1
) {
    throw '发布包中的曲库文件数量异常或缺少《星拂云锦 feat. koi》。'
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

$backup = Join-Path $InstallRoot ("backup\pre-0.9.7.0-fix-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$appUpdateBackup = Join-Path $backup 'app-update'
New-Item -ItemType Directory -Force -Path $backup, $appUpdateBackup, (Join-Path $backup 'info'), (Join-Path $backup 'illustrations') | Out-Null
$hadLatest = Test-Path -LiteralPath $latestTarget
$hadPublishedApk = Test-Path -LiteralPath $publishedApkTarget
$hadIll = Test-Path -LiteralPath $illTarget
$hadIllLow = Test-Path -LiteralPath $illLowTarget
$hadIllBlur = Test-Path -LiteralPath $illBlurTarget
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
    Stop-AppTask 'PhigrosScore-Caddy'
    Stop-AppTask 'PhigrosScore-Backend'

    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backup 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backup 'Caddyfile') -Force
    Copy-Item -LiteralPath $publishTarget -Destination (Join-Path $backup 'Publish-AppUpdate.ps1') -Force
    Copy-Item -LiteralPath $infoTarget -Destination (Join-Path $backup 'info\info.csv') -Force
    Copy-Item -LiteralPath $difficultyTarget -Destination (Join-Path $backup 'info\difficulty.csv') -Force
    Copy-Item -LiteralPath $nicklistTarget -Destination (Join-Path $backup 'info\nicklist.yaml') -Force
    if ($hadIll) {
        Copy-Item -LiteralPath $illTarget -Destination (Join-Path $backup 'illustrations\ill.png') -Force
    }
    if ($hadIllLow) {
        Copy-Item -LiteralPath $illLowTarget -Destination (Join-Path $backup 'illustrations\illLow.png') -Force
    }
    if ($hadIllBlur) {
        Copy-Item -LiteralPath $illBlurTarget -Destination (Join-Path $backup 'illustrations\illBlur.png') -Force
    }
    if ($hadLatest) {
        Copy-Item -LiteralPath $latestTarget -Destination (Join-Path $appUpdateBackup 'latest.json') -Force
    }
    if ($hadPublishedApk) {
        Copy-Item -LiteralPath $publishedApkTarget -Destination (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.7.0-Fix.apk') -Force
    }
    $backupReady = $true

    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force
    Copy-Item -LiteralPath $infoSource -Destination $infoTarget -Force
    Copy-Item -LiteralPath $difficultySource -Destination $difficultyTarget -Force
    Copy-Item -LiteralPath $nicklistSource -Destination $nicklistTarget -Force
    Copy-Item -LiteralPath $illSource -Destination $illTarget -Force
    Copy-Item -LiteralPath $illLowSource -Destination $illLowTarget -Force
    Copy-Item -LiteralPath $illBlurSource -Destination $illBlurTarget -Force

    $installedFiles = @{
        $backendTarget = $backendSource
        $caddyTarget = $caddySource
        $publishTarget = $publishSource
        $infoTarget = $infoSource
        $difficultyTarget = $difficultySource
        $nicklistTarget = $nicklistSource
        $illTarget = $illSource
        $illLowTarget = $illLowSource
        $illBlurTarget = $illBlurSource
    }
    foreach ($entry in $installedFiles.GetEnumerator()) {
        $installedHash = (Get-FileHash -LiteralPath $entry.Key -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($installedHash -ne $expectedHashes[$entry.Value]) {
            throw "安装后的文件 SHA-256 校验失败: $($entry.Key)"
        }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-Endpoint 'http://127.0.0.1:3939/health' | Out-Null
    $localCatalog = Wait-Endpoint 'http://127.0.0.1:3939/api/v2/songs/catalog'
    $localCatalogCount = @($localCatalog.items).Count
    $localNewSongCount = @(
        $localCatalog.items | Where-Object {
            [string] $_.composer -eq 'S9ryne' -and
            [string] $_.name -match 'feat\. koi$'
        }
    ).Count
    if (
        [string]::IsNullOrWhiteSpace([string] $localCatalog.version) -or
        $localCatalogCount -ne 312 -or
        $localNewSongCount -ne 1
    ) {
        throw "后端曲库接口校验失败：实际曲目数 $localCatalogCount，新曲匹配数 $localNewSongCount。"
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy 任务启动失败。'
    }
    $publicCatalog = Wait-Endpoint 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog'
    if ([string] $publicCatalog.version -ne [string] $localCatalog.version) {
        throw '公网曲库接口版本与本机后端不一致。'
    }
    $publicCatalogCount = @($publicCatalog.items).Count
    $publicNewSongCount = @(
        $publicCatalog.items | Where-Object {
            [string] $_.composer -eq 'S9ryne' -and
            [string] $_.name -match 'feat\. koi$'
        }
    ).Count
    if (
        $publicCatalogCount -ne 312 -or
        $publicNewSongCount -ne 1
    ) {
        throw "公网曲库接口校验失败：实际曲目数 $publicCatalogCount，新曲匹配数 $publicNewSongCount。"
    }
    $encodedNewSongFileName = [Uri]::EscapeDataString($newSongFileName)
    $newSongArtwork = Invoke-WebRequest `
        -Method Head `
        -Uri "https://api.plc-liangpi-cup.xyz/_ill/illLow/$encodedNewSongFileName" `
        -UseBasicParsing `
        -TimeoutSec 15
    if ([int] $newSongArtwork.StatusCode -ne 200) {
        throw '新曲曲绘公网检查失败。'
    }

    $releaseChangelog = [string[]] @(
        '修复 Pre-0.9.7.0 服务器发布包遗漏运行时曲库文件，导致 APP 同步后仍只有旧曲目的问题。',
        '内置曲库和服务器曲库更新至 312 首，新增《星拂云锦 feat. koi》及其完整定数信息。',
        '服务器同步更新曲库、别名表和新曲曲绘，并在发布前验证公网曲库数量。'
    )
    & $publishTarget `
        -ApkPath $apkPath `
        -VersionCode 25 `
        -VersionName 'Pre-0.9.7.0-Fix' `
        -InstallRoot $InstallRoot `
        -Changelog $releaseChangelog

    $latestJson = [IO.File]::ReadAllText($latestTarget, [Text.Encoding]::UTF8)
    $latest = $latestJson | ConvertFrom-Json
    $publishedHash = (Get-FileHash -LiteralPath $publishedApkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $installedBackendHash = (Get-FileHash -LiteralPath $backendTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $installedCaddyHash = (Get-FileHash -LiteralPath $caddyTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([int] $latest.versionCode -ne 25 -or [string] $latest.versionName -ne 'Pre-0.9.7.0-Fix') {
        throw 'latest.json 版本信息校验失败。'
    }
    if ([string] $latest.sha256 -ne $expectedHashes[$apkPath] -or $publishedHash -ne $expectedHashes[$apkPath]) {
        throw '发布后的 APK 或 latest.json SHA-256 校验失败。'
    }
    if ($installedBackendHash -ne $expectedHashes[$backendSource]) {
        throw '安装后的后端 SHA-256 校验失败。'
    }
    if ($installedCaddyHash -ne $expectedHashes[$caddySource]) {
        throw '安装后的 Caddyfile SHA-256 校验失败。'
    }

    Write-Output "Pre-0.9.7.0-Fix 已发布。备份位于: $backup"
    Write-Output "曲库版本: $($localCatalog.version) / 曲目数: $(@($localCatalog.items).Count)"
    Write-Output '后端、Caddy、曲库公网接口和更新清单均已通过检查。'
} catch {
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if ($backupReady) {
        Copy-Item -LiteralPath (Join-Path $backup 'phi-backend.exe') -Destination $backendTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Caddyfile') -Destination $caddyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'Publish-AppUpdate.ps1') -Destination $publishTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'info\info.csv') -Destination $infoTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'info\difficulty.csv') -Destination $difficultyTarget -Force
        Copy-Item -LiteralPath (Join-Path $backup 'info\nicklist.yaml') -Destination $nicklistTarget -Force
        if ($hadIll) {
            Copy-Item -LiteralPath (Join-Path $backup 'illustrations\ill.png') -Destination $illTarget -Force
        } elseif (Test-Path -LiteralPath $illTarget) {
            Remove-Item -LiteralPath $illTarget -Force
        }
        if ($hadIllLow) {
            Copy-Item -LiteralPath (Join-Path $backup 'illustrations\illLow.png') -Destination $illLowTarget -Force
        } elseif (Test-Path -LiteralPath $illLowTarget) {
            Remove-Item -LiteralPath $illLowTarget -Force
        }
        if ($hadIllBlur) {
            Copy-Item -LiteralPath (Join-Path $backup 'illustrations\illBlur.png') -Destination $illBlurTarget -Force
        } elseif (Test-Path -LiteralPath $illBlurTarget) {
            Remove-Item -LiteralPath $illBlurTarget -Force
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
            Copy-Item -LiteralPath (Join-Path $appUpdateBackup 'Phi-Score-Query-Pre-0.9.7.0-Fix.apk') -Destination $publishedApkTarget -Force
        }
    }

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    throw "部署失败，已恢复原后端、曲库、曲绘、Caddy 配置和更新清单。原始错误: $($_.Exception.Message)"
}
