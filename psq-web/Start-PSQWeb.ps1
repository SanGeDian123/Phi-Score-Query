param([switch]$Local, [int]$Port = 4173)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
try {
    $taskNode = Get-Command node -ErrorAction SilentlyContinue
    if (!$taskNode) {
        $taskBundledNode = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
        if (Test-Path -LiteralPath $taskBundledNode) { $taskNodePath = $taskBundledNode }
        else { throw 'Please install Node.js 22.13.0 or newer.' }
    } else { $taskNodePath = $taskNode.Source }
    $taskVersion = [version]((& $taskNodePath --version).TrimStart('v'))
    if ($taskVersion -lt [version]'22.13.0') { throw 'Node.js 22.13.0 or newer is required.' }
    $taskDist = if ($env:PSQ_BUILD_DIR) { $env:PSQ_BUILD_DIR } else { Join-Path $PSScriptRoot 'dist' }
    if (!(Test-Path -LiteralPath (Join-Path $taskDist 'index.html'))) { throw 'dist/index.html is missing. Use the prebuilt package or run npm run build first.' }
    if ($Local) {
        if ($Port -lt 1 -or $Port -gt 65535) { throw 'Invalid port.' }
        $env:PSQ_LOCAL_HTTP = '1'
        $env:PSQ_HOST = '127.0.0.1'
        $env:PSQ_PORT = [string]$Port
        $env:PSQ_PUBLIC_ORIGIN = "http://127.0.0.1:$Port"
        Write-Host "Open http://127.0.0.1:$Port/ after the service is ready."
    } elseif (!(Test-Path -LiteralPath '.env') -and !$env:PSQ_PUBLIC_ORIGIN) {
        throw 'Create .env from .env.example and set PSQ_PUBLIC_ORIGIN before production startup.'
    }
    & $taskNodePath 'server/index.mjs'
    exit $LASTEXITCODE
} catch { Write-Error $_; exit 1 }
