[CmdletBinding()]
param(
    # Bind only to this computer instead of the local network.
    [switch]$LocalOnly,
    # Do not open the console in the default browser.
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$port = 3000
$bindHost = if ($LocalOnly) { 'localhost' } else { '0.0.0.0' }
$url = "http://localhost:$port/"
$browserJob = $null
Push-Location $PSScriptRoot

function Get-LanAddress {
    # Prefer the address of a real network adapter over virtual switches.
    $addresses = @(
        Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object {
                $_.IPAddress -notlike '127.*' -and
                $_.IPAddress -notlike '169.254.*' -and
                $_.InterfaceAlias -notmatch 'vEthernet|Loopback|WSL|Hyper-V|VMware|VirtualBox|Bluetooth'
            } |
            Select-Object -ExpandProperty IPAddress
    )
    if ($addresses.Count -gt 0) { return $addresses }
    return @(
        Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
            Select-Object -ExpandProperty IPAddress
    )
}

function Show-PhoneHint {
    $addresses = @(Get-LanAddress)
    if ($addresses.Count -eq 0) {
        Write-Host 'No local network address found. Connect the computer to Wi-Fi or a cable to use the phone.' -ForegroundColor Yellow
        return
    }
    if ($LocalOnly) {
        Write-Host 'Local-only mode: the phone cannot reach this console. Restart without -LocalOnly to allow it.' -ForegroundColor Yellow
        return
    }
    Write-Host ''
    Write-Host 'Phone access (same Wi-Fi as this computer):' -ForegroundColor Cyan
    foreach ($address in $addresses) {
        Write-Host "  http://${address}:$port/" -ForegroundColor Cyan
    }
    Write-Host 'The console page also shows a QR code: open the page, then click "手机访问".' -ForegroundColor DarkGray
    Write-Host ''
}

try {
    # Reuse an existing console instead of starting a second Vinext instance.
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 15
        if ($response.Content -notmatch '<title>PSQ Server Console</title>') {
            throw "Port $port is occupied by another application. Please free it and try again."
        }
        Write-Host "PSQ Server Console is already running: $url"
        Show-PhoneHint
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
    Show-PhoneHint
    & $npm run dev -- --hostname $bindHost --port $port
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
