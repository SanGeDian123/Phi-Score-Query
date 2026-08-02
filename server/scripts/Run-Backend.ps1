$ErrorActionPreference = 'Stop'
$current = Split-Path $PSScriptRoot -Parent
$installRoot = Split-Path $current -Parent

Get-Content "$installRoot\secrets.env" | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:RUST_LOG = 'info'
Set-Location "$current\backend"
& "$current\backend\phi-backend.exe" *>> "$installRoot\logs\backend.log"
exit $LASTEXITCODE
