[CmdletBinding()]
param(
    [string]$InstallRoot = 'C:\Services\PSQWeb',
    [string]$TaskName = 'PSQ-Web',
    [string]$NodePath = '',
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
$entry = Join-Path $InstallRoot 'server\index.mjs'
$runner = Join-Path $InstallRoot 'deploy\Run-PSQWeb.ps1'
foreach ($relative in @('.env', 'server\index.mjs', 'dist\release.json')) {
    if (!(Test-Path -LiteralPath (Join-Path $InstallRoot $relative) -PathType Leaf)) { throw "Missing installation file: $relative. Supply the correct -InstallRoot." }
}
$task = Get-ScheduledTask -TaskName $TaskName -TaskPath '\' -ErrorAction Stop
$actions = @($task.Actions)
if ($actions.Count -ne 1) { throw 'Unexpected task actions. No service was changed.' }
$action = $actions[0]
$workingDirectoryMatches = $action.WorkingDirectory -and [IO.Path]::GetFullPath($action.WorkingDirectory).TrimEnd('\') -eq $InstallRoot
$argumentsMatch = $action.Arguments -match ('(?:^|["\s])' + [regex]::Escape($runner) + '(?=$|["\s])') -or $action.Arguments -match ('(?:^|["\s])' + [regex]::Escape($entry) + '(?=$|["\s])')
if (!$workingDirectoryMatches -or !$argumentsMatch) { throw 'The scheduled task does not match this installation. No service was changed.' }
if ($task.State -eq 'Disabled') { throw 'The task is disabled. No service was changed.' }
if (!$NodePath -and $action.Arguments -match '(?i)-NodePath\s+"([^"]+)"') { $NodePath = $Matches[1] }
if (!$NodePath) { $NodePath = Join-Path $env:ProgramFiles 'nodejs\node.exe' }
if (!(Test-Path -LiteralPath $NodePath -PathType Leaf)) { throw 'Node.js was not found. Supply -NodePath.' }
$nodeVersion = & $NodePath --version
if ($LASTEXITCODE -ne 0 -or [version]($nodeVersion.Trim().TrimStart('v')) -lt [version]'22.13.0') { throw 'Node.js 22.13.0 or newer is required.' }
$release = Get-Content -LiteralPath (Join-Path $InstallRoot 'dist\release.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (!$release.webBuild -or !$release.productVersion) { throw 'Invalid installed release metadata.' }
$port = 4173
foreach ($line in [IO.File]::ReadAllLines((Join-Path $InstallRoot '.env'))) {
    if ($line -match '^\s*PSQ_PORT\s*=\s*["'']?(\d+)["'']?\s*$') { $port = [int]$Matches[1] }
}
if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid PSQ_PORT in the existing .env.' }
$healthUrl = "http://127.0.0.1:$port/api/health"

# Replace only the confirmed response-forwarding function, preserving all other
# installed code, frontend files, .env, .runtime, logs and scheduled task settings.
$replacement = @'
async function copyResponse(upstream, res, cache = 'no-store') {
  // PSQ stream-timeout hotfix 2026-10-11: keep failures within this request.
  if (res.destroyed || res.writableEnded) {
    if (upstream.body) await upstream.body.cancel().catch(() => {});
    return;
  }
  const headers = { 'Content-Type': upstream.headers.get('Content-Type') || 'application/octet-stream', 'Cache-Control': cache };
  for (const name of ['etag', 'last-modified', 'content-range', 'accept-ranges']) if (upstream.headers.has(name)) headers[name] = upstream.headers.get(name);
  res.writeHead(upstream.status, headers);
  if (!upstream.body) return res.end();
  // Consume the Web stream directly, avoiding a Readable adapter whose late
  // abort error could escape the request. pipeline still provides backpressure.
  const reader = upstream.body.getReader();
  const cancelBody = () => { reader.cancel().catch(() => {}); };
  res.once('close', cancelBody);
  try {
    async function* chunks() {
      while (true) {
        const { done, value } = await reader.read();
        if (done) return;
        yield value;
      }
    }
    await pipeline(chunks(), res);
  } finally {
    res.off('close', cancelBody);
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
'@
$original = [IO.File]::ReadAllText($entry)
$newline = if ($original.Contains("`r`n")) { "`r`n" } else { "`n" }
$replacement = ($replacement -replace '\r?\n', $newline).TrimEnd([char[]]"`r`n") + $newline
$functionPattern = [regex]::new('(?ms)^async function copyResponse\(upstream, res, cache = ''no-store''\) \{.*?^\}\r?\n(?=async function release\()')
$found = $functionPattern.Matches($original)
if ($found.Count -ne 1 -or !$original.Contains("import { pipeline } from 'node:stream/promises';")) { throw 'The installed server layout is unsupported. No service was changed.' }
if ($found[0].Value -ne $replacement -and !$found[0].Value.Contains('await pipeline(Readable.fromWeb(upstream.body), res);')) { throw 'The installed forwarding function is unsupported. No service was changed.' }
$candidateText = $functionPattern.Replace($original, [Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement }, 1)
$candidateText = [regex]::Replace($candidateText, '(?m)^([\t ]*if \(!res\.headersSent\).*?;) else res\.end\(\);([\t ]*\r?)$', '$1 else res.destroy();$2')
if ($candidateText -eq $original) { Write-Output 'This stream-timeout hotfix is already installed. No service was restarted.'; return }

$candidate = Join-Path (Split-Path $entry -Parent) ('index.stream-hotfix-' + [guid]::NewGuid().ToString('N') + '.mjs')
$backup = $null
$changed = $false
$stopped = $false
$wasRunning = $task.State -eq 'Running'

function Wait-PSQWebStopped {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-ScheduledTask -TaskName $TaskName -TaskPath '\').State -eq 'Running' -or @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count) {
        if ((Get-Date) -ge $deadline) { throw 'The task or port did not stop. No unknown process was terminated.' }
        Start-Sleep -Milliseconds 250
    }
}
function Wait-PSQWebHealthy {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        try {
            $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
            if ($health.ok -and $health.webBuild -eq $release.webBuild -and (Get-ScheduledTask -TaskName $TaskName -TaskPath '\').State -eq 'Running') { return $health }
        } catch {}
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw 'The restarted PSQ Web service did not pass its local health check.'
}

try {
    [IO.File]::WriteAllText($candidate, $candidateText, [Text.UTF8Encoding]::new($false))
    & $NodePath --check $candidate
    if ($LASTEXITCODE -ne 0) { throw 'Patched server syntax validation failed. No service was changed.' }
    Write-Output "Installed build preserved: $($release.webBuild)"
    if ($ValidateOnly) { Write-Output 'Validation passed. No installed file or task was changed.'; return }
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this repair in an administrator PowerShell on the server.' }
    $backupDirectory = Join-Path $InstallRoot ('.backups\stream-timeout-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $backup = Join-Path $backupDirectory 'index.mjs'
    Copy-Item -LiteralPath $entry -Destination $backup
    Stop-ScheduledTask -TaskName $TaskName -TaskPath '\'
    $stopped = $true
    Wait-PSQWebStopped
    $changed = $true
    Copy-Item -LiteralPath $candidate -Destination $entry -Force
    Start-ScheduledTask -TaskName $TaskName -TaskPath '\'
    $health = Wait-PSQWebHealthy
    Write-Output 'Stream-timeout hotfix installed; local health passed.'
    Write-Output "Original server file backup: $backup"
    $health | Select-Object ok, productVersion, webBuild, apiVersion, builtAt
    Write-Output 'Verify public access: https://phiscorequery.top/api/health and https://phiscorequery.top/app/'
} catch {
    $failure = $_.Exception.Message
    if ($changed) {
        Stop-ScheduledTask -TaskName $TaskName -TaskPath '\'
        Wait-PSQWebStopped
        Copy-Item -LiteralPath $backup -Destination $entry -Force
        if ($wasRunning) { Start-ScheduledTask -TaskName $TaskName -TaskPath '\'; $null = Wait-PSQWebHealthy }
        throw "Repair failed: $failure. Original server file restored; backup: $backup"
    }
    if ($stopped -and $wasRunning) { Start-ScheduledTask -TaskName $TaskName -TaskPath '\' }
    throw
} finally {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { Remove-Item -LiteralPath $candidate -Force }
}
