[CmdletBinding()]
param([switch]$NoBrowser)

$ErrorActionPreference = 'Stop'
$url = 'http://localhost:3000/'
$browserJob = $null
Push-Location $PSScriptRoot

try {
    # Reuse an existing console instead of starting a second Vinext instance.
    $listeners = @(Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 15
        if ($response.Content -notmatch '<title>PSQ Server Console</title>') {
            throw 'Port 3000 is occupied by another application. Please free it and try again.'
        }
        Write-Host "PSQ Server Console is already running: $url"
        if (-not $NoBrowser) { Start-Process $url }
        exit 0
    }

    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    $nodeVersion = [version]((& $node --version).Trim().TrimStart('v'))
    if ($nodeVersion -lt [version]'22.13.0') {
        throw 'Node.js 22.13.0 or newer is required. Install a current Node.js LTS release first.'
    }

    if (-not (Test-Path (Join-Path $PSScriptRoot 'node_modules\.bin\vinext.cmd'))) {
        Write-Host 'Installing website dependencies...'
        & $npm ci
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Check the output above and your network connection.' }
    }

    if (-not $NoBrowser) {
        # Open only after the first page is ready, rather than using a fixed delay.
        $browserJob = Start-Job -ArgumentList $url -ScriptBlock {
            param($targetUrl)
            $deadline = (Get-Date).AddMinutes(3)
            while ((Get-Date) -lt $deadline) {
                try {
                    $page = Invoke-WebRequest -Uri $targetUrl -UseBasicParsing -TimeoutSec 5
                    if ($page.StatusCode -eq 200 -and $page.Content -match '<title>PSQ Server Console</title>') {
                        Start-Process $targetUrl
                        return
                    }
                } catch { }
                Start-Sleep -Seconds 1
            }
        }
    }

    Write-Host "Starting PSQ Server Console: $url"
    Write-Host 'Keep this window open. Press Ctrl+C to stop the website.'
    & $npm run dev -- --hostname localhost --port 3000
    if ($LASTEXITCODE -ne 0) { throw "Website startup failed (exit code $LASTEXITCODE). See the output above." }
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($null -ne $browserJob) {
        Stop-Job $browserJob -ErrorAction SilentlyContinue
        Remove-Job $browserJob -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}
