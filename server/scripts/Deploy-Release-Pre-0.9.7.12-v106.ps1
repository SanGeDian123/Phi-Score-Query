[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly,
    [string] $CatalogUri = 'http://127.0.0.1:3939/api/v2/songs/catalog',
    [string] $PublicCatalogUri = 'https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog',
    [string] $PublicLatestUri = 'https://api.plc-liangpi-cup.xyz/app-update/latest.json',
    [ValidateRange(1, 120)] [int] $VerificationTimeoutSeconds = 45
)
$ErrorActionPreference = 'Stop'
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installPath = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
if ($installPath.Length -lt 10 -or $installPath -match '^[A-Za-z]:$') { throw 'Invalid InstallRoot.' }
$backendRoot = Join-Path $installPath 'current\backend'
$infoRoot = Join-Path $backendRoot 'info'
$updateRoot = Join-Path $installPath 'app-update'
$apkName = 'Phi_Score_Query_Pre-0.9.7.12-v106.apk'
$apkSource = Join-Path $bundleRoot ('app-update\' + $apkName)
$latestSource = Join-Path $bundleRoot 'app-update\latest.json'
$latestTarget = Join-Path $updateRoot 'latest.json'
$utf8 = [Text.UTF8Encoding]::new($false)

function Read-Json([string] $Path) {
    return [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
}
function Get-ApkSha256([string] $Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
function Assert-PlainPath([string] $Path) {
    $part = [IO.Path]::GetFullPath($Path)
    while ($part) {
        if ((Test-Path -LiteralPath $part) -and
            (((Get-Item -LiteralPath $part -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Junction or symbolic link is not allowed: $part"
        }
        $parent = Split-Path -Parent $part
        if ($parent -eq $part) { break }; $part = $parent
    }
}
function Copy-Atomically([string] $Source, [string] $Target) {
    $temporary = $Target + '.upload-' + [Guid]::NewGuid().ToString('N')
    $replaceBackup = $Target + '.replace-backup-' + [Guid]::NewGuid().ToString('N')
    try {
        Copy-Item -LiteralPath $Source -Destination $temporary -ErrorAction Stop
        for ($attempt = 1; $attempt -le 20; $attempt++) {
            try {
                if (Test-Path -LiteralPath $Target -PathType Leaf) {
                    [IO.File]::Replace($temporary, $Target, $replaceBackup, $true)
                } else { [IO.File]::Move($temporary, $Target) }
                return
            } catch {
                if ($attempt -eq 20) { throw }; Start-Sleep -Milliseconds 500
            }
        }
    } finally {
        foreach ($path in @($temporary, $replaceBackup)) {
            if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue }
        }
    }
}
function Unique-Ids($Rows, [string] $Label) {
    $ids = @($Rows | ForEach-Object { [string]$_.id })
    if ($ids.Count -eq 0 -or @($ids | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0 -or
        @($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw "$Label contains blank or duplicate IDs." }
    return $ids
}
function Merge-Csv([string] $Path, $Delta) {
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $rows = @($text | ConvertFrom-Csv)
    $ids = @(Unique-Ids $rows $Path)
    $header = ($text -split '\r?\n', 2)[0]
    foreach ($patch in $Delta) {
        $existing = @($rows | Where-Object { [string]$_.id -ceq [string]$patch.id })
        if ($existing.Count -gt 1) { throw "Duplicate target: $($patch.id)" }
        foreach ($column in $patch.PSObject.Properties.Name) {
            if (-not $rows[0].PSObject.Properties[$column]) { throw "Missing CSV column: $column in $Path" }
        }
        # Preserve unrelated rows, extra columns, server-specific corrections and aliases.
        $value = if ($existing.Count) { $existing[0] } else { $rows[0].PSObject.Copy() }
        if (-not $existing.Count) { foreach ($column in $value.PSObject.Properties.Name) { $value.$column = '' } }
        foreach ($column in $patch.PSObject.Properties.Name) { $value.$column = [string]$patch.$column }
        $line = @($value | ConvertTo-Csv -NoTypeInformation)[1]
        if ($existing.Count) {
            $id = [regex]::Escape([string]$patch.id)
            $pattern = '(?m)^(?:"' + $id + '"|' + $id + '),[^\r\n]*'
            $matches = [regex]::Matches($text, $pattern)
            if ($matches.Count -ne 1) { throw "Expected a single-line CSV row: $($patch.id)" }
            $match = $matches[0]
            $text = $text.Substring(0,$match.Index) + $line + $text.Substring($match.Index+$match.Length)
        } else { $text = $text.TrimEnd([char[]]"`r`n") + $newline + $line + $newline }
    }
    return $text
}
function Get-JsonUtf8([string] $Uri) {
    $request = [Net.HttpWebRequest]::Create($Uri)
    $request.Timeout = 5000; $request.ReadWriteTimeout = 5000
    $request.Headers['Cache-Control'] = 'no-cache'
    $request.AutomaticDecompression = [Net.DecompressionMethods]::GZip -bor [Net.DecompressionMethods]::Deflate
    $response = $request.GetResponse()
    try {
        $reader = [IO.StreamReader]::new($response.GetResponseStream(), [Text.Encoding]::UTF8, $true)
        try { return $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $response.Dispose() }
}
function Assert-Catalog($Catalog, [int] $Count, $ExpectedRows) {
    if (@($Catalog.items).Count -ne $Count) { throw "Catalog count=$(@($Catalog.items).Count), expected=$Count" }
    foreach ($expected in $ExpectedRows) {
        $rows = @($Catalog.items | Where-Object { [string]$_.id -ceq [string]$expected.id })
        if ($rows.Count -ne 1) { throw "Catalog song missing: $($expected.id)" }
        $row = $rows[0]
        foreach ($pair in @(@('name','song'),@('composer','composer'),@('illustrator','illustrator'))) {
            if ([string]$row.($pair[0]) -cne ([string]$expected.($pair[1])).Trim()) { throw "Catalog metadata mismatch: $($expected.id) $($pair[0])" }
        }
        foreach ($level in @('EZ','HD','IN','AT')) {
            $actual = $row.chartConstants.($level.ToLowerInvariant())
            $value = [string]$expected.$level
            if ([string]::IsNullOrWhiteSpace($value)) {
                if ($null -ne $actual) { throw "Unexpected chart: $($expected.id) $level" }
            } elseif ($null -eq $actual -or [math]::Abs([double]$actual-[double]$value) -gt .001) {
                throw "Catalog constant mismatch: $($expected.id) $level"
            }
        }
    }
}
function Assert-Release($Value, $Expected) {
    if ([int]$Value.versionCode -ne 106 -or [string]$Value.versionName -cne 'Pre-0.9.7.12' -or
        [string]$Value.apkUrl -cne [string]$Expected.apkUrl -or [string]$Value.sha256 -cne [string]$Expected.sha256 -or
        [int64]$Value.sizeBytes -ne [int64]$Expected.sizeBytes -or $Value.mandatory -ne $false -or
        (@($Value.changelog) -join "`n") -cne (@($Expected.changelog) -join "`n")) { throw 'Update metadata mismatch for version 106.' }
}
function Wait-For([scriptblock] $Check, [string] $Label) {
    $deadline = (Get-Date).AddSeconds($VerificationTimeoutSeconds)
    do {
        try { & $Check; return } catch { $lastError = $_.Exception.Message }
        if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds 1 }
    } while ((Get-Date) -lt $deadline)
    throw "$Label failed: $lastError"
}

foreach ($path in @($installPath,$backendRoot,$infoRoot,$updateRoot,$latestTarget)) { Assert-PlainPath $path }
foreach ($path in @($infoRoot,$updateRoot)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Installed directory missing: $path" }
}
$release = Read-Json $latestSource
$manifest = Read-Json (Join-Path $bundleRoot 'RELEASE_MANIFEST.json')
if ([int]$manifest.versionCode -ne 106 -or $manifest.publishesAppUpdate -ne $true -or
    [string]$manifest.apkFile -cne $apkName -or @($manifest.songIds).Count -ne 7) { throw 'Release package manifest mismatch.' }
if ([string]$release.apkUrl -cne ('https://api.plc-liangpi-cup.xyz/app-update/'+$apkName) -or
    [string]$release.sha256 -notmatch '^[0-9a-f]{64}$' -or (@($release.changelog) -join '') -match '躁域') { throw 'Invalid update manifest.' }
Assert-Release $release $release
if ((Get-Item -LiteralPath $apkSource).Length -ne [int64]$release.sizeBytes -or
    (Get-ApkSha256 $apkSource) -cne [string]$release.sha256) { throw 'APK does not match update manifest.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkArchive = [IO.Compression.ZipFile]::OpenRead($apkSource)
try {
    if (-not $apkArchive.GetEntry('AndroidManifest.xml') -or -not $apkArchive.GetEntry('classes.dex')) { throw 'Invalid APK archive.' }
} finally { $apkArchive.Dispose() }
$oldLatest = if (Test-Path -LiteralPath $latestTarget -PathType Leaf) { Read-Json $latestTarget } else { $null }
if ($oldLatest -and [int]$oldLatest.versionCode -ge 106) { throw "Server already publishes version $($oldLatest.versionCode); downgrade/equal replacement refused." }
$infoDelta = @(Import-Csv -LiteralPath (Join-Path $bundleRoot 'payload\info\info.delta.csv') -Encoding UTF8)
$diffDelta = @(Import-Csv -LiteralPath (Join-Path $bundleRoot 'payload\info\difficulty.delta.csv') -Encoding UTF8)
$deltaIds = @(Unique-Ids $infoDelta 'info delta')
$diffIds = @(Unique-Ids $diffDelta 'difficulty delta')
if ($deltaIds.Count -ne 7 -or ($deltaIds -join '|') -cne ($diffIds -join '|') -or
    ($deltaIds -join '|') -cne (@($manifest.songIds) -join '|')) { throw 'Delta song IDs mismatch.' }
foreach ($row in $infoDelta) {
    $diff = @($diffDelta | Where-Object { [string]$_.id -ceq [string]$row.id })[0]
    foreach ($level in @('EZ','HD','IN','AT')) { if ([string]$row.$level -cne [string]$diff.$level) { throw 'Coupled constants mismatch.' } }
    if ([string]$row.id -match '[/\\]' -or [string]$row.id -match '^\.\.?$') { throw 'Invalid resource ID.' }
}
$infoPath = Join-Path $infoRoot 'info.csv'; $diffPath = Join-Path $infoRoot 'difficulty.csv'
foreach ($path in @($infoPath,$diffPath)) { Assert-PlainPath $path }
$liveInfo = @(Import-Csv -LiteralPath $infoPath -Encoding UTF8)
$liveDiff = @(Import-Csv -LiteralPath $diffPath -Encoding UTF8)
$liveIds = @(Unique-Ids $liveInfo 'server info'); $liveDiffIds = @(Unique-Ids $liveDiff 'server difficulty')
if ((@($liveIds | Sort-Object) -join '|') -cne (@($liveDiffIds | Sort-Object) -join '|')) { throw 'Installed catalog IDs mismatch.' }
$mergedInfoText = Merge-Csv $infoPath $infoDelta
$mergedDiffText = Merge-Csv $diffPath $diffDelta
$expectedRows = @($mergedInfoText | ConvertFrom-Csv)
$expectedCount = $expectedRows.Count
$verifyRows = @($expectedRows | Where-Object { $deltaIds -ccontains [string]$_.id })
# Verify the preserved previous correction too, when present in the runtime catalog.
$verifyRows += @($expectedRows | Where-Object { [string]$_.id -ceq ('ExoplanetaryMirage.'+[char]0x304b+[char]0x3081+[char]0x308a+[char]0x3042) })
$imageStates = @()
foreach ($variant in @('ill','illLow','illBlur')) {
    foreach ($id in $deltaIds) {
        $relative = 'resources\ill\'+$variant+'\'+$id+'.png'
        $source = Join-Path (Join-Path $bundleRoot 'payload') $relative
        $target = Join-Path $backendRoot $relative
        Assert-PlainPath $target
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Package illustration missing: $source" }
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            if (Test-Path -LiteralPath $target) { throw "Illustration target is not a file: $target" }
            $imageStates += @{ Source=$source; Target=$target; Relative=$relative; Text=$null; Existed=$false; Changed=$false }
        }
    }
}
$apkTarget = Join-Path $updateRoot $apkName
foreach ($path in @($apkTarget,$latestTarget)) {
    Assert-PlainPath $path
    if ((Test-Path -LiteralPath $path) -and -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Target is not a file: $path" }
}
if ($ValidateOnly) {
    Write-Output "VALIDATION OK: Pre-0.9.7.12 / 106; songs=7, expected catalog=$expectedCount, missing artwork=$($imageStates.Count); no files changed."
    return
}

$backupRoot = Join-Path $installPath ('backup\release-v106-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))
Assert-PlainPath $backupRoot
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$states = @(
    @{ Source=$null; Target=$diffPath; Relative='info\difficulty.csv'; Text=$mergedDiffText; Existed=$true; Changed=$false },
    @{ Source=$null; Target=$infoPath; Relative='info\info.csv'; Text=$mergedInfoText; Existed=$true; Changed=$false }
) + $imageStates + @(
    @{ Source=$apkSource; Target=$apkTarget; Relative=$apkName; Text=$null; Existed=$false; Changed=$false },
    @{ Source=$latestSource; Target=$latestTarget; Relative='latest.json'; Text=$null; Existed=$false; Changed=$false }
)
foreach ($state in $states) {
    $state.Existed = Test-Path -LiteralPath $state.Target -PathType Leaf
    $state.Saved = Join-Path $backupRoot $state.Relative
    if ($state.Existed) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $state.Saved) -Force | Out-Null
        Copy-Item -LiteralPath $state.Target -Destination $state.Saved
    }
}
try {
    foreach ($state in $states | Select-Object -SkipLast 1) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $state.Target) -Force | Out-Null
        $source = $state.Source
        if ($null -ne $state.Text) {
            $source = Join-Path $backupRoot ('prepared-'+[IO.Path]::GetFileName($state.Target))
            [IO.File]::WriteAllText($source,$state.Text,$utf8)
        }
        Copy-Atomically $source $state.Target; $state.Changed = $true
    }
    Wait-For { Assert-Catalog (Get-JsonUtf8 $CatalogUri) $expectedCount $verifyRows } 'Local catalog verification'
    Wait-For { Assert-Catalog (Get-JsonUtf8 $PublicCatalogUri) $expectedCount $verifyRows } 'Public catalog verification'
    # The update only becomes visible after catalog + complete APK publication.
    $latestState = $states[-1]
    Copy-Atomically $latestState.Source $latestState.Target; $latestState.Changed = $true
    Assert-Release (Read-Json $latestTarget) $release
    Wait-For {
        $separator = if ($PublicLatestUri.Contains('?')) { '&' } else { '?' }
        Assert-Release (Get-JsonUtf8 ($PublicLatestUri+$separator+'release=106&probe='+[Guid]::NewGuid().ToString('N'))) $release
        $probe = [UriBuilder]::new($PublicLatestUri)
        $probe.Path = $probe.Path.Substring(0,$probe.Path.LastIndexOf('/')+1)+$apkName; $probe.Query = 'release=106'
        $head = Invoke-WebRequest -Uri $probe.Uri.AbsoluteUri -Method Head -UseBasicParsing -TimeoutSec 5
        if ([int]$head.StatusCode -ne 200 -or [int64]$head.Headers['Content-Length'] -ne [int64]$release.sizeBytes) { throw 'Public APK size mismatch.' }
    } 'Public update verification'
    Write-Output "PUBLISHED OK: Pre-0.9.7.12 / versionCode 106; mandatory=false; catalog=$expectedCount; backup=$backupRoot"
} catch {
    $failure = $_.Exception.Message
    try {
        for ($index=$states.Count-1; $index -ge 0; $index--) {
            $state=$states[$index]
            if (-not $state.Changed) { continue }
            if ($state.Existed) { Copy-Atomically $state.Saved $state.Target }
            elseif (Test-Path -LiteralPath $state.Target -PathType Leaf) { Remove-Item -LiteralPath $state.Target -Force }
        }
    } catch { throw "Deployment failed: $failure. Restoration failed: $($_.Exception.Message). Backup: $backupRoot" }
    throw "Deployment failed; previous local files restored. Cause: $failure. Backup: $backupRoot"
}
