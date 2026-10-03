$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name = 'server-resource-practice-four-charts-Pre-0.9.7.10-Fix2-v84-20260927'
$stage = Join-Path 'D:\PhigrosAppBuild' $name
$zip = $stage + '.zip'
if ((Test-Path -LiteralPath $stage) -or (Test-Path -LiteralPath $zip)) {
    throw "Package output already exists: $stage"
}

$sourceDirectory = 'C:\Users\Administrator\Desktop\杂项\chart'
$charts = @(
    @{ Source = 'Exoplanetary Mirage_IN_16.9.pez'; FileName = 'ExoplanetaryMirage_IN_16.9.pez' },
    @{ Source = 'About The Universe_IN_14.4.pez'; FileName = 'AboutTheUniverse_IN_14.4.pez' },
    @{ Source = 'Entrance to the Chaos_AT_17.6.pez'; FileName = 'EntrancetotheChaos_AT_17.6.pez' },
    @{ Source = 'Entrance to the Chaos_IN_16.8.pez'; FileName = 'EntrancetotheChaos_IN_16.8.pez' }
)
foreach ($chart in $charts) {
    $source = Join-Path $sourceDirectory $chart.Source
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing source chart: $source" }
}

New-Item -ItemType Directory -Path (Join-Path $stage 'practice-charts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'scripts') -Force | Out-Null
foreach ($chart in $charts) {
    Copy-Item -LiteralPath (Join-Path $sourceDirectory $chart.Source) -Destination (Join-Path $stage "practice-charts\$($chart.FileName)")
}
Copy-Item -LiteralPath (Join-Path $repo 'server\scripts\Deploy-PracticeCharts.ps1') -Destination (Join-Path $stage 'scripts\Deploy-PracticeCharts.ps1')

$manifest = @{
    package = $name
    kind = 'server-static-resource-only'
    appVersionName = 'Pre-0.9.7.10-Fix2'
    appVersionCode = 84
    includesApk = $false
    publishesAppUpdate = $false
    modifiesLatestJson = $false
    payload = @($charts | ForEach-Object { "practice-charts/$($_.FileName)" })
    destination = 'C:/Services/PhigrosScore/app-update/practice-charts/'
}
$utf8 = New-Object Text.UTF8Encoding($true)
[IO.File]::WriteAllText((Join-Path $stage 'SERVER_RESOURCE_MANIFEST.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)

$readme = @"
# 谱面资源服务器升级包

新增 Exoplanetary Mirage IN 16.9、About The Universe IN 14.4、Entrance to the Chaos AT 17.6 和 IN 16.8。

此包仅包含四个谱面文件和资源部署脚本。不会发布 APP 更新，不包含 APK，不修改 app-update/latest.json、后端或 Caddy。

将 ZIP 上传服务器桌面并解压，管理员 PowerShell 执行：

Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "`$env:USERPROFILE\Desktop\$name"
& .\scripts\Deploy-PracticeCharts.ps1 -ValidateOnly

校验通过后执行：

& .\scripts\Deploy-PracticeCharts.ps1

此包只在本地生成，尚未部署。
"@
[IO.File]::WriteAllText((Join-Path $stage 'DEPLOY.md'), $readme, $utf8)

& (Join-Path $stage 'scripts\Deploy-PracticeCharts.ps1') -ValidateOnly
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Fastest, $false)
$archive = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    if ($entries -match '\.apk$|(^|/)latest\.json$|Publish-AppUpdate\.ps1$') { throw 'Package contains forbidden APP update content.' }
    foreach ($required in @($manifest.payload) + @('scripts/Deploy-PracticeCharts.ps1', 'SERVER_RESOURCE_MANIFEST.json', 'DEPLOY.md')) {
        if ($required -notin $entries) { throw "Package is missing $required" }
    }
} finally { $archive.Dispose() }
Write-Output $zip
