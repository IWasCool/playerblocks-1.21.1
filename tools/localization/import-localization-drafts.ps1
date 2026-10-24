<#!
Optional authoring tool. Translates only public mod UI strings; never runs during
builds or gameplay. Review the resulting drafts before shipping them.
#>
param([string[]]$Targets)
$ErrorActionPreference = 'Stop'
$Targets = @($Targets | ForEach-Object { $_ -split ',' })
$inputFile = Join-Path $PSScriptRoot '../../build/localization/input.json'
$draftDir = Join-Path $PSScriptRoot '../../build/localization/drafts'
New-Item -ItemType Directory -Force -Path $draftDir | Out-Null
$source = Get-Content -LiteralPath $inputFile -Raw -Encoding UTF8 | ConvertFrom-Json
$entries = @($source.PSObject.Properties)
$utf8 = [Text.UTF8Encoding]::new($false)
foreach ($target in $Targets) {
    $file = Join-Path $draftDir ($target + '.json')
    if (Test-Path -LiteralPath $file) { continue }
    $result = [ordered]@{}
    try {
        # Small batches avoid URL/request limits and keep numeric argument markers intact.
        for ($offset = 0; $offset -lt $entries.Count; $offset += 21) {
            $batch = @($entries | Select-Object -Skip $offset -First 21)
            $query = ''
            for ($n = 0; $n -lt $batch.Count; $n++) {
                $value = [regex]::Replace($batch[$n].Value, '%(\d+)\$s', '{{${1}}}')
                $query += ('[[{0:D3}]]' -f $n) + "`n" + $value + "`n"
            }
            $uri = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=' + [uri]::EscapeDataString($target) + '&dt=t&q=' + [uri]::EscapeDataString($query)
            $response = Invoke-RestMethod -Uri $uri -TimeoutSec 25
            $translated = ($response[0] | ForEach-Object { $_[0] }) -join ''
            [IO.File]::WriteAllText((Join-Path $draftDir ($target + '-' + $offset + '.txt')), $translated, $utf8)
            $matches = [regex]::Matches($translated, '\[\[?(\d{3})\]\]?\s*([\s\S]*?)(?=\[\[?\d{3}\]\]?|$)')
            if ($matches.Count -ne $batch.Count) { throw 'Translation lost entry markers' }
            foreach ($match in $matches) {
                $entry = $batch[[int]$match.Groups[1].Value]
                $value = [regex]::Replace($match.Groups[2].Value.Trim(), '\{\{?\s*(\d+)\s*\}\}?', '%${1}$s')
                $value = [regex]::Replace($value, '\s+', ' ')
                $expected = @([regex]::Matches($entry.Value, '%\d+\$s') | ForEach-Object Value | Sort-Object)
                $actual = @([regex]::Matches($value, '%\d+\$s') | ForEach-Object Value | Sort-Object)
                if (($expected -join ',') -ne ($actual -join ',')) {
                    # A short isolated request is less likely to rewrite a format argument.
                    $singleUri = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=' + [uri]::EscapeDataString($target) + '&dt=t&q=' + [uri]::EscapeDataString($entry.Value)
                    $single = Invoke-RestMethod -Uri $singleUri -TimeoutSec 25
                    $value = (($single[0] | ForEach-Object { $_[0] }) -join '').Trim()
                    $value = [regex]::Replace($value, '%\s*(\d+)\s*\$\s*s', '%${1}$s')
                    $actual = @([regex]::Matches($value, '%\d+\$s') | ForEach-Object Value | Sort-Object)
                    if (($expected -join ',') -ne ($actual -join ',')) { throw ('Translation lost arguments: ' + $entry.Name) }
                }
                $result[$entry.Name] = $value
            }
        }
        [IO.File]::WriteAllText($file, ($result | ConvertTo-Json -Depth 5) + "`n", $utf8)
        Write-Output ('DRAFT ' + $target)
    } catch {
        Write-Output ('UNAVAILABLE ' + $target + ': ' + $_.Exception.Message)
    }
}
