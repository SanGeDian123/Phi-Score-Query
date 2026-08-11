[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $ApkPath,
    [Parameter(Mandatory)] [ValidateRange(1, 2147483647)] [int] $VersionCode,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $VersionName,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string[]] $Changelog,
    [string] $PublishedAt = (Get-Date -Format 'yyyy-MM-dd'),
    [bool] $Mandatory = $false,
    [string] $InstallRoot = 'C:\Services\PhigrosScore',
    [string] $PublicBaseUrl = 'https://api.plc-liangpi-cup.xyz/app-update'
)

$ErrorActionPreference = 'Stop'

function Publish-FileAtomically([string] $TemporaryPath, [string] $DestinationPath) {
    if (Test-Path -LiteralPath $DestinationPath) {
        $destination = Get-Item -LiteralPath $DestinationPath -Force
        if ($destination.PSIsContainer) {
            throw "发布目标必须是文件，但当前是目录：$DestinationPath"
        }
        $replaceBackup = "$DestinationPath.replace-backup-$([Guid]::NewGuid().ToString('N'))"
        try {
            [IO.File]::Replace($TemporaryPath, $DestinationPath, $replaceBackup, $true)
        } finally {
            if (Test-Path -LiteralPath $replaceBackup) {
                Remove-Item -LiteralPath $replaceBackup -Force
            }
        }
    } else {
        [IO.File]::Move($TemporaryPath, $DestinationPath)
    }
}

$sourceApk = (Resolve-Path -LiteralPath $ApkPath).Path
if ([IO.Path]::GetExtension($sourceApk) -ne '.apk') { throw 'ApkPath 必须指向 APK 文件。' }

$updateRoot = Join-Path ([IO.Path]::GetFullPath($InstallRoot)) 'app-update'
New-Item -ItemType Directory -Force -Path $updateRoot | Out-Null

$safeVersion = $VersionName -replace '[^A-Za-z0-9._-]', '_'
$fileName = "Phi-Score-Query-$safeVersion.apk"
$publishedApk = Join-Path $updateRoot $fileName
$tempApk = "$publishedApk.upload"
Copy-Item -LiteralPath $sourceApk -Destination $tempApk -Force
Publish-FileAtomically $tempApk $publishedApk

$hash = (Get-FileHash -LiteralPath $publishedApk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $publishedApk).Length
$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    publishedAt = $PublishedAt
    apkUrl = "$($PublicBaseUrl.TrimEnd('/'))/$fileName"
    sha256 = $hash
    sizeBytes = $size
    mandatory = $Mandatory
    changelog = @($Changelog)
}

$manifestPath = Join-Path $updateRoot 'latest.json'
$tempManifest = "$manifestPath.upload"
$manifestJson = $manifest | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText($tempManifest, $manifestJson, (New-Object Text.UTF8Encoding($false)))
Publish-FileAtomically $tempManifest $manifestPath

Write-Output "更新清单: $manifestPath"
Write-Output "安装包: $publishedApk"
Write-Output "SHA256: $hash"
Write-Output "公开地址: $($manifest.apkUrl)"
