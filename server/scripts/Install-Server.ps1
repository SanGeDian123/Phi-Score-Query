[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path $PSScriptRoot -Parent
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($InstallRoot.Length -lt 10) { throw 'InstallRoot 路径异常。' }

& "$PSScriptRoot\Test-ServerConnectivity.ps1"
if (-not (Test-Path "$packageRoot\backend\phi-backend.exe")) { throw '离线包中缺少 phi-backend.exe。' }
if (-not (Test-Path "$packageRoot\caddy\caddy.exe")) { throw '离线包中缺少 caddy.exe。' }

$vcRuntime = Join-Path $env:SystemRoot 'System32\VCRUNTIME140.dll'
if (-not (Test-Path -LiteralPath $vcRuntime)) {
    $vcInstaller = Join-Path $packageRoot 'prerequisites\vc_redist.x64.exe'
    if (-not (Test-Path -LiteralPath $vcInstaller)) {
        throw '系统缺少 VCRUNTIME140.dll，且离线包中缺少 vc_redist.x64.exe。'
    }
    $vcProcess = Start-Process -FilePath $vcInstaller -ArgumentList '/install', '/quiet', '/norestart' -Wait -PassThru
    if ($vcProcess.ExitCode -notin @(0, 1638, 3010)) {
        throw "Microsoft Visual C++ x64 运行库安装失败，退出码: $($vcProcess.ExitCode)"
    }
    if (-not (Test-Path -LiteralPath $vcRuntime)) {
        throw 'Microsoft Visual C++ x64 运行库安装完成，但 VCRUNTIME140.dll 尚不可用；请重启服务器后重新运行安装器。'
    }
}

$taskNames = @('PhigrosScore-Backend', 'PhigrosScore-Caddy')
foreach ($taskName in $taskNames) {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Force -Path $InstallRoot, "$InstallRoot\logs", "$InstallRoot\backup", "$InstallRoot\app-update" | Out-Null
$current = Join-Path $InstallRoot 'current'
$backup = $null
if (Test-Path -LiteralPath $current) {
    $backup = Join-Path $InstallRoot ("backup\" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Move-Item -LiteralPath $current -Destination $backup
}
Copy-Item -LiteralPath $packageRoot -Destination $current -Recurse
if ($backup) {
    Get-ChildItem "$backup\backend\resources" -Filter 'usage_stats.db*' -ErrorAction SilentlyContinue |
        Copy-Item -Destination "$current\backend\resources" -Force
    if (Test-Path "$backup\backend\resources\stats") {
        Copy-Item "$backup\backend\resources\stats" "$current\backend\resources\stats" -Recurse -Force
    }
}

$secretsPath = Join-Path $InstallRoot 'secrets.env'
if (-not (Test-Path -LiteralPath $secretsPath)) {
    function New-Secret([int] $bytes = 48) {
        $buffer = New-Object byte[] $bytes
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $rng.GetBytes($buffer)
        } finally {
            $rng.Dispose()
        }
        [Convert]::ToBase64String($buffer)
    }
    @(
        "APP_SESSION_JWT_SECRET=$(New-Secret)",
        "APP_SESSION_AUTH_EMBED_SECRET=$(New-Secret)",
        "APP_SESSION_EXCHANGE_SHARED_SECRET=$(New-Secret)",
        "APP_STATS_USER_HASH_SALT=$(New-Secret 32)"
    ) | Set-Content -LiteralPath $secretsPath -Encoding utf8
    & icacls.exe $secretsPath /inheritance:r /grant:r '*S-1-5-18:(R)' '*S-1-5-32-544:(R)' | Out-Null
}

$powerShell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$settings = New-ScheduledTaskSettingsSet -RestartCount 20 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$trigger = New-ScheduledTaskTrigger -AtStartup

$backendAction = New-ScheduledTaskAction -Execute $powerShell -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$current\scripts\Run-Backend.ps1`""
$caddyAction = New-ScheduledTaskAction -Execute $powerShell -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$current\scripts\Run-Caddy.ps1`""
Register-ScheduledTask -TaskName $taskNames[0] -Action $backendAction -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Register-ScheduledTask -TaskName $taskNames[1] -Action $caddyAction -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null

if (-not (Get-NetFirewallRule -DisplayName 'PhigrosScore HTTPS' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName 'PhigrosScore HTTPS' -Direction Inbound -Protocol TCP -LocalPort 443 -Action Allow | Out-Null
}

Start-ScheduledTask -TaskName $taskNames[0]
$backendReady = $false
$backendDeadline = (Get-Date).AddSeconds(60)
do {
    Start-Sleep -Seconds 2
    try {
        Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
        $backendReady = $true
    } catch {
        if ((Get-Date) -ge $backendDeadline) { throw }
    }
} until ($backendReady)
Start-ScheduledTask -TaskName $taskNames[1]
Write-Output '安装完成。DNS 生效后访问: https://api.plc-liangpi-cup.xyz/health'
