param(
    [string]$InstallRoot = 'C:\Services\PhigrosScore',
    [string]$CaddyTaskName = 'PhigrosScore-Caddy',
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'

function Get-PSQWebCaddyText([string]$Text) {
    $newline = if ($Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $block = @(
        'phiscorequery.top {',
        '    encode zstd gzip',
        '    reverse_proxy 127.0.0.1:4173',
        '}'
    ) -join $newline
    $references = [regex]::Matches($Text, '(?mi)^[\t ]*[^#\r\n]*phiscorequery\.top')
    if ($references.Count -gt 0) {
        if ($references.Count -eq 1 -and $Text.Contains($block)) { return $Text }
        throw 'phiscorequery.top is already referenced in Caddyfile. Review that site instead of adding a duplicate.'
    }
    return $Text.TrimEnd([char[]]"`r`n") + $newline + $newline + $block + $newline
}

function Wait-PSQCaddyStopped {
    $deadline = (Get-Date).AddSeconds(15)
    while ((Get-ScheduledTask -TaskName $CaddyTaskName).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw 'Timed out stopping the Caddy scheduled task.' }
        Start-Sleep -Milliseconds 250
    }
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script in an administrator PowerShell on the server.'
}
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$caddyDirectory = Join-Path $InstallRoot 'current\caddy'
$caddyFile = Join-Path $caddyDirectory 'Caddyfile'
$caddyExe = Join-Path $caddyDirectory 'caddy.exe'
$caddyRunner = Join-Path $InstallRoot 'current\scripts\Run-Caddy.ps1'
$secretsFile = Join-Path $InstallRoot 'secrets.env'
foreach ($path in @($caddyFile, $caddyExe, $caddyRunner, $secretsFile)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing server file: $path" }
}
$task = Get-ScheduledTask -TaskName $CaddyTaskName
if (!@($task.Actions | Where-Object { $_.Arguments -like "*$caddyRunner*" }).Count) {
    throw "The task $CaddyTaskName does not use $caddyRunner. No configuration was changed."
}
$health = Invoke-RestMethod 'http://127.0.0.1:4173/api/health' -TimeoutSec 10
if (!$health.ok -or $health.productVersion -ne (Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\release.config.json') -Raw -Encoding UTF8 | ConvertFrom-Json).productVersion) {
    throw 'The expected PSQ Web service is not healthy on 127.0.0.1:4173.'
}

$candidate = Join-Path $caddyDirectory ('Caddyfile.psq-web-' + [guid]::NewGuid().ToString('N') + '.candidate')
$originalEnvironment = @{}
$backup = $null
$installed = $false
$locationPushed = $false
try {
    $text = [IO.File]::ReadAllText($caddyFile)
    [IO.File]::WriteAllText($candidate, (Get-PSQWebCaddyText $text), (New-Object Text.UTF8Encoding($false)))

    # Match Run-Caddy.ps1 without displaying secrets or retaining them in the shell.
    $caddyEnvironment = @{}
    Get-Content -LiteralPath $secretsFile | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { $caddyEnvironment[$Matches[1]] = $Matches[2] }
    }
    $rootForward = $InstallRoot -replace '\\', '/'
    $caddyEnvironment['APP_LOG_DIR'] = "$rootForward/logs"
    $caddyEnvironment['APP_UPDATE_DIR'] = "$rootForward/app-update"
    $caddyEnvironment['APP_ANNOUNCEMENT_DIR'] = "$rootForward/app-announcement"
    $caddyEnvironment['APP_AVATAR_DIR'] = "$rootForward/avatar"
    $caddyEnvironment['APP_SOURCE_DIR'] = "$rootForward/source"
    foreach ($name in $caddyEnvironment.Keys) {
        $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $caddyEnvironment[$name], 'Process')
    }
    Push-Location -LiteralPath $caddyDirectory
    $locationPushed = $true
    $savedErrorPreference = $ErrorActionPreference
    try {
        # Caddy writes ordinary informational messages to stderr on PowerShell 5.1.
        $ErrorActionPreference = 'Continue'
        & $caddyExe validate --config $candidate --adapter caddyfile
        $validationExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedErrorPreference }
    if ($validationExit -ne 0) { throw 'Caddy validation failed. The installed configuration was not changed.' }
    if ($ValidateOnly) {
        Write-Output 'Validation passed. No installed configuration, task or firewall rule was changed.'
        return
    }

    foreach ($port in @(80, 443)) {
        $ruleName = "PSQWeb-TCP-$port"
        if (!(Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)) {
            New-NetFirewallRule -Name $ruleName -DisplayName "PSQ Web TCP $port" -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow | Out-Null
        }
    }
    $backup = "$caddyFile.before-psq-web-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').bak"
    Copy-Item -LiteralPath $caddyFile -Destination $backup
    $installed = $true
    Copy-Item -LiteralPath $candidate -Destination $caddyFile -Force
    Stop-ScheduledTask -TaskName $CaddyTaskName
    Wait-PSQCaddyStopped
    Start-ScheduledTask -TaskName $CaddyTaskName

    $deadline = (Get-Date).AddSeconds(20)
    do {
        Start-Sleep -Milliseconds 500
        $caddyPids = @((Get-Process -Name caddy -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $caddyExe }).Id)
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 443 -ErrorAction SilentlyContinue | Where-Object { $caddyPids -contains $_.OwningProcess })
        $ready = (Get-ScheduledTask -TaskName $CaddyTaskName).State -eq 'Running' -and $listeners.Count -gt 0
    } until ($ready -or (Get-Date) -ge $deadline)
    if (!$ready) { throw 'Caddy did not start listening on TCP 443.' }
    Start-Sleep -Seconds 3
    if ((Get-ScheduledTask -TaskName $CaddyTaskName).State -ne 'Running') { throw 'Caddy exited after startup.' }
    Write-Output "Caddy is running. Backup: $backup"
    Write-Output 'Allow inbound TCP 80 and 443 in your hosting provider firewall too, if one is enabled.'
    Write-Output 'Certificate issuance can take a little time. Verify: https://phiscorequery.top/api/health'
} catch {
    $failure = $_.Exception.Message
    if ($installed) {
        try {
            Copy-Item -LiteralPath $backup -Destination $caddyFile -Force
            Stop-ScheduledTask -TaskName $CaddyTaskName
            Wait-PSQCaddyStopped
            Start-ScheduledTask -TaskName $CaddyTaskName
        } catch {
            throw "Deployment failed: $failure. Recovery also failed: $($_.Exception.Message). Backup: $backup"
        }
        throw "Deployment failed; the original Caddyfile was restored and its task restarted: $failure. Backup: $backup"
    }
    throw
} finally {
    if ($locationPushed) { Pop-Location }
    foreach ($name in $originalEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], 'Process')
    }
    if (Test-Path -LiteralPath $candidate) { Remove-Item -LiteralPath $candidate -Force }
}
