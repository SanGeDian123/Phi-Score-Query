$ErrorActionPreference = 'Stop'
$current = Split-Path $PSScriptRoot -Parent
$installRoot = Split-Path $current -Parent

Get-Content "$installRoot\secrets.env" | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:RUST_LOG = 'info'
$env:APP_SUGGESTION_MEDIA_DIR = ($installRoot -replace '\\', '/') + '/suggestion-media'
Set-Location "$current\backend"
& "$current\backend\phi-backend.exe" *>> "$installRoot\logs\backend.log"
exit $LASTEXITCODE
