[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Title,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Body,
    [string] $Id = (Get-Date -Format 'yyyyMMdd-HHmmss-fff'),
    [string] $PublishedAt = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'),
    [string] $InstallRoot = 'C:\Services\PhigrosScore'
)

$ErrorActionPreference = 'Stop'
$Id = $Id.Trim()
$Title = $Title.Trim()
$Body = $Body.Trim()
if ($Id.Length -eq 0 -or $Id.Length -gt 128) { throw 'Id 长度必须为 1-128 个字符。' }
if ($Title.Length -eq 0 -or $Title.Length -gt 120) { throw 'Title 长度必须为 1-120 个字符。' }
if ($Body.Length -eq 0 -or $Body.Length -gt 8000) { throw 'Body 长度必须为 1-8000 个字符。' }
$publishedAtValue = [DateTimeOffset]::MinValue
if (-not [DateTimeOffset]::TryParse(
    $PublishedAt,
    [Globalization.CultureInfo]::InvariantCulture,
    [Globalization.DateTimeStyles]::AllowWhiteSpaces,
    [ref] $publishedAtValue
)) {
    throw 'PublishedAt 必须是包含日期、时间和时区的有效时间。'
}
# Rust 端按带时区的秒级时间解析；统一规范化输入，避免传入短日期或本地化格式后重启排序失效。
$PublishedAt = $publishedAtValue.ToString(
    'yyyy-MM-dd HH:mm:ss zzz',
    [Globalization.CultureInfo]::InvariantCulture
)

$announcementRoot = Join-Path ([IO.Path]::GetFullPath($InstallRoot)) 'app-announcement'
New-Item -ItemType Directory -Force -Path $announcementRoot | Out-Null

$announcement = [ordered]@{
    id = $Id
    title = $Title
    body = $Body
    publishedAt = $PublishedAt
}

function Read-Utf8Json([string] $Path, [string] $Description) {
    try {
        [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json
    } catch {
        throw "$Description 不是有效 JSON: $Path / $($_.Exception.Message)"
    }
}

function Get-PublishedAtTicks($Item) {
    $parsed = [DateTimeOffset]::MinValue
    if ([DateTimeOffset]::TryParse(
        [string] $Item.publishedAt,
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AllowWhiteSpaces,
        [ref] $parsed
    )) {
        return $parsed.UtcDateTime.Ticks
    }
    return [Int64]::MinValue
}

function Write-Utf8JsonAtomically([string] $Path, $Value) {
    $temporary = "$Path.$([Guid]::NewGuid().ToString('N')).upload"
    try {
        $json = $Value | ConvertTo-Json -Depth 6
        [IO.File]::WriteAllText($temporary, $json, (New-Object Text.UTF8Encoding($false)))
        Move-Item -LiteralPath $temporary -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

$latestPath = Join-Path $announcementRoot 'latest.json'
$indexPath = Join-Path $announcementRoot 'index.json'
$items = @()
$hasAnnouncementIndex = Test-Path -LiteralPath $indexPath
if ($hasAnnouncementIndex) {
    $index = Read-Utf8Json $indexPath '公告历史清单'
    if ($null -eq $index.PSObject.Properties['items']) {
        throw "公告历史清单缺少 items 数组: $indexPath"
    }
    $items = @($index.items)
} elseif (Test-Path -LiteralPath $latestPath) {
    # 首次升级时兼容旧版仅有 latest.json 的目录；index.json 存在后不再读取旧 latest。
    $legacyLatest = Read-Utf8Json $latestPath '最新公告'
    $items += $legacyLatest
}

$ids = @{}
foreach ($item in $items) {
    $existingId = [string] $item.id
    if ([string]::IsNullOrWhiteSpace($existingId)) { throw '公告历史中存在空 ID。' }
    if ($ids.ContainsKey($existingId)) { throw "公告历史中存在重复 ID: $existingId" }
    $ids[$existingId] = $true
}
if ($ids.ContainsKey($Id)) { throw "公告 ID 已存在: $Id" }

$items = @($announcement) + @($items)
$items = @($items | Sort-Object `
    @{ Expression = { Get-PublishedAtTicks $_ }; Descending = $true }, `
    @{ Expression = { [string] $_.id }; Descending = $true })
$index = [ordered]@{ items = @($items) }

# 先更新完整历史，再切换 latest；后端启动时也会自动修复两份文件的一致性。
Write-Utf8JsonAtomically $indexPath $index
Write-Utf8JsonAtomically $latestPath $items[0]

Write-Output "公告 ID: $Id"
Write-Output "最新公告: $latestPath"
Write-Output "公告历史: $indexPath"
Write-Output '公开地址: https://api.plc-liangpi-cup.xyz/app-announcement/latest.json'
Write-Output '历史地址: https://api.plc-liangpi-cup.xyz/app-announcement/index.json'
