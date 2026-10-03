#Requires -RunAsAdministrator

[CmdletBinding()]
param(
    [string]$InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$currentRoot = Join-Path $InstallRoot 'current'
$caddyRoot = Join-Path $currentRoot 'caddy'
$caddyFile = Join-Path $caddyRoot 'Caddyfile'
$caddyExe = Join-Path $caddyRoot 'caddy.exe'
$secretsFile = Join-Path $InstallRoot 'secrets.env'
$backupFile = Join-Path $caddyRoot ('Caddyfile.before-rks-guess-disable-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$tempFile = Join-Path $caddyRoot 'Caddyfile.rks-guess-disable.upload'

foreach ($requiredPath in @($caddyFile, $caddyExe, $secretsFile)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required server file is missing: $requiredPath"
    }
}

Get-Content -LiteralPath $secretsFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$normalizedInstallRoot = $InstallRoot -replace '\\', '/'
$env:APP_LOG_DIR = "$normalizedInstallRoot/logs"
$env:APP_UPDATE_DIR = "$normalizedInstallRoot/app-update"
$env:APP_ANNOUNCEMENT_DIR = "$normalizedInstallRoot/app-announcement"
$env:APP_AVATAR_DIR = "$normalizedInstallRoot/avatar"
$env:APP_SOURCE_DIR = "$normalizedInstallRoot/source"

$source = [IO.File]::ReadAllText($caddyFile)
$routePattern = '(?ms)^\s*@rksGuessApi\s+path\s+/api/v2/games/rks-guess/match\s+/api/v2/games/rks-guess/\*\s*\r?\n\s*handle\s+@rksGuessApi\s*\{.*?(?=^\s*@allowed\s+path\b)'
$disabledRoute = @'
	@rksGuessApi path /api/v2/games/rks-guess/match /api/v2/games/rks-guess/*
	handle @rksGuessApi {
		header Content-Type "application/problem+json; charset=utf-8"
		respond `{"type":"about:blank","title":"\u6682\u672a\u5f00\u653e","status":503,"detail":"RKS \u731c\u731c\u4e50\u6682\u672a\u5f00\u653e"}` 503
	}

'@

$matches = [regex]::Matches($source, $routePattern)
if ($matches.Count -ne 1) {
    throw "Expected exactly one RKS Guess route block, found $($matches.Count). No files were changed."
}
$updated = [regex]::Replace($source, $routePattern, $disabledRoute, 1)
[IO.File]::WriteAllText($tempFile, $updated, [Text.UTF8Encoding]::new($false))

try {
    Push-Location $caddyRoot
    try {
        & $caddyExe validate --config $tempFile --adapter caddyfile
        if ($LASTEXITCODE -ne 0) {
            throw "Caddy rejected the temporary configuration (exit code $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }

    [IO.File]::Replace($tempFile, $caddyFile, $backupFile)

    Stop-ScheduledTask -TaskName 'PhigrosScore-Caddy' -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    Start-ScheduledTask -TaskName 'PhigrosScore-Caddy'
    Start-Sleep -Seconds 3

    Start-ScheduledTask -TaskName 'PhigrosScore-Backend' -ErrorAction SilentlyContinue
    $localHealth = $null
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $localHealth = Invoke-WebRequest 'http://127.0.0.1:3939/health' -UseBasicParsing -TimeoutSec 5
            if ($localHealth.StatusCode -eq 200) { break }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    if ($null -eq $localHealth -or $localHealth.StatusCode -ne 200) {
        throw 'RKS Guess is blocked at Caddy, but the existing backend did not recover within 45 seconds.'
    }

    $publicHealth = Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/health' -UseBasicParsing -TimeoutSec 15
    if ($publicHealth.StatusCode -ne 200) {
        throw "Public health check returned HTTP $($publicHealth.StatusCode)."
    }

    $gameStatus = $null
    try {
        Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/games/rks-guess/match' `
            -Method Post `
            -ContentType 'application/json' `
            -Body '{"mode":"single"}' `
            -UseBasicParsing `
            -TimeoutSec 15 | Out-Null
        $gameStatus = 200
    } catch {
        if ($_.Exception.Response) {
            $gameStatus = [int]$_.Exception.Response.StatusCode
        } else {
            throw
        }
    }
    if ($gameStatus -ne 503) {
        throw "RKS Guess block verification returned HTTP $gameStatus instead of 503."
    }

    Write-Output 'RECOVERY_OK'
    Write-Output 'Local health: 200'
    Write-Output 'Public health: 200'
    Write-Output 'RKS Guess: 503 unavailable'
    Write-Output "Caddy backup: $backupFile"
} finally {
    if (Test-Path -LiteralPath $tempFile) {
        Remove-Item -LiteralPath $tempFile -Force
    }
}
