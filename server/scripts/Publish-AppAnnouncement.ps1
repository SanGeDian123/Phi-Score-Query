[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Title,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Body,
    [string] $Id = (Get-Date -Format 'yyyyMMdd-HHmmss'),
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

$announcementRoot = Join-Path ([IO.Path]::GetFullPath($InstallRoot)) 'app-announcement'
New-Item -ItemType Directory -Force -Path $announcementRoot | Out-Null

$manifest = [ordered]@{
    id = $Id
    title = $Title
    body = $Body
    publishedAt = $PublishedAt
}
$manifestPath = Join-Path $announcementRoot 'latest.json'
$tempManifest = "$manifestPath.upload"
$manifestJson = $manifest | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText($tempManifest, $manifestJson, (New-Object Text.UTF8Encoding($false)))
Move-Item -LiteralPath $tempManifest -Destination $manifestPath -Force

Write-Output "公告 ID: $Id"
Write-Output "公告清单: $manifestPath"
Write-Output '公开地址: https://api.plc-liangpi-cup.xyz/app-announcement/latest.json'
