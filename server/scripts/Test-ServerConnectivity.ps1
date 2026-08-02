[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$targets = @(
    'www.taptap.com',
    'www.taptap.cn',
    'open.tapapis.cn',
    'rak3ffdi.cloud.tds1.tapapis.cn',
    'acme-v02.api.letsencrypt.org'
)

$results = foreach ($hostName in $targets) {
    $dns = try { [bool](Resolve-DnsName $hostName -ErrorAction Stop) } catch { $false }
    $https = Test-NetConnection $hostName -Port 443 -InformationLevel Quiet -WarningAction SilentlyContinue
    [pscustomobject]@{ Host = $hostName; DNS = $dns; HTTPS = $https }
}

$results | Format-Table -AutoSize
if ($results.Where({ -not $_.DNS -or -not $_.HTTPS }).Count -gt 0) {
    throw '必要的外部 HTTPS 连接不完整，请先修复网络或 DNS。'
}

$apiDns = Resolve-DnsName 'api.plc-liangpi-cup.xyz' -Type A -ErrorAction SilentlyContinue
if (-not $apiDns) {
    Write-Warning 'api.plc-liangpi-cup.xyz 尚无 A 记录。请在域名控制台新增 api -> 服务器公网 IPv4。'
} else {
    $apiDns | Select-Object Name, IPAddress | Format-Table -AutoSize
}
