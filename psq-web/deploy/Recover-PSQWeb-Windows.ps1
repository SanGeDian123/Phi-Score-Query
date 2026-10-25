[CmdletBinding()]
param(
    [string]$InstallRoot = '',
    [string]$TaskName = 'PSQ-Web',
    [string]$PublicOrigin = 'https://phiscorequery.top',
    [switch]$Recover
)

$ErrorActionPreference = 'Stop'

function Show-PSQWebLog {
    $logFile = Join-Path $InstallRoot 'logs\psq-web.log'
    if (!(Test-Path -LiteralPath $logFile -PathType Leaf)) {
        Write-Output "No service log found: $logFile"
        return
    }
    Write-Output 'Recent service log (credential-bearing lines omitted):'
    Get-Content -LiteralPath $logFile -Tail 40 | ForEach-Object {
        if ($_ -match '(?i)session.?token|access.?token|refresh.?token|authorization|cookie.?secret|set-cookie|password|api.?key') {
            Write-Output '[credential-bearing line omitted]'
        } else { Write-Output $_ }
    }
}

function Get-PSQWebHealth {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
        if ($health.ok -and $health.webBuild -eq $release.webBuild -and $health.productVersion -eq $release.productVersion) {
            return $health
        }
    } catch {}
    return $null
}

function Show-PSQWebPublicHealth {
    foreach ($path in @('/api/health', '/', '/app/')) {
        $uri = $PublicOrigin.TrimEnd('/') + $path
        try {
            $reply = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 8
            if ($path -eq '/api/health') {
                $publicHealth = $reply.Content | ConvertFrom-Json
                [pscustomobject]@{ URL = $uri; Status = [int]$reply.StatusCode; BuildMatches = ($publicHealth.ok -and $publicHealth.webBuild -eq $release.webBuild) }
            } else {
                [pscustomobject]@{ URL = $uri; Status = [int]$reply.StatusCode; BuildMatches = $null }
            }
        } catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            [pscustomobject]@{ URL = $uri; Status = $status; BuildMatches = $false }
        }
    }
}

$task = Get-ScheduledTask -TaskName $TaskName -TaskPath '\' -ErrorAction SilentlyContinue
if (!$InstallRoot) {
    $directories = @($task.Actions.WorkingDirectory | Where-Object { $_ } | Select-Object -Unique)
    $InstallRoot = if ($directories.Count -eq 1) { $directories[0] } else { 'C:\Services\PSQWeb' }
}
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
Write-Output "Installation: $InstallRoot"
foreach ($relative in @('.env', 'server\index.mjs', 'dist\index.html', 'dist\release.json')) {
    if (!(Test-Path -LiteralPath (Join-Path $InstallRoot $relative) -PathType Leaf)) {
        throw "Missing installation file: $relative. Supply the actual installation with -InstallRoot."
    }
}
$release = Get-Content -LiteralPath (Join-Path $InstallRoot 'dist\release.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (!$release.webBuild -or !$release.productVersion) { throw 'Installed release metadata is invalid.' }
$port = 4173
foreach ($line in [IO.File]::ReadAllLines((Join-Path $InstallRoot '.env'))) {
    if ($line -match '^\s*PSQ_PORT\s*=\s*["'']?(\d+)["'']?\s*$') { $port = [int]$Matches[1] }
}
if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid PSQ_PORT in the existing .env.' }
$healthUrl = "http://127.0.0.1:$port/api/health"
if ($task) {
    $task | Select-Object TaskName, State, @{ N = 'MaxRunTime'; E = { $_.Settings.ExecutionTimeLimit } }
    Get-ScheduledTaskInfo -TaskName $TaskName -TaskPath '\' | Select-Object LastRunTime, LastTaskResult
}
$health = Get-PSQWebHealth
Write-Output "Local health OK: $([bool]$health)"
Show-PSQWebLog

if ($Recover -and !$health) {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run recovery in an administrator PowerShell on the server.'
    }
    if (!$task) {
        throw 'PSQ-Web scheduled task is missing. Install the persistent service using deploy\Install-PSQWeb-Windows.ps1 in the actual installation.'
    }
    if ($task.State -eq 'Disabled') { throw 'The scheduled task is disabled. Review why it was disabled before enabling it.' }
    $actions = @($task.Actions)
    if ($actions.Count -ne 1) { throw 'Unexpected task actions. No task was restarted.' }
    $action = $actions[0]
    $workingDirectoryMatches = $action.WorkingDirectory -and [IO.Path]::GetFullPath($action.WorkingDirectory).TrimEnd('\') -eq $InstallRoot
    $runner = Join-Path $InstallRoot 'deploy\Run-PSQWeb.ps1'
    $server = Join-Path $InstallRoot 'server\index.mjs'
    $argumentsMatch = $action.Arguments -match ('(?:^|["\s])' + [regex]::Escape($runner) + '(?=$|["\s])') -or $action.Arguments -match ('(?:^|["\s])' + [regex]::Escape($server) + '(?=$|["\s])')
    if (!$argumentsMatch -and !($workingDirectoryMatches -and $action.Arguments -match '(?:^|["\s])(?:\.\\)?server[\\/]index\.mjs(?=$|["\s])')) {
        throw 'Task action does not match the PSQ Web installation. No task was restarted.'
    }
    if ($task.State -eq 'Running') {
        Write-Output 'Restarting the matching, unhealthy PSQ Web task.'
        Stop-ScheduledTask -TaskName $TaskName -TaskPath '\'
    }
    $deadline = (Get-Date).AddSeconds(15)
    while ((Get-ScheduledTask -TaskName $TaskName -TaskPath '\').State -eq 'Running' -or @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count) {
        if ((Get-Date) -ge $deadline) { throw "Port $port remains occupied or the task has not stopped. No unknown process was terminated." }
        Start-Sleep -Milliseconds 250
    }
    Start-ScheduledTask -TaskName $TaskName -TaskPath '\'
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 500
        $health = Get-PSQWebHealth
    } until ($health -or (Get-Date) -ge $deadline)
    if (!$health) {
        Show-PSQWebLog
        throw 'The service did not pass its local health check. Review the preceding log.'
    }
    Write-Output 'The installed PSQ Web service now passes its local health check.'
}
if ($health) { $health | Select-Object ok, productVersion, webBuild, apiVersion, builtAt }
Show-PSQWebPublicHealth
if (!$Recover) { Write-Output 'Diagnosis only. Add -Recover to start or restart the matching unhealthy task.' }
