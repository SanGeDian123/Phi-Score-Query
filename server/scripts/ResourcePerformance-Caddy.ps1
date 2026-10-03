function Get-ResourcePerformanceCaddy([string] $Text) {
    # Patch only the known resource handlers; retain deployment-specific routes and limits.
    if ($Text -notmatch '(?m)^\s*\?Cache-Control "no-store"\s*$') {
        $pattern = '(?m)^(\s*)Cache-Control "no-store"\s*$'
        if ([regex]::Matches($Text, $pattern).Count -ne 1) { throw 'Cannot locate the global cache fallback in installed Caddyfile.' }
        $Text = [regex]::Replace($Text, $pattern, '$1?Cache-Control "no-store"')
    }
    foreach ($handler in @('illustrations', 'suggestionMedia')) {
        $pattern = '(?m)(handle @' + $handler + ' \{\r?\n)[ \t]*header Cache-Control "public, max-age=604800, immutable"\r?\n'
        $Text = [regex]::Replace($Text, $pattern, '$1')
    }
    if ($Text -notmatch '@downloadFiles path') {
        $pattern = '(handle_path /app-update/\* \{\r?\n)'
        if ([regex]::Matches($Text, $pattern).Count -ne 1) { throw 'Cannot locate the app-update file handler.' }
        $insert = '$1' + "`t`t@downloadFiles path *.pez *.apk *.zip`n`t`theader @downloadFiles Cache-Control ""public, max-age=0, must-revalidate""`n"
        $Text = [regex]::Replace($Text, $pattern, $insert)
    }
    return $Text
}
