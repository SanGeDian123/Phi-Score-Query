param(
    [string]$InstallRoot = 'C:\Services\PSQWeb',
    [string]$NodePath = '',
    [string]$TaskName = 'PSQ-Web'
)

$ErrorActionPreference = 'Stop'

function Test-PSQWebAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-PSQWebRunner {
    return @'
param([Parameter(Mandatory = $true)][string]$NodePath)
$ErrorActionPreference = 'Stop'
$psqRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $psqRoot
$psqLogDirectory = Join-Path $psqRoot 'logs'
New-Item -ItemType Directory -Path $psqLogDirectory -Force | Out-Null
$psqLogFile = Join-Path $psqLogDirectory 'psq-web.log'
try {
    # Native stderr includes ordinary Node messages on Windows PowerShell 5.1.
    $ErrorActionPreference = 'Continue'
    & $NodePath (Join-Path $psqRoot 'server\index.mjs') *>> $psqLogFile
    $psqExitCode = $LASTEXITCODE
    if ($null -eq $psqExitCode -or $psqExitCode -eq 0) { $psqExitCode = 1 }
    exit $psqExitCode
} catch {
    $_.Exception.Message | Out-File -LiteralPath $psqLogFile -Append
    exit 1
}
'@
}

if (!(Test-PSQWebAdministrator)) { throw 'Run this script in an administrator PowerShell on the server.' }
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
foreach ($relativePath in @('.env', 'server\index.mjs', 'dist\index.html', 'dist\release.json')) {
    $path = Join-Path $InstallRoot $relativePath
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing deployment file: $path" }
}
if (!$NodePath) {
    $standardNode = Join-Path $env:ProgramFiles 'nodejs\node.exe'
    if (Test-Path -LiteralPath $standardNode -PathType Leaf) { $NodePath = $standardNode }
    else {
        $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
        if ($nodeCommand) { $NodePath = $nodeCommand.Source }
    }
}
if (!$NodePath -or !(Test-Path -LiteralPath $NodePath -PathType Leaf)) { throw 'Node.js was not found. Supply its full path with -NodePath.' }
$NodePath = [IO.Path]::GetFullPath($NodePath)
$nodeVersionOutput = & $NodePath --version
if ($LASTEXITCODE -ne 0 -or [version]($nodeVersionOutput.Trim().TrimStart('v')) -lt [version]'22.13.0') {
    throw 'Node.js 22.13.0 or newer is required.'
}
if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
    throw "Scheduled task $TaskName already exists. No task was replaced; review the existing task."
}
if (@(Get-NetTCPConnection -State Listen -LocalPort 4173 -ErrorAction SilentlyContinue).Count) {
    throw 'Port 4173 is still occupied. Press Ctrl+C in the original Node window, then run this installer again.'
}
$release = Get-Content -LiteralPath (Join-Path $InstallRoot 'dist\release.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$deployDirectory = Join-Path $InstallRoot 'deploy'
$logDirectory = Join-Path $InstallRoot 'logs'
$runnerPath = Join-Path $deployDirectory 'Run-PSQWeb.ps1'
$runnerBackup = $null
$registered = $false
try {
    New-Item -ItemType Directory -Path $deployDirectory, $logDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $runnerPath) {
        $runnerBackup = "$runnerPath.$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').bak"
        Copy-Item -LiteralPath $runnerPath -Destination $runnerBackup
    }
    [IO.File]::WriteAllText($runnerPath, (Get-PSQWebRunner), (New-Object Text.UTF8Encoding($false)))
    $powerShellExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" -NodePath "{1}"' -f $runnerPath, $NodePath
    $action = New-ScheduledTaskAction -Execute $powerShellExe -Argument $arguments -WorkingDirectory $InstallRoot
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $settings = New-ScheduledTaskSettingsSet -RestartCount 20 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -MultipleInstances IgnoreNew
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'PSQ PWA startup and automatic restart' | Out-Null
    $registered = $true
    Start-ScheduledTask -TaskName $TaskName

    $deadline = (Get-Date).AddSeconds(30)
    $ready = $false
    do {
        Start-Sleep -Milliseconds 500
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:4173/api/health' -TimeoutSec 3
            $ready = $health.ok -and $health.webBuild -eq $release.webBuild -and (Get-ScheduledTask -TaskName $TaskName).State -eq 'Running'
        } catch { $ready = $false }
    } until ($ready -or (Get-Date) -ge $deadline)
    if (!$ready) { throw "Startup health check failed. Review $logDirectory\psq-web.log." }
    Start-Sleep -Seconds 2
    if ((Get-ScheduledTask -TaskName $TaskName).State -ne 'Running') { throw 'The PSQ Web task exited after startup.' }
    Write-Output "Installed and running: $TaskName"
    Write-Output 'Starts at Windows startup and retries every minute, up to 20 times after an exit.'
    Write-Output "Log: $logDirectory\psq-web.log"
    Write-Output 'Verify public access: https://phiscorequery.top/api/health'
    $health
} catch {
    $failure = $_.Exception.Message
    if ($registered) {
        try {
            Stop-ScheduledTask -TaskName $TaskName
            $stopDeadline = (Get-Date).AddSeconds(15)
            while ((Get-ScheduledTask -TaskName $TaskName).State -eq 'Running') {
                if ((Get-Date) -ge $stopDeadline) { throw 'Timed out stopping the newly installed task.' }
                Start-Sleep -Milliseconds 250
            }
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        } catch { throw "Installation failed: $failure. Task cleanup failed: $($_.Exception.Message). Review task $TaskName." }
    }
    if ($runnerBackup) { Copy-Item -LiteralPath $runnerBackup -Destination $runnerPath -Force }
    throw "Installation failed: $failure. Your .env and .runtime were preserved. You can run node server\index.mjs manually from $InstallRoot."
}
