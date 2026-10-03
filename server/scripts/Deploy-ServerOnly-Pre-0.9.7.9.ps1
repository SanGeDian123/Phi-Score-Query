[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $CaddyExecutable
)

$ErrorActionPreference = 'Stop'

$bundleRoot = Split-Path -Parent $PSScriptRoot
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 10) {
    throw 'InstallRoot path is invalid.'
}

$currentRoot = Join-Path $installRoot 'current'
$backendRoot = Join-Path $currentRoot 'backend'
$backendTarget = Join-Path $backendRoot 'phi-backend.exe'
$infoTarget = Join-Path $backendRoot 'info'
$fontTarget = Join-Path $currentRoot 'backend\resources\fonts\Aldrich-Regular.ttf'
$assetsTarget = Join-Path $currentRoot 'backend\resources\templates\image\bn\phi_plugin_assets'
$installedCaddyExe = Join-Path $currentRoot 'caddy\caddy.exe'
$caddyExe = if ([string]::IsNullOrWhiteSpace($CaddyExecutable)) {
    $installedCaddyExe
} else {
    [IO.Path]::GetFullPath($CaddyExecutable)
}
$caddyTarget = Join-Path $currentRoot 'caddy\Caddyfile'
$runBackendTarget = Join-Path $currentRoot 'scripts\Run-Backend.ps1'
$runCaddyTarget = Join-Path $currentRoot 'scripts\Run-Caddy.ps1'
$publishAnnouncementTarget = Join-Path $currentRoot 'scripts\Publish-AppAnnouncement.ps1'
$appUpdateRoot = Join-Path $installRoot 'app-update'
$latestTarget = Join-Path $appUpdateRoot 'latest.json'
$announcementRoot = Join-Path $installRoot 'app-announcement'
$suggestionMediaRoot = Join-Path $installRoot 'suggestion-media'
$sourceOfferRoot = Join-Path $installRoot 'source'
$sourceArchiveName = 'backend-source-Pre-0.9.7.9.zip'
$sourceArchiveTarget = Join-Path $sourceOfferRoot $sourceArchiveName

$manifestPath = Join-Path $bundleRoot 'SHA256SUMS.json'
$serverOnlyManifestPath = Join-Path $bundleRoot 'SERVER_ONLY_MANIFEST.json'
$backendSource = Join-Path $bundleRoot 'backend\phi-backend.exe'
$infoSource = Join-Path $bundleRoot 'backend\info'
$fontSource = Join-Path $bundleRoot 'backend\resources\fonts\Aldrich-Regular.ttf'
$assetsSource = Join-Path $bundleRoot 'backend\resources\templates\image\bn\phi_plugin_assets'
$caddySource = Join-Path $bundleRoot 'caddy\Caddyfile'
$runBackendSource = Join-Path $bundleRoot 'scripts\Run-Backend.ps1'
$runCaddySource = Join-Path $bundleRoot 'scripts\Run-Caddy.ps1'
$publishAnnouncementSource = Join-Path $bundleRoot 'scripts\Publish-AppAnnouncement.ps1'
$sourceArchiveSource = Join-Path $bundleRoot ('source\' + $sourceArchiveName)

function Read-Utf8Json([string] $Path) {
    [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}

function Assert-NormalDirectory([string] $Path, [string] $Description) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $item = Get-Item -LiteralPath $Path -Force
    if (-not $item.PSIsContainer) {
        throw "$Description must be a directory, but a file exists at: $Path"
    }
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description cannot be a junction or symbolic link: $Path"
    }
}

function Ensure-NormalDirectory([string] $Path, [string] $Description) {
    Assert-NormalDirectory $Path $Description
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Copy-DirectoryContents([string] $Source, [string] $Destination, [bool] $ReplaceDestination = $false) {
    Assert-NormalDirectory $Source 'Source directory'
    if (-not (Test-Path -LiteralPath $Source)) {
        throw "Source directory does not exist: $Source"
    }
    Assert-NormalDirectory $Destination 'Destination directory'
    if ($ReplaceDestination -and (Test-Path -LiteralPath $Destination)) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    Ensure-NormalDirectory $Destination 'Destination directory'
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Get-FileSnapshot([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{ Exists = $false; Hash = $null; Length = $null }
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.PSIsContainer) {
        throw "Expected a file, but found a directory: $Path"
    }
    [pscustomobject]@{
        Exists = $true
        Hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
        Length = $item.Length
    }
}

function Assert-SnapshotUnchanged([string] $Description, $Before, [string] $Path) {
    $after = Get-FileSnapshot $Path
    if ([bool] $before.Exists -ne [bool] $after.Exists) {
        throw "$Description existence changed: $Path"
    }
    if ($before.Exists -and ([string] $before.Hash -ne [string] $after.Hash)) {
        throw "$Description SHA-256 changed: $Path"
    }
}

function Wait-AppTaskStopped([string] $TaskName) {
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) {
            throw "Timed out waiting for task to stop: $TaskName"
        }
        Start-Sleep -Milliseconds 250
    }
}

function Restore-OptionalFile([bool] $Existed, [string] $BackupPath, [string] $Target) {
    if ($Existed) {
        Ensure-NormalDirectory (Split-Path -Parent $Target) 'Restore parent directory'
        Copy-Item -LiteralPath $BackupPath -Destination $Target -Force
    } elseif (Test-Path -LiteralPath $Target) {
        Remove-Item -LiteralPath $Target -Force
    }
}

function Get-HttpStatus([string] $Uri) {
    try {
        Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10 | Out-Null
        return 200
    } catch {
        if ($_.Exception.Response) {
            return [int] $_.Exception.Response.StatusCode
        }
        return 0
    }
}

function Assert-CatalogManifest {
    $count = 0
    if ($null -eq $serverOnlyManifest.catalog -or
        -not [int]::TryParse([string] $serverOnlyManifest.catalog.songCount, [ref] $count) -or $count -le 0) {
        throw 'Invalid package: catalog.songCount must be a positive integer.'
    }
    $rows = @(Import-Csv -LiteralPath (Join-Path $infoSource 'info.csv') -Encoding UTF8)
    if ($rows.Count -ne $count) {
        throw "Invalid package catalog count: info.csv=$($rows.Count), manifest=$count."
    }
    $requiredIds = @($serverOnlyManifest.catalog.requiredSongIds)
    if ($requiredIds.Count -eq 0) { throw 'Invalid package: catalog.requiredSongIds is missing.' }
    foreach ($id in $requiredIds) {
        if ([string]::IsNullOrWhiteSpace([string] $id) -or -not ($rows | Where-Object { $_.id -eq [string] $id })) {
            throw "Invalid package: required catalog song missing: $id"
        }
    }
}

function Assert-CatalogPayload($Catalog, [string] $Scope) {
    if ($null -eq $Catalog -or $null -eq $Catalog.items) {
        throw "$Scope 曲库响应缺少 items。"
    }
    $expectedCount = [int] $serverOnlyManifest.catalog.songCount
    $actualItems = @($Catalog.items)
    if ($actualItems.Count -ne $expectedCount) {
        throw "$Scope 曲库数量不匹配：实际 $($actualItems.Count)，预期 $expectedCount。"
    }
    foreach ($requiredId in @($serverOnlyManifest.catalog.requiredSongIds)) {
        if (-not ($actualItems | Where-Object { [string] $_.id -eq [string] $requiredId })) {
            throw "$Scope 曲库缺少必需曲目 ID：$requiredId。"
        }
    }
    $expectedVersion = [string] $serverOnlyManifest.catalog.version
    if (-not [string]::IsNullOrWhiteSpace($expectedVersion) -and
        [string] $Catalog.version -ne $expectedVersion) {
        throw "$Scope 曲库版本不匹配：实际 $($Catalog.version)，预期 $expectedVersion。"
    }
}

function Wait-BackendHealth {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
            return
        } catch {
            if ((Get-Date) -ge $deadline) {
                throw 'New backend did not pass health check in 60 seconds.'
            }
        }
    } while ($true)
}

function Wait-BackendStable([int] $Seconds = 30) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $task = Get-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction Stop
        if ($task.State -ne 'Running') {
            throw "Backend task stopped during the ${Seconds}-second stability observation (state=$($task.State))."
        }
        try {
            Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
        } catch {
            throw "Backend health check failed during the ${Seconds}-second stability observation: $($_.Exception.Message)"
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
}

function Write-FailureReport(
    [string] $Path,
    [string] $Failure,
    [string] $BackendLog,
    [string] $CaddyLog
) {
    try {
        $parent = Split-Path -Parent $Path
        if (-not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }
        $lines = [System.Collections.Generic.List[string]]::new()
        $lines.Add("GeneratedAt: $([DateTimeOffset]::Now.ToString('o'))")
        $lines.Add($Failure)
        $lines.Add('')
        $lines.Add('=== Scheduled tasks ===')
        foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
            $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
            $info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue
            $lines.Add("$taskName state=$($task.State) lastResult=$($info.LastTaskResult) lastRun=$($info.LastRunTime)")
        }
        foreach ($log in @(
            [pscustomobject]@{ Name = 'backend.log'; Path = $BackendLog },
            [pscustomobject]@{ Name = 'caddy.log'; Path = $CaddyLog }
        )) {
            $lines.Add('')
            $lines.Add("=== Tail: $($log.Name) ===")
            if (Test-Path -LiteralPath $log.Path) {
                Get-Content -LiteralPath $log.Path -Tail 160 -ErrorAction SilentlyContinue |
                    ForEach-Object { $lines.Add([string] $_) }
            } else {
                $lines.Add('(log file not found)')
            }
        }
        [IO.File]::WriteAllLines($Path, $lines, [Text.Encoding]::UTF8)
    } catch {
        Write-Warning "Could not write deployment failure report: $($_.Exception.Message)"
    }
}

function Assert-LatestManifestSafe([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $manifest = Read-Utf8Json $Path
    $versionCode = 0
    if ($null -ne $manifest.versionCode) {
        if (-not [int]::TryParse(([string] $manifest.versionCode), [ref] $versionCode)) {
            throw "Existing APP update manifest has an invalid versionCode: $Path"
        }
    }
    $versionName = [string] $manifest.versionName
    # The server-only package may be installed alongside the matching APP
    # release (versionCode 45 / Pre-0.9.7.9).  Only a strictly newer APP
    # update is outside this package's compatibility boundary.
    if ($versionCode -gt 45) {
        throw "Refusing server-only deployment because $Path advertises a newer APP update (versionCode=$versionCode, versionName=$versionName). Restore the previous APP update manifest first."
    }
}

$failedStep = 'preparing deployment'
function Set-DeploymentStep([string] $Name) {
    $script:failedStep = $Name
    Write-Output "Deployment step: $Name"
}

# The package manifest is deliberately separate from the APP update manifest.
foreach ($required in @(
    $manifestPath,
    $serverOnlyManifestPath,
    $backendSource,
    $infoSource,
    $fontSource,
    $assetsSource,
    $caddySource,
    $runBackendSource,
    $runCaddySource,
    $publishAnnouncementSource,
    $sourceArchiveSource
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing server-only package file: $required"
    }
}

$bundleRootResolved = (Resolve-Path -LiteralPath $bundleRoot).Path.TrimEnd('\\')
Get-ChildItem -LiteralPath $bundleRoot -File -Recurse -Force | ForEach-Object {
    $relativeName = $_.FullName.Substring($bundleRootResolved.Length).TrimStart('\\').Replace('\\', '/')
    if ($_.Extension -ieq '.apk' -or
        $_.Name -ieq 'latest.json' -or
        $_.Name -ieq 'Publish-AppUpdate.ps1') {
        throw "Forbidden APP update artifact exists in server-only package: $relativeName"
    }
}

$manifest = Read-Utf8Json $manifestPath
if ([string] $manifest.packageType -ne 'server-only' -or [int] $manifest.versionCode -ne 45) {
    throw 'SHA256SUMS.json is not a Pre-0.9.7.9 server-only manifest.'
}
$serverOnlyManifest = Read-Utf8Json $serverOnlyManifestPath
if ([string] $serverOnlyManifest.kind -ne 'server-only' -or
    [bool] $serverOnlyManifest.appUpdateIsolation.publishesAppUpdate -or
    [bool] $serverOnlyManifest.appUpdateIsolation.modifiesLatestJson -or
    [bool] $serverOnlyManifest.appUpdateIsolation.includesApk) {
    throw 'SERVER_ONLY_MANIFEST.json does not describe an isolated server-only package.'
}

foreach ($entry in $manifest.files.psobject.Properties) {
    $relativeName = [string] $entry.Name
    if ($relativeName -match '(^|[\\/])latest\.json$|\.apk$|Publish-AppUpdate\.ps1$') {
        throw "Forbidden APP update file listed in server-only package: $relativeName"
    }
    $filePath = Join-Path $bundleRoot ($relativeName.Replace('/', '\'))
    if (-not (Test-Path -LiteralPath $filePath)) {
        throw "Missing package file listed in SHA256SUMS.json: $relativeName"
    }
    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne ([string] $entry.Value).ToLowerInvariant()) {
        throw "Package SHA-256 mismatch: $relativeName / $actualHash"
    }
}

Assert-CatalogManifest

$latestBefore = Get-FileSnapshot $latestTarget
Assert-LatestManifestSafe $latestTarget

if (-not (Test-Path -LiteralPath $caddyExe)) {
    throw "Caddy executable not found. Pass -CaddyExecutable or install it at: $installedCaddyExe"
}
$env:APP_LOG_DIR = ($installRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($installRoot -replace '\\', '/') + '/app-update'
$env:APP_ANNOUNCEMENT_DIR = ($installRoot -replace '\\', '/') + '/app-announcement'
$env:APP_AVATAR_DIR = ($installRoot -replace '\\', '/') + '/avatar'
$env:APP_SOURCE_DIR = ($installRoot -replace '\\', '/') + '/source'
& $caddyExe validate --config $caddySource --adapter caddyfile
if ($LASTEXITCODE -ne 0) {
    throw 'Server-only Caddy configuration validation failed.'
}

if ($ValidateOnly) {
    Write-Output 'Server-only package hashes, isolation manifest, latest.json guard, and Caddy configuration are valid.'
    Write-Output 'No installed files were changed.'
    return
}

foreach ($required in @($backendTarget, $installedCaddyExe, $caddyTarget, $runBackendTarget, $runCaddyTarget)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing installed server file: $required"
    }
}
foreach ($taskName in @('PhigrosScore-Backend', 'PhigrosScore-Caddy')) {
    if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
        throw "Missing scheduled task: $taskName"
    }
}

$backupRoot = Join-Path $installRoot ('backup\server-only-pre-0.9.7.9-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$backupReady = $false
$tasksStopped = $false
$hadFont = Test-Path -LiteralPath $fontTarget
$hadAssets = Test-Path -LiteralPath $assetsTarget
$hadInfo = Test-Path -LiteralPath $infoTarget
$hadPublishAnnouncement = Test-Path -LiteralPath $publishAnnouncementTarget
$hadSourceArchive = Test-Path -LiteralPath $sourceArchiveTarget

try {
    Set-DeploymentStep 'creating server-only backup'
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $backendTarget -Destination (Join-Path $backupRoot 'phi-backend.exe') -Force
    Copy-Item -LiteralPath $caddyTarget -Destination (Join-Path $backupRoot 'Caddyfile') -Force
    Copy-Item -LiteralPath $runBackendTarget -Destination (Join-Path $backupRoot 'Run-Backend.ps1') -Force
    Copy-Item -LiteralPath $runCaddyTarget -Destination (Join-Path $backupRoot 'Run-Caddy.ps1') -Force
    if ($hadFont) { Copy-Item -LiteralPath $fontTarget -Destination (Join-Path $backupRoot 'Aldrich-Regular.ttf') -Force }
    if ($hadAssets) { Copy-DirectoryContents $assetsTarget (Join-Path $backupRoot 'phi_plugin_assets') $true }
    if ($hadInfo) { Copy-DirectoryContents $infoTarget (Join-Path $backupRoot 'info') $true }
    if ($hadPublishAnnouncement) { Copy-Item -LiteralPath $publishAnnouncementTarget -Destination (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') -Force }
    if ($hadSourceArchive) { Copy-Item -LiteralPath $sourceArchiveTarget -Destination (Join-Path $backupRoot $sourceArchiveName) -Force }
    $backupReady = $true

    Set-DeploymentStep 'stopping scheduled tasks'
    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    Wait-AppTaskStopped 'PhigrosScore-Caddy'
    Wait-AppTaskStopped 'PhigrosScore-Backend'
    $tasksStopped = $true

    Set-DeploymentStep 'installing backend executable and info data'
    Copy-Item -LiteralPath $backendSource -Destination $backendTarget -Force
    Ensure-NormalDirectory $infoTarget 'Backend info directory'
    Copy-DirectoryContents $infoSource $infoTarget $true

    Set-DeploymentStep 'installing Phi-Plugin renderer resources'
    Ensure-NormalDirectory (Split-Path $fontTarget -Parent) 'Font directory'
    Ensure-NormalDirectory (Split-Path $assetsTarget -Parent) 'Phi-Plugin image directory'
    Copy-Item -LiteralPath $fontSource -Destination $fontTarget -Force
    Copy-DirectoryContents $assetsSource $assetsTarget $true

    Set-DeploymentStep 'installing Caddy and runtime scripts'
    Ensure-NormalDirectory (Split-Path $caddyTarget -Parent) 'Caddy directory'
    Ensure-NormalDirectory (Split-Path $runBackendTarget -Parent) 'Script directory'
    Copy-Item -LiteralPath $caddySource -Destination $caddyTarget -Force
    Copy-Item -LiteralPath $runBackendSource -Destination $runBackendTarget -Force
    Copy-Item -LiteralPath $runCaddySource -Destination $runCaddyTarget -Force
    Copy-Item -LiteralPath $publishAnnouncementSource -Destination $publishAnnouncementTarget -Force
    Ensure-NormalDirectory $sourceOfferRoot 'Source offer directory'
    Copy-Item -LiteralPath $sourceArchiveSource -Destination $sourceArchiveTarget -Force

    Set-DeploymentStep 'starting backend and checking health'
    Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
    Wait-BackendHealth

    Set-DeploymentStep 'checking suggestion authentication'
    $suggestionStatus = Get-HttpStatus 'http://127.0.0.1:3939/api/v2/suggestions/random'
    if ($suggestionStatus -ne 401) {
        throw "Suggestion API authentication probe failed: HTTP $suggestionStatus"
    }

    Set-DeploymentStep 'checking achievement API validation'
    $achievementStatus = Get-HttpStatus 'http://127.0.0.1:3939/api/v2/songs/achievement-rates?song_id=probe&difficulty=BAD'
    if ($achievementStatus -ne 422) {
        throw "Achievement API validation probe failed: HTTP $achievementStatus"
    }

    Set-DeploymentStep 'checking RKS guess authentication boundary'
    $rksGuessStatus = Get-HttpStatus 'http://127.0.0.1:3939/api/v2/games/rks-guess/probe'
    if ($rksGuessStatus -ne 401) {
        throw "RKS guess authentication probe failed: HTTP $rksGuessStatus"
    }

    Set-DeploymentStep 'checking daily checkin authentication boundary'
    $checkinStatus = Get-HttpStatus 'http://127.0.0.1:3939/api/v2/checkin?month=2026-09'
    if ($checkinStatus -ne 401) {
        throw "Daily checkin route did not reach the new backend: HTTP $checkinStatus"
    }
    $checkinLeaderboardStatus = Get-HttpStatus 'http://127.0.0.1:3939/api/v2/checkin/leaderboard'
    if ($checkinLeaderboardStatus -ne 401) {
        throw "Daily checkin leaderboard route did not reach the new backend: HTTP $checkinLeaderboardStatus"
    }

    Set-DeploymentStep 'checking alias search'
    $aliasResponse = Invoke-WebRequest 'http://127.0.0.1:3939/api/v2/songs/search?q=%E5%BC%82%E5%B8%B8&limit=5' -UseBasicParsing -TimeoutSec 10
    if ([int] $aliasResponse.StatusCode -ne 200 -or [string]::IsNullOrWhiteSpace($aliasResponse.Content)) {
        throw 'Alias search probe did not return HTTP 200 with a response body.'
    }
    try {
        $aliasPayload = $aliasResponse.Content | ConvertFrom-Json
        if ([int] $aliasPayload.total -lt 1 -or @($aliasPayload.items).Count -lt 1) {
            throw 'no matching song'
        }
    } catch {
        throw 'Alias search probe returned no song for the nicklist alias 异常.'
    }

    Set-DeploymentStep 'checking local catalog contents'
    $localCatalogResponse = Invoke-WebRequest 'http://127.0.0.1:3939/api/v2/songs/catalog' -UseBasicParsing -TimeoutSec 15
    $localCatalog = $localCatalogResponse.Content | ConvertFrom-Json
    Assert-CatalogPayload $localCatalog '本机'

    Set-DeploymentStep 'observing backend stability for 30 seconds'
    Wait-BackendStable 30

    Set-DeploymentStep 'starting Caddy'
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
        throw 'Caddy task failed to start.'
    }

    Set-DeploymentStep 'checking public health and catalog routes'
    if ((Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/health') -ne 200) {
        throw 'Public health endpoint probe failed.'
    }
    $publicCatalogResponse = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog' -UseBasicParsing -TimeoutSec 20
    $publicCatalog = $publicCatalogResponse.Content | ConvertFrom-Json
    Assert-CatalogPayload $publicCatalog '公网'
    if ([string] $publicCatalog.version -ne [string] $localCatalog.version) {
        throw "公网曲库版本与本机不一致：公网=$($publicCatalog.version)，本机=$($localCatalog.version)。"
    }

    Set-DeploymentStep 'checking public RKS guess route'
    $publicRksGuessStatus = Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/api/v2/games/rks-guess/probe'
    if ($publicRksGuessStatus -ne 401) {
        throw "Public RKS guess route did not reach the authenticated backend: HTTP $publicRksGuessStatus"
    }

    Set-DeploymentStep 'checking public daily checkin routes'
    $publicCheckinStatus = Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/api/v2/checkin?month=2026-09'
    if ($publicCheckinStatus -ne 401) {
        throw "Public daily checkin route did not reach the new backend: HTTP $publicCheckinStatus"
    }
    $publicCheckinLeaderboardStatus = Get-HttpStatus 'https://api.plc-liangpi-cup.xyz/api/v2/checkin/leaderboard'
    if ($publicCheckinLeaderboardStatus -ne 401) {
        throw "Public daily checkin leaderboard route did not reach the new backend: HTTP $publicCheckinLeaderboardStatus"
    }

    Set-DeploymentStep 'verifying APP update manifest isolation'
    Assert-SnapshotUnchanged 'APP update manifest' $latestBefore $latestTarget

    if (-not (Test-Path -LiteralPath $infoTarget\info.csv) -or
        -not (Test-Path -LiteralPath $infoTarget\difficulty.csv) -or
        -not (Test-Path -LiteralPath $infoTarget\nicklist.yaml) -or
        -not (Test-Path -LiteralPath $fontTarget) -or
        -not (Test-Path -LiteralPath $assetsTarget) -or
        -not (Test-Path -LiteralPath $sourceArchiveTarget)) {
        throw 'Required server-only resources were not installed.'
    }

    Write-Output 'Server-only Pre-0.9.7.9 safe RKS Guess backend and renderer deployed.'
    Write-Output "Backup: $backupRoot"
    Write-Output 'APP APK was not copied, APP update publisher was not installed, and app-update/latest.json was not modified.'
} catch {
    $originalError = $_
    $originalType = $originalError.Exception.GetType().FullName
    $originalMessage = $originalError.Exception.Message
    $originalStack = $originalError.ScriptStackTrace
    $rollbackError = $null
    try {
        Write-Output "Deployment failed during: $failedStep. Rolling back server files only..."
        Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
        Stop-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
        Wait-AppTaskStopped 'PhigrosScore-Caddy'
        Wait-AppTaskStopped 'PhigrosScore-Backend'
        if ($backupReady) {
            Copy-Item -LiteralPath (Join-Path $backupRoot 'phi-backend.exe') -Destination $backendTarget -Force
            Copy-Item -LiteralPath (Join-Path $backupRoot 'Caddyfile') -Destination $caddyTarget -Force
            Copy-Item -LiteralPath (Join-Path $backupRoot 'Run-Backend.ps1') -Destination $runBackendTarget -Force
            Copy-Item -LiteralPath (Join-Path $backupRoot 'Run-Caddy.ps1') -Destination $runCaddyTarget -Force
            Restore-OptionalFile $hadFont (Join-Path $backupRoot 'Aldrich-Regular.ttf') $fontTarget
            Restore-OptionalFile $hadPublishAnnouncement (Join-Path $backupRoot 'Publish-AppAnnouncement.ps1') $publishAnnouncementTarget
            Restore-OptionalFile $hadSourceArchive (Join-Path $backupRoot $sourceArchiveName) $sourceArchiveTarget
            if ($hadAssets) {
                Copy-DirectoryContents (Join-Path $backupRoot 'phi_plugin_assets') $assetsTarget $true
            } elseif (Test-Path -LiteralPath $assetsTarget) {
                Remove-Item -LiteralPath $assetsTarget -Recurse -Force
            }
            if ($hadInfo) {
                Copy-DirectoryContents (Join-Path $backupRoot 'info') $infoTarget $true
            } elseif (Test-Path -LiteralPath $infoTarget) {
                Remove-Item -LiteralPath $infoTarget -Recurse -Force
            }
        }
        # Never restore, remove, or rewrite anything under app-update.
        Assert-SnapshotUnchanged 'APP update manifest during rollback' $latestBefore $latestTarget

        Write-Output 'Starting rolled-back backend and requiring a healthy local endpoint...'
        Start-ScheduledTask -TaskName 'PhigrosScore-Backend'
        Wait-BackendHealth
        Wait-BackendStable 10
        Write-Output 'Rolled-back backend is healthy; starting Caddy...'
        Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
        Start-Sleep -Seconds 3
        if ((Get-ScheduledTask -TaskName 'PhigrosScore-Caddy').State -ne 'Running') {
            throw 'Rolled-back Caddy task failed to stay running.'
        }
    } catch {
        $rollbackError = $_.Exception.Message
    }
    if ($rollbackError) {
        Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Start-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    }
    $failure = "Server-only deployment failed during '$failedStep'. Original error [$originalType]: $originalMessage"
    if (-not [string]::IsNullOrWhiteSpace($originalStack)) { $failure += "`nScript stack:`n$originalStack" }
    if ($rollbackError) {
        $failure += "`nRollback also reported: $rollbackError"
    } else {
        $failure += "`nServer files rolled back; the old backend passed health/stability checks, Caddy was restarted, and APP update files were left untouched."
    }
    $failureReport = Join-Path $installRoot ('logs\server-only-deploy-failure-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    Write-FailureReport $failureReport $failure (Join-Path $installRoot 'logs\backend.log') (Join-Path $installRoot 'logs\caddy.log')
    $failure += "`nFailure report: $failureReport"
    throw $failure
}
