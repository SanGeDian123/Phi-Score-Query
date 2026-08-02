[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$current = Join-Path $InstallRoot 'current'
$scriptsRoot = Join-Path $current 'scripts'
$publishSource = Join-Path $bundleRoot 'Publish-AppUpdate.ps1'
$publishTarget = Join-Path $scriptsRoot 'Publish-AppUpdate.ps1'
$apkPath = Join-Path $bundleRoot 'Phi-Score-Query-Pre-0.9.6.1-Fix.apk'
$changelogPath = Join-Path $bundleRoot 'changelog.json'

foreach ($required in @($publishSource, $apkPath, $changelogPath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing deployment file: $required" }
}

New-Item -ItemType Directory -Force -Path $scriptsRoot | Out-Null
Copy-Item -LiteralPath $publishSource -Destination $publishTarget -Force
[string[]] $changelog = Get-Content -LiteralPath $changelogPath -Raw -Encoding UTF8 | ConvertFrom-Json

& $publishTarget `
    -ApkPath $apkPath `
    -VersionCode 14 `
    -VersionName 'Pre-0.9.6.1-Fix' `
    -Changelog $changelog `
    -InstallRoot $InstallRoot

Write-Output 'Pre-0.9.6.1-Fix has been published.'
Write-Output 'Only the APK and latest.json were updated. Next-Phi-Backend and Caddy do not need to restart.'
