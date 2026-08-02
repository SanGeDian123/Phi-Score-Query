$ErrorActionPreference = 'Stop'
$current = Split-Path $PSScriptRoot -Parent
$installRoot = Split-Path $current -Parent

Get-Content "$installRoot\secrets.env" | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:APP_LOG_DIR = ($installRoot -replace '\\', '/') + '/logs'
$env:APP_UPDATE_DIR = ($installRoot -replace '\\', '/') + '/app-update'
$env:APP_AVATAR_DIR = ($installRoot -replace '\\', '/') + '/avatar'
Set-Location "$current\caddy"
& "$current\caddy\caddy.exe" run --config "$current\caddy\Caddyfile" --adapter caddyfile *>> "$installRoot\logs\caddy.log"
exit $LASTEXITCODE
