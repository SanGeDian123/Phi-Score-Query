[CmdletBinding()]
param(
    [string]$InstallRoot = 'C:\Services\PhigrosScore',
    [string]$WebInstallRoot = 'C:\Services\PSQWeb',
    [string]$BackendTaskName = 'PhigrosScore-Backend',
    [string]$CaddyTaskName = 'PhigrosScore-Caddy',
    [string]$WebTaskName = 'PSQ-Web',
    [string]$BackendOrigin = 'http://127.0.0.1:3939',
    [string]$PublicOrigin = 'https://api.plc-liangpi-cup.xyz',
    [string]$WebPublicOrigin = 'https://phiscorequery.top',
    [string]$CaddyExecutable,
    [string]$NodePath,
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
$WebInstallRoot = [IO.Path]::GetFullPath($WebInstallRoot).TrimEnd('\')
function Assert-PlainPath([string]$Path) {
    $part = [IO.Path]::GetFullPath($Path)
    while ($part) {
        if ((Test-Path -LiteralPath $part) -and ((Get-Item -LiteralPath $part -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Reparse point is not allowed: $part" }
        $parent = Split-Path -Parent $part
        if ($parent -eq $part) { break }; $part = $parent
    }
}
function Checked([string]$Root,[string]$Relative) {
    $result = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (!$result.StartsWith($Root + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Target escapes installation.' }
    Assert-PlainPath $result; return $result
}
foreach ($root in @($InstallRoot,$WebInstallRoot)) {
    if ($root.Length -lt 10 -or $root -eq [IO.Path]::GetPathRoot($root).TrimEnd('\') -or $bundleRoot -eq $root -or $bundleRoot.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase) -or $root.StartsWith($bundleRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Extract outside the two existing installations.' }
    Assert-PlainPath $root
}
if ($InstallRoot -eq $WebInstallRoot -or $InstallRoot.StartsWith($WebInstallRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or $WebInstallRoot.StartsWith($InstallRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Backend and web installations must be separate.' }
Assert-PlainPath $bundleRoot
function Read-Json([string]$Path) { [IO.File]::ReadAllText($Path,[Text.Encoding]::UTF8) | ConvertFrom-Json }
function Atomic-Copy([string]$Source,[string]$Target) {
    $temp=$Target+'.upload-'+[Guid]::NewGuid().ToString('N')
    $previous=$Target+'.replace-'+[Guid]::NewGuid().ToString('N')
    try {
        Copy-Item -LiteralPath $Source -Destination $temp
        for($attempt=1;$attempt -le 20;$attempt++){
            try {
                if (Test-Path -LiteralPath $Target -PathType Leaf) { [IO.File]::Replace($temp,$Target,$previous,$true) }
                else { [IO.File]::Move($temp,$Target) }
                break
            } catch { if($attempt -eq 20){throw};Start-Sleep -Milliseconds 250 }
        }
    } finally { foreach($path in @($temp,$previous)){if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force }} }
}
function Public-Json([string]$Uri) {
    $response=Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10 -Headers @{'Cache-Control'='no-cache'}
    $response.RawContentStream.Position=0
    $reader=[IO.StreamReader]::new($response.RawContentStream,[Text.Encoding]::UTF8)
    try { $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
}
$manifest=Read-Json (Join-Path $bundleRoot 'RELEASE_MANIFEST.json')
$release=Read-Json (Join-Path $bundleRoot 'app-update\latest.json')
$webRelease=Read-Json (Join-Path $bundleRoot 'web-update\dist\release.json')
$apkName='Phi_Score_Query_Pre-0.9.8.0-v112.apk'
$apkSource=Join-Path $bundleRoot ('app-update\'+$apkName)
$latestSource=Join-Path $bundleRoot 'app-update\latest.json'
$apkTarget=Checked $InstallRoot ('app-update\'+$apkName)
$latestTarget=Checked $InstallRoot 'app-update\latest.json'
if ($manifest.kind -ne 'published-app-backend-web' -or !$manifest.publishesAppUpdate -or $release.versionCode -ne 112 -or $release.versionName -cne 'Pre-0.9.8.0' -or $release.mandatory -or $release.sha256 -notmatch '^[a-f0-9]{64}$' -or $release.apkUrl -cne ('https://api.plc-liangpi-cup.xyz/app-update/'+$apkName) -or $webRelease.productVersion -cne 'Pre-0.9.8.0' -or $webRelease.webBuild -cne 'Pre-0.9.8.0-web.1+20261025') { throw 'Unexpected package or release metadata.' }
if (!(Test-Path -LiteralPath $apkSource -PathType Leaf) -or (Get-Item -LiteralPath $apkSource).Length -ne $release.sizeBytes) { throw 'APK size differs from the update manifest.' }
if (Test-Path -LiteralPath $apkTarget) { throw 'This release APK is already installed; refusing to overwrite it.' }
if ((Test-Path -LiteralPath $latestTarget) -and (Read-Json $latestTarget).versionCode -ge 112) { throw 'Refusing to replace an equal or newer APP release.' }
$backendArgs=@{InstallRoot=$InstallRoot;BackendTaskName=$BackendTaskName;CaddyTaskName=$CaddyTaskName;BackendOrigin=$BackendOrigin;PublicOrigin=$PublicOrigin}
if($CaddyExecutable){$backendArgs.CaddyExecutable=$CaddyExecutable}
$backendScript=Join-Path $bundleRoot 'backend-update\scripts\Deploy-RksTrend-Pre-0.9.8.0.ps1'
$webScript=Join-Path $bundleRoot 'web-update\deploy\Upgrade-PSQWeb-Windows.ps1'
$webArgs=@{InstallRoot=$WebInstallRoot;PackageRoot=(Join-Path $bundleRoot 'web-update');TaskName=$WebTaskName}
if($NodePath){$webArgs.NodePath=$NodePath}
foreach($taskName in @($BackendTaskName,$CaddyTaskName,$WebTaskName)){Get-ScheduledTask -TaskName $taskName -ErrorAction Stop | Out-Null}
& $backendScript @backendArgs -ValidateOnly
& $webScript @webArgs -ValidateOnly
$port=4173
foreach($line in [IO.File]::ReadAllLines((Join-Path $WebInstallRoot '.env'))){if($line -match '^\s*PSQ_PORT\s*=\s*["'']?(\d+)["'']?\s*$'){$port=[int]$Matches[1]}}
$webManaged=@('dist','server','release.config.json','access.config.json','ROUTES.md','Start-PSQWeb.ps1','Start-PSQWeb.cmd','Start-PSQWeb.sh','README.md','DEPLOY.md','RELEASE.json','LICENSE','NOTICE','deploy\Upgrade-PSQWeb-Windows.ps1','deploy\Install-PSQWeb-Windows.ps1')
$targets=@()
foreach($file in Get-ChildItem -LiteralPath (Join-Path $bundleRoot 'backend-update\backend') -Recurse -File){
    $relative='current\backend\'+$file.FullName.Substring((Join-Path $bundleRoot 'backend-update\backend').Length+1)
    $target=Checked $InstallRoot $relative
    if($file.Extension -eq '.json' -and (Test-Path -LiteralPath $target)){continue}
    $targets+=@{Root=$InstallRoot;Relative=$relative;Group='backend'}
}
$caddyTarget=Checked $InstallRoot 'current\caddy\Caddyfile'
$sourceNames=[regex]::Matches([IO.File]::ReadAllText($caddyTarget),'(?m)^[\t ]*rewrite[\t ]+\*[\t ]+/(?<name>backend-source-[^/\\\s]+\.zip)[\t ]*\r?$')
if($sourceNames.Count -ne 1){throw 'Cannot identify the existing corresponding-source route.'}
foreach($relative in @('current\caddy\Caddyfile',('source\'+$sourceNames[0].Groups['name'].Value),'app-update\latest.json',('app-update\'+$apkName))){$targets+=@{Root=$InstallRoot;Relative=$relative;Group='backend'}}
foreach($relative in $webManaged){$targets+=@{Root=$WebInstallRoot;Relative=$relative;Group='web'}}
foreach($entry in $targets){
    $target=Checked $entry.Root $entry.Relative
    if(Test-Path -LiteralPath $target -PathType Container){foreach($child in Get-ChildItem -LiteralPath $target -Recurse -Force){Assert-PlainPath $child.FullName}}
}
if($ValidateOnly){Write-Output 'VALIDATION OK: APP v112, backend, PWA and website. No files or tasks changed; no update published.';return}
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
if(!([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Run in administrator PowerShell on the server.'}
$backup=Checked $InstallRoot ('backup\release-v112-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$states=@()
foreach($entry in $targets){
    $target=Checked $entry.Root $entry.Relative
    $saved=Checked $backup ($entry.Group+'\'+$entry.Relative)
    $existed=Test-Path -LiteralPath $target
    if($existed){New-Item -ItemType Directory -Path (Split-Path $saved -Parent) -Force | Out-Null;Copy-Item -LiteralPath $target -Destination $saved -Recurse}
    $states+=@{Root=$entry.Root;Relative=$entry.Relative;Target=$target;Saved=$saved;Existed=$existed;Group=$entry.Group}
}
function Stop-ReleaseTasks {
    Stop-ScheduledTask -TaskName $WebTaskName -ErrorAction Stop
    $deadline=(Get-Date).AddSeconds(25)
    while(@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count -or (Get-ScheduledTask -TaskName $WebTaskName).State -eq 'Running'){
        if((Get-Date) -ge $deadline){throw 'Known web task/port did not stop; no unknown process was terminated.'};Start-Sleep -Milliseconds 250
    }
    foreach($pair in @(@($CaddyTaskName,(Checked $InstallRoot 'current\caddy\caddy.exe')),@($BackendTaskName,(Checked $InstallRoot 'current\backend\phi-backend.exe')))){
        Stop-ScheduledTask -TaskName $pair[0] -ErrorAction Stop
        foreach($process in @(Get-CimInstance Win32_Process | Where-Object {$_.ExecutablePath -and $_.ExecutablePath -ieq $pair[1]})){Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop}
    }
}
try {
    & $backendScript @backendArgs
    & $webScript @webArgs
    $publicWeb=Public-Json ($WebPublicOrigin.TrimEnd('/')+'/api/health')
    if(!$publicWeb.ok -or $publicWeb.webBuild -cne $webRelease.webBuild -or $publicWeb.productVersion -cne $release.versionName){throw 'Public PWA version did not match the new build.'}
    New-Item -ItemType Directory -Path (Split-Path $apkTarget -Parent) -Force | Out-Null
    Atomic-Copy $apkSource $apkTarget
    $head=Invoke-WebRequest -Uri ($PublicOrigin.TrimEnd('/')+'/app-update/'+$apkName) -Method Head -UseBasicParsing -TimeoutSec 10
    if($head.StatusCode -ne 200 -or [long]$head.Headers['Content-Length'] -ne [long]$release.sizeBytes){throw 'Public APK is unavailable or truncated.'}
    # Publish only after the backend, website/PWA and the complete APK are available.
    Atomic-Copy $latestSource $latestTarget
    $actual=Public-Json ($PublicOrigin.TrimEnd('/')+'/app-update/latest.json')
    if($actual.versionCode -ne 112 -or $actual.versionName -cne $release.versionName -or $actual.sha256 -cne $release.sha256 -or $actual.apkUrl -cne $release.apkUrl -or [long]$actual.sizeBytes -ne [long]$release.sizeBytes -or $actual.mandatory -or (($actual.changelog -join "`n") -cne ($release.changelog -join "`n"))){throw 'Public APP update manifest differs from this release.'}
    $siteUpdate=Public-Json ($WebPublicOrigin.TrimEnd('/')+'/api/app-update')
    if($siteUpdate.versionCode -ne 112 -or $siteUpdate.versionName -cne $release.versionName){throw 'Website download information did not update.'}
    Write-Output "PUBLISHED OK: Pre-0.9.8.0 / v112 and $($webRelease.webBuild). Backup: $backup"
} catch {
    $failure=$_.Exception.Message
    try {
        Stop-ReleaseTasks
        foreach($state in $states){
            $target=Checked $state.Root $state.Relative
            if(Test-Path -LiteralPath $target){
                $failed=Checked $backup ('failed-new-build\'+$state.Group+'\'+$state.Relative)
                New-Item -ItemType Directory -Path (Split-Path $failed -Parent) -Force | Out-Null
                Move-Item -LiteralPath $target -Destination $failed
            }
            if($state.Existed){New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null;Copy-Item -LiteralPath $state.Saved -Destination $target -Recurse}
        }
        Start-ScheduledTask -TaskName $BackendTaskName
        Start-ScheduledTask -TaskName $CaddyTaskName
        Start-ScheduledTask -TaskName $WebTaskName
    } catch {throw "Release failed: $failure. Rollback needs attention: $($_.Exception.Message). Backup: $backup"}
    throw "Release failed; previous backend, website and APP publication files restored: $failure. Backup: $backup"
}
