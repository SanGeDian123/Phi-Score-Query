[CmdletBinding()]
param(
    [string]$InstallRoot = 'C:\Services\PhigrosScore',
    [switch]$ValidateOnly,
    [string]$CatalogUri = 'http://127.0.0.1:3939/api/v2/songs/catalog'
)
$ErrorActionPreference = 'Stop'
$songId = 'ExoplanetaryMirage.' + [char]0x304b + [char]0x3081 + [char]0x308a + [char]0x3042
$infoDir = Join-Path $InstallRoot 'current\backend\info'
$patches = @()
foreach ($name in @('info.csv', 'difficulty.csv')) {
    $path = Join-Path $infoDir $name
    $bytes = [IO.File]::ReadAllBytes($path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    $header = ($text -split '\r?\n', 2)[0]
    $idPattern = [regex]::Escape($songId)
    $pattern = '(?m)^(?:"' + $idPattern + '"|' + $idPattern + '),[^\r\n]*'
    $matches = [regex]::Matches($text, $pattern)
    if ($matches.Count -ne 1) { throw "$name must contain exactly one Exoplanetary Mirage row; found $($matches.Count)." }
    $match = $matches[0]
    $row = ($header + "`n" + $match.Value) | ConvertFrom-Csv
    if (-not $row.PSObject.Properties['IN']) { throw "$name has no IN column." }
    $oldValue = [double]::Parse([string]$row.IN, [Globalization.CultureInfo]::InvariantCulture)
    if ([math]::Abs($oldValue - 16.9) -gt 0.001 -and [math]::Abs($oldValue - 16.8) -gt 0.001) {
        throw "$name has unexpected IN=$oldValue; refusing to replace it."
    }
    $row.IN = '16.8'
    $line = @($row | ConvertTo-Csv -NoTypeInformation)[1]
    $updated = $text.Substring(0, $match.Index) + $line + $text.Substring($match.Index + $match.Length)
    $patches += [pscustomobject]@{ Name=$name; Path=$path; Text=$updated; Bom=$hasBom }
}
if ($ValidateOnly) {
    Write-Output 'VALIDATION OK: Exoplanetary Mirage IN -> 16.8; no files changed.'
    return
}
$backup = Join-Path $InstallRoot ('backup\em-in-16.8-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
foreach ($patch in $patches) { Copy-Item -LiteralPath $patch.Path -Destination (Join-Path $backup $patch.Name) }
try {
    foreach ($patch in $patches) {
        $temporary = $patch.Path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
        $replaceBackup = $temporary + '.previous'
        try {
            [IO.File]::WriteAllText($temporary, $patch.Text, [Text.UTF8Encoding]::new($patch.Bom))
            [IO.File]::Replace($temporary, $patch.Path, $replaceBackup)
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
            if (Test-Path -LiteralPath $replaceBackup) { Remove-Item -LiteralPath $replaceBackup -Force }
        }
    }
    $deadline = (Get-Date).AddSeconds(60)
    $last = 'No API response.'
    do {
        try {
            $request = [Net.HttpWebRequest]::Create($CatalogUri)
            $request.Timeout = 5000
            $request.ReadWriteTimeout = 5000
            $response = $request.GetResponse()
            try {
                $reader = [IO.StreamReader]::new($response.GetResponseStream(), [Text.Encoding]::UTF8, $true)
                try { $catalog = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            } finally { $response.Dispose() }
            $items = @($catalog.items | Where-Object { [string]$_.id -ceq $songId })
            $last = "matches=$($items.Count)"
            if ($items.Count -eq 1) {
                $value = $items[0].chartConstants.in
                $last = "IN=$value, expected=16.8"
                if ($null -ne $value -and [math]::Abs([double]$value - 16.8) -lt 0.001) {
                    Write-Output "DEPLOYMENT OK: Exoplanetary Mirage IN=16.8; backup=$backup"
                    return
                }
            }
        } catch { $last = $_.Exception.Message }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Backend did not confirm the correction within 60 seconds. Last response: $last"
} catch {
    $cause = $_.Exception.Message
    foreach ($patch in $patches) { Copy-Item -LiteralPath (Join-Path $backup $patch.Name) -Destination $patch.Path -Force }
    throw "Deployment rolled back. Cause: $cause Backup: $backup"
}
