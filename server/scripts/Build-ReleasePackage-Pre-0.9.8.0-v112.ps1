[CmdletBinding()]
param(
    [string]$OutputRoot='D:\PhigrosAppBuild',
    [string]$BuiltApk='D:\PhigrosAppBuild\Phi_Score_Query_Pre-0.9.8.0-v112.apk',
    [string]$BackendPackage='D:\PhigrosAppBuild\server-only-upgrade-Pre-0.9.8.0-RksTrend-20261025.zip',
    [string]$WebPackageDirectory='D:\PhigrosAppBuild\PSQ-PWA-Pre-0.9.8.0-web.1-20261025'
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression,System.IO.Compression.FileSystem
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$name='server-upgrade-Pre-0.9.8.0-v112-20261025'
$stage=Join-Path $OutputRoot ($name+'-stage')
$zip=Join-Path $OutputRoot ($name+'.zip')
foreach($path in @($stage,$zip)){if(Test-Path -LiteralPath $path){throw "Delivery already exists: $path"}}
foreach($path in @($BuiltApk,$BackendPackage,(Join-Path $WebPackageDirectory 'dist\release.json'))){if(!(Test-Path -LiteralPath $path -PathType Leaf)){throw "Missing verified input: $path"}}
$web=Get-Content -LiteralPath (Join-Path $WebPackageDirectory 'dist\release.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($web.productVersion -cne 'Pre-0.9.8.0' -or $web.webBuild -cne 'Pre-0.9.8.0-web.1+20261025'){throw 'PWA release mismatch.'}
New-Item -ItemType Directory -Path $stage,(Join-Path $stage 'app-update'),(Join-Path $stage 'scripts'),(Join-Path $stage 'source') -Force | Out-Null
[IO.Compression.ZipFile]::ExtractToDirectory($BackendPackage,(Join-Path $stage 'backend-update'))
Copy-Item -LiteralPath $WebPackageDirectory -Destination (Join-Path $stage 'web-update') -Recurse
$apkName='Phi_Score_Query_Pre-0.9.8.0-v112.apk'
Copy-Item -LiteralPath $BuiltApk -Destination (Join-Path $stage ('app-update\'+$apkName))
# Required by the Android updater protocol; no redundant file checksums are generated.
$hash=(Get-FileHash -LiteralPath $BuiltApk -Algorithm SHA256).Hash.ToLowerInvariant()
$release=[ordered]@{
    versionCode=112;versionName='Pre-0.9.8.0';publishedAt='2026-10-25';
    apkUrl=('https://api.plc-liangpi-cup.xyz/app-update/'+$apkName);sha256=$hash;
    sizeBytes=(Get-Item -LiteralPath $BuiltApk).Length;mandatory=$false;
    changelog=@('“谱面播放与练习”功能新增打击音效调整设置；','支持Best N图片生成，上限200张谱面；','新增“时间回溯”模式，开启后，可查看旧定数体系下的RKS及Best 19。','新增RKS趋势图，在概览页面点击RKS数字即可进入。','优化了使用体验。')
}
$utf8=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $stage 'app-update\latest.json'),($release|ConvertTo-Json -Depth 5),$utf8)
$manifest=[ordered]@{
    package=$name;kind='published-app-backend-web';versionName='Pre-0.9.8.0';versionCode=112;webBuild=$web.webBuild;
    includesApk=$true;publishesAppUpdate=$true;mandatory=$false;createdAt='2026-10-25';
    updateOrder=@('backend and corresponding source','PWA and website','public health/version checks','APK then latest.json','website Android download information');
    preserves=@('databases and private configuration','PWA .env and entire .runtime','catalog, aliases, artwork and charts','announcements','custom template JSON','admin website');deployed=$false
}
[IO.File]::WriteAllText((Join-Path $stage 'RELEASE_MANIFEST.json'),($manifest|ConvertTo-Json -Depth 5),$utf8)
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Deploy-Release-Pre-0.9.8.0-v112.ps1') -Destination (Join-Path $stage 'scripts')
Copy-Item -LiteralPath (Join-Path $repo 'server\RELEASE-Pre-0.9.8.0-v112.md') -Destination (Join-Path $stage 'DEPLOY.md')
foreach($file in @('LICENSE','THIRD_PARTY_NOTICES.md','SOURCE_OFFER.md')){Copy-Item -LiteralPath (Join-Path $repo $file) -Destination $stage}
# The matching Rust source is in backend-update/source; include web source separately.
$sourceRoot=Join-Path $repo 'psq-web'
$archive=[IO.Compression.ZipFile]::Open((Join-Path $stage 'source\psq-web-Pre-0.9.8.0.zip'),[IO.Compression.ZipArchiveMode]::Create)
try{
    $sourceFiles=@()
    foreach($tree in @('src','public','server','scripts','deploy','tests')){$sourceFiles+=Get-ChildItem -LiteralPath (Join-Path $sourceRoot $tree) -Recurse -File -Force}
    $sourceFiles+=Get-ChildItem -LiteralPath $sourceRoot -File -Force | Where-Object {$_.Name -in @('.gitignore','.env.example','README.md','DEPLOY.md','ROUTES.md','LICENSE','NOTICE','index.html','package.json','package-lock.json','release.config.json','access.config.json','tsconfig.json','vite.config.ts','Start-PSQWeb.ps1','Start-PSQWeb.cmd','Start-PSQWeb.sh')}
    foreach($file in $sourceFiles){
        $relative=$file.FullName.Substring($sourceRoot.Length+1).Replace('\','/')
        if($relative -match '(^|/)(node_modules|\.runtime|\.git|\.env|cookie\.key)(/|$)' -or $relative -match '\.(db|log|jks|keystore|apk)$'){throw "Private source file: $relative"}
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Fastest)|Out-Null
    }
}finally{$archive.Dispose()}
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
Copy-Item -LiteralPath $zip -Destination ([Environment]::GetFolderPath('Desktop'))
Write-Output "Package: $zip"
Write-Output "Stage: $stage"
