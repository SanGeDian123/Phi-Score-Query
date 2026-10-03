import csv
import json
import pathlib
import shutil
import tempfile
import zipfile
from datetime import datetime


REPO = pathlib.Path(__file__).resolve().parents[2]
IDS = [
    "AboutTheUniverse.SOTUIMIssionary",
    "Cryogenic.CamelliaftPetraGurin",
    "Implexrough.Silentroommommy",
    "Evanescent.LeaF",
    "EntrancetotheChaos.打打だいずvssiromaru",
    "ExoplanetaryMirage.かめりあ",
]
INFO_FIELDS = ["id", "song", "composer", "illustrator", "EZ", "HD", "IN", "AT"]
DIFFICULTY_FIELDS = ["id", "EZ", "HD", "IN", "AT"]
SOURCE = REPO / "backend-source"
OUT_DIR = REPO / "server" / "dist"


DEPLOY_SCRIPT = r"""[CmdletBinding()]
param(
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [switch] $ValidateOnly
)
$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$manifestPath = Join-Path $packageRoot 'SERVER_ONLY_MANIFEST.json'
$manifest = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$infoDeltaPath = Join-Path $packageRoot 'payload\info\info.delta.csv'
$difficultyDeltaPath = Join-Path $packageRoot 'payload\info\difficulty.delta.csv'
$illustrationSourceRoot = Join-Path $packageRoot 'payload\resources\ill'
$installRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($installRoot.Length -lt 12 -or $installRoot -match '^[A-Za-z]:\\?$') { throw 'InstallRoot path is invalid.' }
$currentRoot = Join-Path $installRoot 'current'
$backendRoot = Join-Path $currentRoot 'backend'
$infoRoot = Join-Path $backendRoot 'info'
$resourceRoot = Join-Path $backendRoot 'resources\ill'
$infoTarget = Join-Path $infoRoot 'info.csv'
$difficultyTarget = Join-Path $infoRoot 'difficulty.csv'
$backendExecutableTarget = Join-Path $backendRoot 'phi-backend.exe'
$catalogUri = 'http://127.0.0.1:3939/api/v2/songs/catalog'
$backendTaskName = 'PhigrosScore-Backend'

function Assert-NormalDirectory([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) { throw "Required directory not found: $Path" }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse-point target refused: $Path" }
}
function Assert-File([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required file not found: $Path" }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse-point file refused: $Path" }
}
function Wait-BackendTaskStopped([int] $Seconds = 30) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop).State -eq 'Running') {
        if ((Get-Date) -ge $deadline) { throw "Timed out waiting for task to stop: $backendTaskName" }
        Start-Sleep -Milliseconds 250
    }
}
function Get-ManagedBackendProcesses {
    $expectedPath = [IO.Path]::GetFullPath($backendExecutableTarget)
    return @(Get-CimInstance -ClassName Win32_Process -Filter "Name = 'phi-backend.exe'" -ErrorAction Stop |
        Where-Object {
            $_.ExecutablePath -and
            [string]::Equals(
                [IO.Path]::GetFullPath([string]$_.ExecutablePath),
                $expectedPath,
                [StringComparison]::OrdinalIgnoreCase
            )
        })
}
function Stop-ManagedBackendProcesses {
    foreach ($process in @(Get-ManagedBackendProcesses)) {
        Stop-Process -Id ([int]$process.ProcessId) -Force -ErrorAction Stop
    }
}
function Get-BackendListenerIds {
    return @(Get-NetTCPConnection -LocalPort 3939 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
}
function Test-ManagedBackendListener {
    $expectedPath = [IO.Path]::GetFullPath($backendExecutableTarget)
    foreach ($processId in @(Get-BackendListenerIds)) {
        $process = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
        if ($process.ExecutablePath -and [string]::Equals(
            [IO.Path]::GetFullPath([string]$process.ExecutablePath),
            $expectedPath,
            [StringComparison]::OrdinalIgnoreCase
        )) { return $true }
    }
    return $false
}
function Wait-BackendPortReleased([int] $Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $listenerIds = @(Get-BackendListenerIds)
        if ($listenerIds.Count -eq 0) { return }
        Stop-ManagedBackendProcesses
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    $remaining = [string]::Join(',', @((Get-BackendListenerIds) | ForEach-Object { [string]$_ }))
    throw "Port 3939 is still in use after stopping the installed backend; listener PID(s): $remaining"
}
function Get-CatalogStatusSummary($Catalog, $ExpectedCount, $ExpectedInfoRows) {
    if ($null -eq $Catalog -or $null -eq $Catalog.items) { return 'API response has no items.' }
    $items = @($Catalog.items)
    $parts = New-Object 'System.Collections.Generic.List[string]'
    $parts.Add("count=$($items.Count)/$ExpectedCount")
    foreach ($expected in $ExpectedInfoRows) {
        $found = @($items | Where-Object { [string]$_.id -ceq [string]$expected.id })
        if ($found.Count -ne 1) {
            $parts.Add("songId=$($expected.id) matches=$($found.Count)")
            continue
        }
        $item = $found[0]
        $mismatches = New-Object 'System.Collections.Generic.List[string]'
        if ([string]$item.name -cne [string]$expected.song) { $mismatches.Add("name='$($item.name)'") }
        if ([string]$item.composer -cne [string]$expected.composer) { $mismatches.Add('composer') }
        if ([string]$item.illustrator -cne [string]$expected.illustrator) { $mismatches.Add('illustrator') }
        $constants = $item.chartConstants
        if ($null -eq $constants) {
            $mismatches.Add('chartConstants missing')
        } else {
            foreach ($check in @(
                @{ Name='EZ'; Actual=$constants.ez; Expected=$expected.EZ },
                @{ Name='HD'; Actual=$constants.hd; Expected=$expected.HD },
                @{ Name='IN'; Actual=$constants.in; Expected=$expected.IN },
                @{ Name='AT'; Actual=$constants.at; Expected=$expected.AT }
            )) {
                if ([string]::IsNullOrWhiteSpace([string]$check.Expected)) {
                    if ($null -ne $check.Actual -and [string]$check.Actual -ne '') { $mismatches.Add("$($check.Name)=$($check.Actual), expected empty") }
                } elseif ($null -eq $check.Actual -or [math]::Abs([double]$check.Actual - [double]$check.Expected) -gt 0.001) {
                    $mismatches.Add("$($check.Name)=$($check.Actual), expected $($check.Expected)")
                }
            }
        }
        if ($mismatches.Count -gt 0) { $parts.Add("songId=$($expected.id): $([string]::Join(', ', $mismatches))") }
    }
    return [string]::Join('; ', $parts.ToArray())
}
function Get-CatalogResponseUtf8 {
    $request = [Net.HttpWebRequest]::Create($catalogUri)
    $request.Method = 'GET'
    $request.Accept = 'application/json'
    $request.Timeout = 5000
    $request.ReadWriteTimeout = 5000
    $request.AutomaticDecompression = [Net.DecompressionMethods]::GZip -bor [Net.DecompressionMethods]::Deflate
    $response = $request.GetResponse()
    try {
        $reader = [IO.StreamReader]::new($response.GetResponseStream(), [Text.Encoding]::UTF8, $true)
        try { $json = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $response.Dispose() }
    return ConvertFrom-Json -InputObject $json
}
function Wait-BackendHealth([int] $Seconds = 60) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
            if (Test-ManagedBackendListener) { return }
        } catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Backend health check failed after restart, or port 3939 is not served by $backendExecutableTarget."
}
function Get-UniqueIds($Rows, [string] $Description) {
    $ids = @($Rows | ForEach-Object { [string] $_.id })
    $unique = @($ids | Sort-Object -Unique)
    if ($unique.Count -ne $ids.Count) { throw "$Description contains duplicate song IDs." }
    return $ids
}
function Get-PngDimension([byte[]] $Bytes, [int] $Offset) {
    return [int] (([int]$Bytes[$Offset] -shl 24) -bor ([int]$Bytes[$Offset + 1] -shl 16) -bor ([int]$Bytes[$Offset + 2] -shl 8) -bor [int]$Bytes[$Offset + 3])
}
function ConvertTo-CsvLine($Values) {
    $escaped = @()
    foreach ($value in $Values) { $escaped += '"' + ([string]$value).Replace('"', '""') + '"' }
    return [string]::Join(',', $escaped)
}
function Add-CsvRows([string] $Path, $Rows, [string[]] $Columns) {
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    $crlf = [string]([char]13) + [char]10
    $lf = [string][char]10
    $cr = [string][char]13
    $newline = if ($text.Contains($crlf)) { $crlf } else { $lf }
    $lines = @()
    foreach ($row in $Rows) {
        $values = @()
        foreach ($column in $Columns) { $values += [string] $row.$column }
        $lines += ConvertTo-CsvLine $values
    }
    $prefix = if ($text.EndsWith($lf) -or $text.EndsWith($cr)) { '' } else { $newline }
    $append = $prefix + [string]::Join($newline, $lines) + $newline
    [IO.File]::AppendAllText($Path, $append, [Text.UTF8Encoding]::new($false))
}
function Test-CatalogResponse($Catalog, $ExpectedCount, $ExpectedInfoRows) {
    if ($null -eq $Catalog -or $null -eq $Catalog.items) { return $false }
    $items = @($Catalog.items)
    if ($items.Count -ne $ExpectedCount) { return $false }
    foreach ($expected in $ExpectedInfoRows) {
        $found = @($items | Where-Object { [string]$_.id -ceq [string]$expected.id })
        if ($found.Count -ne 1) { return $false }
        $item = $found[0]
        if ([string]$item.name -cne [string]$expected.song -or
            [string]$item.composer -cne [string]$expected.composer -or
            [string]$item.illustrator -cne [string]$expected.illustrator) { return $false }
        $constants = $item.chartConstants
        if ($null -eq $constants) { return $false }
        if ([math]::Abs([double]$constants.ez - [double]$expected.EZ) -gt 0.001 -or
            [math]::Abs([double]$constants.hd - [double]$expected.HD) -gt 0.001 -or
            [math]::Abs([double]$constants.in - [double]$expected.IN) -gt 0.001) { return $false }
        if ([string]::IsNullOrWhiteSpace([string]$expected.AT)) {
            if ($null -ne $constants.at -and [string]$constants.at -ne '') { return $false }
        } elseif ([math]::Abs([double]$constants.at - [double]$expected.AT) -gt 0.001) { return $false }
    }
    return $true
}

Assert-File $manifestPath
Assert-File $infoDeltaPath
Assert-File $difficultyDeltaPath
$requiredIds = @($manifest.requiredSongIds)
if ($requiredIds.Count -ne 6 -or [int]$manifest.addedSongCount -ne 6) { throw 'Invalid package song count.' }
$infoDelta = @(Import-Csv -LiteralPath $infoDeltaPath -Encoding UTF8)
$difficultyDelta = @(Import-Csv -LiteralPath $difficultyDeltaPath -Encoding UTF8)
if ($infoDelta.Count -ne 6 -or $difficultyDelta.Count -ne 6) { throw 'Package must contain six rows in each catalog delta.' }
$null = Get-UniqueIds $infoDelta 'info.delta.csv'
$null = Get-UniqueIds $difficultyDelta 'difficulty.delta.csv'
foreach ($id in $requiredIds) {
    if (-not ($infoDelta | Where-Object { [string]$_.id -ceq [string]$id })) { throw "info delta missing required ID: $id" }
    if (-not ($difficultyDelta | Where-Object { [string]$_.id -ceq [string]$id })) { throw "difficulty delta missing required ID: $id" }
}
foreach ($row in $infoDelta) {
    $diff = @($difficultyDelta | Where-Object { [string]$_.id -ceq [string]$row.id })[0]
    foreach ($level in @('EZ','HD','IN','AT')) { if ([string]$row.$level -cne [string]$diff.$level) { throw "Difficulty mismatch for $($row.id): $level" } }
}
$variants = @(
    [pscustomobject]@{ Name='ill'; Width=2048; Height=1080 },
    [pscustomobject]@{ Name='illLow'; Width=512; Height=270 },
    [pscustomobject]@{ Name='illBlur'; Width=256; Height=135 }
)
$imageCount = 0
foreach ($variant in $variants) {
    $sourceDir = Join-Path $illustrationSourceRoot $variant.Name
    Assert-NormalDirectory $sourceDir
    foreach ($id in $requiredIds) {
        $sourceImage = Join-Path $sourceDir ($id + '.png')
        Assert-File $sourceImage
        $bytes = [IO.File]::ReadAllBytes($sourceImage)
        $signature = [byte[]](137,80,78,71,13,10,26,10)
        if ($bytes.Length -lt 24) { throw "Invalid PNG: $sourceImage" }
        for ($i=0; $i -lt 8; $i++) { if ($bytes[$i] -ne $signature[$i]) { throw "Invalid PNG: $sourceImage" } }
        if ([Text.Encoding]::ASCII.GetString($bytes,12,4) -ne 'IHDR' -or
            (Get-PngDimension $bytes 16) -ne $variant.Width -or (Get-PngDimension $bytes 20) -ne $variant.Height) { throw "Unexpected PNG dimensions: $sourceImage" }
        $imageCount++
    }
}
if ($imageCount -ne 18) { throw "Expected 18 illustration files, found $imageCount." }

foreach ($path in @($installRoot,$currentRoot,$backendRoot,$infoRoot,$resourceRoot)) { Assert-NormalDirectory $path }
foreach ($variant in $variants) { Assert-NormalDirectory (Join-Path $resourceRoot $variant.Name) }
Assert-File $infoTarget
Assert-File $difficultyTarget
Assert-File $backendExecutableTarget
$backendTask = Get-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop
if ($backendTask.State -ne 'Running') { throw "Backend scheduled task is not running: $backendTaskName (state=$($backendTask.State))." }
if (-not (Test-ManagedBackendListener)) { throw "Port 3939 is not currently served by the installed backend: $backendExecutableTarget" }
try {
    Invoke-RestMethod 'http://127.0.0.1:3939/health' -TimeoutSec 5 | Out-Null
} catch {
    throw "Backend health check failed before deployment: $($_.Exception.Message)"
}
$liveInfo = @(Import-Csv -LiteralPath $infoTarget -Encoding UTF8)
$liveDifficulty = @(Import-Csv -LiteralPath $difficultyTarget -Encoding UTF8)
$liveInfoIds = @(Get-UniqueIds $liveInfo 'server info.csv')
$liveDifficultyIds = @(Get-UniqueIds $liveDifficulty 'server difficulty.csv')
$sortedInfoIds = @($liveInfoIds | Sort-Object)
$sortedDifficultyIds = @($liveDifficultyIds | Sort-Object)
if ($liveInfo.Count -ne $liveDifficulty.Count -or [string]::Join(([string][char]10),$sortedInfoIds) -cne [string]::Join(([string][char]10),$sortedDifficultyIds)) { throw 'Server info.csv and difficulty.csv IDs do not match; refusing to merge.' }
foreach ($id in $requiredIds) {
    if ($liveInfoIds -contains [string]$id -or $liveDifficultyIds -contains [string]$id) { throw "Song ID already exists on server; refusing duplicate: $id" }
}
foreach ($variant in $variants) {
    $targetDir = Join-Path $resourceRoot $variant.Name
    foreach ($id in $requiredIds) {
        $targetImage = Join-Path $targetDir ($id + '.png')
        if (Test-Path -LiteralPath $targetImage) { throw "Target illustration already exists; refusing overwrite: $targetImage" }
    }
}
$expectedTotal = $liveInfo.Count + 6
if ($ValidateOnly) {
    Write-Output "VALIDATION OK: package=6 songs/18 images; server=$($liveInfo.Count) songs; result=$expectedTotal; no files changed."
    return
}

$backupRoot = Join-Path $installRoot ('backup\psq-catalog-6songs-20260925-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
Copy-Item -LiteralPath $infoTarget -Destination (Join-Path $backupRoot 'info.csv') -Force
Copy-Item -LiteralPath $difficultyTarget -Destination (Join-Path $backupRoot 'difficulty.csv') -Force
$copiedImages = New-Object 'System.Collections.Generic.List[string]'
$backendNeedsRestart = $false
try {
    Stop-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop
    $backendNeedsRestart = $true
    Wait-BackendTaskStopped
    Stop-ManagedBackendProcesses
    Wait-BackendPortReleased
    foreach ($variant in $variants) {
        $sourceDir = Join-Path $illustrationSourceRoot $variant.Name
        $targetDir = Join-Path $resourceRoot $variant.Name
        foreach ($id in $requiredIds) {
            $sourceImage = Join-Path $sourceDir ($id + '.png')
            $targetImage = Join-Path $targetDir ($id + '.png')
            Copy-Item -LiteralPath $sourceImage -Destination $targetImage
            [void]$copiedImages.Add($targetImage)
        }
    }
    Add-CsvRows $difficultyTarget $difficultyDelta @('id','EZ','HD','IN','AT')
    Add-CsvRows $infoTarget $infoDelta @('id','song','composer','illustrator','EZ','HD','IN','AT')
    Start-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop
    Wait-BackendHealth
    $deadline = (Get-Date).AddSeconds(60)
    $verified = $false
    $lastCatalogSummary = 'No catalog response received.'
    do {
        Start-Sleep -Seconds 2
        try {
            $catalog = Get-CatalogResponseUtf8
            $lastCatalogSummary = Get-CatalogStatusSummary $catalog $expectedTotal $infoDelta
            if (Test-CatalogResponse $catalog $expectedTotal $infoDelta) { $verified = $true; break }
        } catch { $lastCatalogSummary = "Catalog request failed: $($_.Exception.Message)" }
    } while ((Get-Date) -lt $deadline)
    if (-not $verified) { throw "The running backend did not expose all six songs and constants within 60 seconds. Last response: $lastCatalogSummary" }
    $backendNeedsRestart = $false
} catch {
    $failure = $_.Exception.Message
    Copy-Item -LiteralPath (Join-Path $backupRoot 'info.csv') -Destination $infoTarget -Force
    Copy-Item -LiteralPath (Join-Path $backupRoot 'difficulty.csv') -Destination $difficultyTarget -Force
    foreach ($path in $copiedImages) { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force } }
    if ($backendNeedsRestart) {
        try {
            if ((Get-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop).State -eq 'Running') {
                Stop-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop
            }
            Wait-BackendTaskStopped
            Stop-ManagedBackendProcesses
            Wait-BackendPortReleased
            Start-ScheduledTask -TaskName $backendTaskName -ErrorAction Stop
            Wait-BackendHealth
        } catch {
            $failure += " Backend restart after rollback failed: $($_.Exception.Message)"
        }
    }
    throw "Deployment failed and catalog files were restored from $backupRoot. Cause: $failure"
}
Write-Output "DEPLOYMENT OK: catalog=$expectedTotal songs, added=6, illustrations=$imageCount; backup=$backupRoot"
"""


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


info_rows = read_csv(SOURCE / "info" / "info.csv")
difficulty_rows = read_csv(SOURCE / "info" / "difficulty.csv")
if len(info_rows) != 320 or len(difficulty_rows) != 320:
    raise SystemExit(f"Unexpected source catalog size: info={len(info_rows)} difficulty={len(difficulty_rows)}")
by_info = {row["id"]: row for row in info_rows}
by_difficulty = {row["id"]: row for row in difficulty_rows}
if not all(song_id in by_info and song_id in by_difficulty for song_id in IDS):
    raise SystemExit("One or more source rows are missing.")

stage = pathlib.Path(tempfile.mkdtemp(prefix="psq-catalog-6songs-20260925-"))
(stage / "payload" / "info").mkdir(parents=True)
for folder in ("ill", "illLow", "illBlur"):
    (stage / "payload" / "resources" / "ill" / folder).mkdir(parents=True)

with (stage / "payload" / "info" / "info.delta.csv").open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=INFO_FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(by_info[song_id] for song_id in IDS)
with (stage / "payload" / "info" / "difficulty.delta.csv").open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=DIFFICULTY_FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(by_difficulty[song_id] for song_id in IDS)

for folder in ("ill", "illLow", "illBlur"):
    for song_id in IDS:
        source = SOURCE / "resources" / "ill" / folder / f"{song_id}.png"
        if not source.is_file():
            raise SystemExit(f"Missing illustration: {source}")
        shutil.copy2(source, stage / "payload" / "resources" / "ill" / folder / source.name)

manifest = {
    "packageType": "PSQ server-only song catalog delta",
    "created": "2026-09-25",
    "sourceAppVersion": "4.0.0",
    "addedSongCount": 6,
    "requiredSongIds": IDS,
    "illustrationFiles": 18,
    "catalogDeltaOnly": True,
    "containsBackendExecutable": False,
    "containsApk": False,
    "containsAppUpdateManifest": False,
    "containsPublishAppUpdateScript": False,
    "temporaryUntilOfficialBackendSync": True,
}
(stage / "SERVER_ONLY_MANIFEST.json").write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
(stage / "Deploy-PSQ-SongCatalog-6Songs-20260925.ps1").write_text(
    DEPLOY_SCRIPT, encoding="utf-8-sig"
)
(stage / "README-DEPLOY.txt").write_text(
    """PSQ 服务端曲库临时增量包

内容：6 首曲目信息、6 组定数、高清/低清/模糊曲绘共 18 张。曲绘从 Phigros 4.0.0 APK 的 Addressables 资源中提取。

本包只合并六首增量，不覆盖服务器完整曲库；不包含后端可执行文件、APK、app-update/latest.json 或 Publish-AppUpdate.ps1。

在服务器桌面解压后，先运行 Deploy-PSQ-SongCatalog-6Songs-20260925.ps1 -InstallRoot C:\\Services\\PhigrosScore -ValidateOnly。检查通过后，去掉 -ValidateOnly 再运行正式部署命令。正式部署会短暂重启 PhigrosScore-Backend，并在必要时只结束位于本安装目录中的残留 phi-backend.exe，确认 3939 端口释放后再启动后端。脚本会备份曲库文件；若接口校验失败，将恢复 CSV、删除本次曲绘并重启后端，同时在报错中给出实际曲目数及字段/定数差异。

插画师字段：4.0.0 APK 的曲目 Addressables 清单提供曲绘资源，但未提供这六首的插画署名字段，因此 info.delta.csv 中 illustrator 留空，未猜填署名。
""",
    encoding="utf-8",
)

OUT_DIR.mkdir(parents=True, exist_ok=True)
output = OUT_DIR / "server-only-catalog-Phigros-4.0.0-6Songs-20260925.zip"
if output.exists():
    suffix = datetime.now().strftime("%H%M%S")
    output = output.with_name(f"{output.stem}-{suffix}{output.suffix}")
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for path in sorted(stage.rglob("*")):
        if path.is_file():
            archive.write(path, path.relative_to(stage).as_posix())

with zipfile.ZipFile(output) as archive:
    names = archive.namelist()
    forbidden = [
        name for name in names
        if name.lower().endswith(".apk")
        or name.lower().endswith("latest.json")
        or name.endswith("Publish-AppUpdate.ps1")
    ]
    if forbidden:
        raise SystemExit(f"Unexpected app update files in package: {forbidden}")
    print(f"PACKAGE={output}")
    print(f"BYTES={output.stat().st_size}")
    print(f"ENTRIES={len(names)}")
    print("\n".join(names))
