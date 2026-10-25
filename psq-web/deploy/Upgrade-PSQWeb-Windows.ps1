param(
    [string]$PackageRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$InstallRoot = 'C:\Services\PSQWeb',
    [string]$TaskName = 'PSQ-Web',
    [string]$NodePath = '',
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$PackageRoot = [IO.Path]::GetFullPath($PackageRoot).TrimEnd('\')
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($PackageRoot -eq $InstallRoot -or $PackageRoot.StartsWith($InstallRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or $InstallRoot.StartsWith($PackageRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Extract the new package to a separate directory, outside the running installation.'
}
if ($InstallRoot -eq [IO.Path]::GetPathRoot($InstallRoot).TrimEnd('\')) { throw 'The installation directory cannot be a drive root.' }
foreach ($relative in @('.env', 'server\index.mjs', 'dist\release.json')) {
    if (!(Test-Path -LiteralPath (Join-Path $InstallRoot $relative) -PathType Leaf)) { throw "Missing existing installation file: $relative. Supply the correct -InstallRoot." }
}
foreach ($relative in @('server\index.mjs', 'server\avatar.mjs', 'server\access-policy.mjs', 'dist\index.html', 'dist\release.json', 'release.config.json', 'access.config.json')) {
    if (!(Test-Path -LiteralPath (Join-Path $PackageRoot $relative) -PathType Leaf)) { throw "Incomplete new package: $relative" }
}
$release = Get-Content -LiteralPath (Join-Path $PackageRoot 'dist\release.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (!$release.webBuild -or !$release.productVersion) { throw 'Invalid package release metadata.' }
$port = 4173
foreach ($line in [IO.File]::ReadAllLines((Join-Path $InstallRoot '.env'))) {
    if ($line -match '^\s*PSQ_PORT\s*=\s*["'']?(\d+)["'']?\s*$') { $port = [int]$Matches[1] }
}
if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid PSQ_PORT in the existing .env.' }
$healthUrl = "http://127.0.0.1:$port/api/health"
$task = Get-ScheduledTask -TaskName $TaskName -TaskPath '\' -ErrorAction SilentlyContinue
if ($task) {
    $runnerPath = Join-Path $InstallRoot 'deploy\Run-PSQWeb.ps1'
    $serverPath = Join-Path $InstallRoot 'server\index.mjs'
    foreach ($action in @($task.Actions)) {
        $workingDirectoryMatches = $action.WorkingDirectory -and [IO.Path]::GetFullPath($action.WorkingDirectory).TrimEnd('\') -eq $InstallRoot
        $argumentsMatch = $action.Arguments -match ('(?:^|["\s])' + [regex]::Escape($runnerPath) + '(?=$|["\s])') -or $action.Arguments -match ('(?:^|["\s])' + [regex]::Escape($serverPath) + '(?=$|["\s])')
        if (!$workingDirectoryMatches -or !$argumentsMatch) { throw "Task $TaskName does not match this installation. No task was stopped." }
    }
} elseif (@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count) {
    throw 'No matching scheduled task exists, and the Node port is occupied. Press Ctrl+C in the original Node window, then run this script again.'
}
if (!$NodePath) {
    $standardNode = Join-Path $env:ProgramFiles 'nodejs\node.exe'
    if (Test-Path -LiteralPath $standardNode -PathType Leaf) { $NodePath = $standardNode }
    else { $command = Get-Command node.exe -ErrorAction SilentlyContinue; if ($command) { $NodePath = $command.Source } }
}
if (!$NodePath -or !(Test-Path -LiteralPath $NodePath -PathType Leaf)) { throw 'Node.js was not found. Supply -NodePath with its full path.' }
$NodePath = [IO.Path]::GetFullPath($NodePath)
$nodeVersion = & $NodePath --version
if ($LASTEXITCODE -ne 0 -or [version]($nodeVersion.Trim().TrimStart('v')) -lt [version]'22.13.0') { throw 'Node.js 22.13.0 or newer is required.' }
foreach ($module in @('index.mjs', 'avatar.mjs', 'session.mjs', 'proxy-policy.mjs', 'access-policy.mjs')) {
    & $NodePath --check (Join-Path $PackageRoot "server\$module")
    if ($LASTEXITCODE -ne 0) { throw "New server module failed syntax validation: $module" }
}
Write-Output "Installation: $InstallRoot"
Write-Output "New build: $($release.webBuild)"
Write-Output 'Preserved: .env, the entire .runtime directory, logs, and the installed task runner.'
if ($ValidateOnly) { Write-Output 'Validation passed. No files or tasks were changed.'; return }
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run the upgrade in an administrator PowerShell on the server.' }

function Get-CheckedInstallPath([string]$relative) {
    $absolute = [IO.Path]::GetFullPath((Join-Path $InstallRoot $relative))
    if (!$absolute.StartsWith($InstallRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Path is outside the installation: $relative" }
    return $absolute
}
$managed = @('dist', 'server', 'release.config.json', 'access.config.json', 'ROUTES.md', 'Start-PSQWeb.ps1', 'Start-PSQWeb.cmd', 'Start-PSQWeb.sh', 'README.md', 'DEPLOY.md', 'RELEASE.json', 'LICENSE', 'NOTICE', 'deploy\Upgrade-PSQWeb-Windows.ps1', 'deploy\Install-PSQWeb-Windows.ps1')
$backupRoot = Get-CheckedInstallPath ('.backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$failedRoot = Join-Path $backupRoot 'failed-new-build'
$moved = New-Object 'Collections.Generic.List[string]'
$replaced = New-Object 'Collections.Generic.List[string]'
$wasRunning = $task -and $task.State -eq 'Running'
$stopped = $false
try {
    if ($task) {
        Stop-ScheduledTask -TaskName $TaskName -TaskPath '\'
        $stopped = $true
        $deadline = (Get-Date).AddSeconds(20)
        while ((Get-ScheduledTask -TaskName $TaskName -TaskPath '\').State -eq 'Running' -or @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count) {
            if ((Get-Date) -ge $deadline) { throw 'The Node port is still occupied after stopping the task. No unknown process was terminated.' }
            Start-Sleep -Milliseconds 250
        }
    }
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    foreach ($relative in $managed) {
        $source = Join-Path $PackageRoot $relative
        if (!(Test-Path -LiteralPath $source)) { continue }
        $destination = Get-CheckedInstallPath $relative
        $backup = [IO.Path]::GetFullPath((Join-Path $backupRoot $relative))
        if (!$backup.StartsWith($backupRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid backup target.' }
        New-Item -ItemType Directory -Path (Split-Path $backup -Parent), (Split-Path $destination -Parent) -Force | Out-Null
        if (Test-Path -LiteralPath $destination) { Move-Item -LiteralPath $destination -Destination $backup; $moved.Add($relative) }
        $replaced.Add($relative)
        Copy-Item -LiteralPath $source -Destination $destination -Recurse
    }
    if ($task) {
        Start-ScheduledTask -TaskName $TaskName -TaskPath '\'
        $deadline = (Get-Date).AddSeconds(30)
        $ready = $false
        do {
            Start-Sleep -Milliseconds 500
            try { $health = Invoke-RestMethod $healthUrl -TimeoutSec 3; $ready = $health.ok -and $health.webBuild -eq $release.webBuild }
            catch { $ready = $false }
        } until ($ready -or (Get-Date) -ge $deadline)
        if (!$ready) { throw 'The new build did not pass its local health check.' }
        if ((Get-ScheduledTask -TaskName $TaskName -TaskPath '\').State -ne 'Running') { throw 'The scheduled task exited after startup.' }
        Write-Output 'Upgrade complete; the scheduled task is running.'
        $health
    } else {
        Write-Output 'Files upgraded. Start the existing foreground service, or install the scheduled task:'
        Write-Output ('powershell.exe -NoProfile -ExecutionPolicy Bypass -File "{0}" -InstallRoot "{1}"' -f (Join-Path $InstallRoot 'deploy\Install-PSQWeb-Windows.ps1'), $InstallRoot)
    }
    Write-Output "Previous distribution backup: $backupRoot"
    Write-Output 'Verify public access: https://phiscorequery.top/api/health'
    Write-Output 'The website and web login open automatically at the configured countdown target. Keep existing browser sessions and site data.'
} catch {
    $failure = $_.Exception.Message
    if ($task -and $replaced.Count) {
        Stop-ScheduledTask -TaskName $TaskName -TaskPath '\' -ErrorAction SilentlyContinue
        $deadline = (Get-Date).AddSeconds(20)
        while (@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count) {
            if ((Get-Date) -ge $deadline) { throw "Upgrade failed: $failure. Port remains occupied; rollback files are in $backupRoot. Stop the known Node process before restoring." }
            Start-Sleep -Milliseconds 250
        }
    }
    foreach ($relative in $replaced) {
        $destination = Get-CheckedInstallPath $relative
        if (Test-Path -LiteralPath $destination) {
            $failed = [IO.Path]::GetFullPath((Join-Path $failedRoot $relative))
            if (!$failed.StartsWith($failedRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid failed-build target.' }
            New-Item -ItemType Directory -Path (Split-Path $failed -Parent) -Force | Out-Null
            Move-Item -LiteralPath $destination -Destination $failed
        }
        if ($moved.Contains($relative)) { Move-Item -LiteralPath (Join-Path $backupRoot $relative) -Destination $destination }
    }
    if ($task -and $stopped -and $wasRunning) { Start-ScheduledTask -TaskName $TaskName -TaskPath '\' }
    throw "Upgrade failed: $failure. Previous distribution restored; .env and .runtime preserved."
}
