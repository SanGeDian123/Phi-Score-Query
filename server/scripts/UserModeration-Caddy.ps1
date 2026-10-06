function Get-UserModerationCaddy([string] $Text, [string] $Snippet) {
    # Change only the moderation block; retain installed resource routes and limits.
    $newline = if ($Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    # Existing suggestion image URLs must be checked again after a restriction changes.
    $media = [regex]::Matches($Text, '(?ms)(?<head>^[\t ]*handle @suggestionMedia \{\r?\n)(?<body>.*?)(?<close>^[\t ]*\})')
    if ($media.Count -ne 1) { throw 'Cannot locate the suggestion media handler in installed Caddyfile.' }
    $match = $media[0]
    $body = [regex]::Replace($match.Groups['body'].Value, '(?m)^[\t ]*header[\t ]+\??Cache-Control[^\r\n]*\r?\n', '')
    $replacement = $match.Groups['head'].Value + "`t`theader Cache-Control ""no-store""" + $newline + $body + $match.Groups['close'].Value
    $Text = $Text.Substring(0, $match.Index) + $replacement + $Text.Substring($match.Index + $match.Length)
    $block = ($Snippet -replace '\r?\n', $newline).TrimEnd([char[]]"`r`n") + $newline
    $pattern = '(?ms)^[\t ]*# BEGIN PSQ USER MODERATION\r?\n.*?^[\t ]*# END PSQ USER MODERATION[\t ]*(?:\r?\n|$)'
    $existing = [regex]::Matches($Text, $pattern)
    if ($existing.Count -gt 1) { throw 'Multiple moderation blocks in installed Caddyfile.' }
    if ($existing.Count -eq 1) {
        $match = $existing[0]
        return $Text.Substring(0, $match.Index) + $block + $Text.Substring($match.Index + $match.Length)
    }
    if ($Text.Contains('@userModerationApi') -or $Text.Contains('# BEGIN PSQ USER MODERATION')) {
        throw 'Installed moderation block is incomplete or unmanaged.'
    }
    $anchors = [regex]::Matches($Text, '(?m)^[\t ]*@adminConsoleApi[\t ]+path[^\r\n]*')
    if ($anchors.Count -ne 1) { throw 'Cannot locate the admin API handler in installed Caddyfile.' }
    return $Text.Insert($anchors[0].Index, $block + $newline)
}
