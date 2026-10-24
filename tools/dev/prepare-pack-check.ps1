param(
    [Parameter(Mandatory = $true)]
    [string]$ClientInstance,
    [Parameter(Mandatory = $true)]
    [string]$ServerInstance,
    [Parameter(Mandatory = $true)]
    [string]$GameplaySelection,
    [Parameter(Mandatory = $true)]
    [string]$NativeLibrary,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string]$NativeSha256,
    [string]$OutputRoot = 'build/pack-check'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$buildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputRoot))
if (-not $fixtureRoot.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Fixture output must be a new directory below this project build directory.'
}
if (Test-Path -LiteralPath $fixtureRoot) {
    throw ('Refusing to mix fixture snapshots in an existing output directory: ' + $fixtureRoot)
}

$clientRoot = (Resolve-Path -LiteralPath $ClientInstance).ProviderPath
$serverRoot = (Resolve-Path -LiteralPath $ServerInstance).ProviderPath
$selectionPath = (Resolve-Path -LiteralPath $GameplaySelection).ProviderPath
$selection = Get-Content -LiteralPath $selectionPath -Raw | ConvertFrom-Json
$clientGameplayNames = @($selection.copied_jars | ForEach-Object { [string]$_.jar })
if ($clientGameplayNames.Count -eq 0 -or @($clientGameplayNames | Select-Object -Unique).Count -ne $clientGameplayNames.Count) {
    throw 'The gameplay selection must contain a nonempty set of unique installed-client JAR names.'
}

$nativePath = (Resolve-Path -LiteralPath $NativeLibrary).ProviderPath
$nativeExpected = $NativeSha256.ToUpperInvariant()
if ((Get-FileHash -LiteralPath $nativePath -Algorithm SHA256).Hash -ne $nativeExpected) {
    throw 'The disposable Power Grid DLL source does not match its verified upstream checksum.'
}
$utf8 = [Text.UTF8Encoding]::new($false)
$hashCache = @{}
$metadataCache = @{}

function Write-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20), $utf8)
}

function Get-CachedHash([string]$Path) {
    if (-not $hashCache.ContainsKey($Path)) {
        $hashCache[$Path] = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
    return $hashCache[$Path]
}

function Test-PlayerBlocksJar([IO.FileInfo]$File) {
    if ($metadataCache.ContainsKey($File.FullName)) { return $metadataCache[$File.FullName] }
    $isPlayerBlocks = $File.Name -match '^player[_-]?blocks(?:[-.]|$)'
    if (-not $isPlayerBlocks) {
        $zip = [IO.Compression.ZipFile]::OpenRead($File.FullName)
        try {
            foreach ($metadataName in @('META-INF/neoforge.mods.toml', 'META-INF/mods.toml')) {
                $entry = $zip.GetEntry($metadataName)
                if ($null -eq $entry) { continue }
                $reader = [IO.StreamReader]::new($entry.Open())
                try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
                if ($metadata -match '(?m)^\s*modId\s*=\s*["'']player_blocks["'']') {
                    $isPlayerBlocks = $true
                    break
                }
            }
        } finally { $zip.Dispose() }
    }
    $metadataCache[$File.FullName] = $isPlayerBlocks
    return $isPlayerBlocks
}

function Get-EntryHash($Entry) {
    $stream = $Entry.Open()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $sha.Dispose(); $stream.Dispose() }
}

function Copy-CentralKitchenFixture([string]$Source, [string]$Target) {
    $inputZip = [IO.Compression.ZipFile]::OpenRead($Source)
    $removed = @()
    $retained = @()
    try {
        $outputStream = [IO.File]::Open($Target, [IO.FileMode]::CreateNew)
        $outputZip = [IO.Compression.ZipArchive]::new($outputStream, [IO.Compression.ZipArchiveMode]::Create, $false)
        try {
            foreach ($entry in $inputZip.Entries) {
                if ($entry.FullName.Contains('GameTests') -or $entry.FullName.Contains('/gametest/')) {
                    if (-not ($entry.FullName.EndsWith('/') -or $entry.FullName.EndsWith('.class'))) {
                        throw ('Unexpected non-class Central Kitchen test entry: ' + $entry.FullName)
                    }
                    $removed += $entry.FullName
                    continue
                }
                $targetEntry = $outputZip.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
                $targetEntry.LastWriteTime = $entry.LastWriteTime
                if (-not $entry.FullName.EndsWith('/')) {
                    $sourceStream = $entry.Open()
                    $targetStream = $targetEntry.Open()
                    try { $sourceStream.CopyTo($targetStream) }
                    finally { $sourceStream.Dispose(); $targetStream.Dispose() }
                }
            }
        } finally { $outputZip.Dispose(); $outputStream.Dispose() }

        $checkZip = [IO.Compression.ZipFile]::OpenRead($Target)
        try {
            if ($checkZip.Entries.Count -ne $inputZip.Entries.Count - $removed.Count) {
                throw 'Central Kitchen fixture ZIP entry count changed unexpectedly.'
            }
            foreach ($entry in $inputZip.Entries) {
                if ($entry.FullName -in $removed) { continue }
                $copied = $checkZip.GetEntry($entry.FullName)
                if ($null -eq $copied -or $copied.Length -ne $entry.Length) {
                    throw ('Missing or resized retained Central Kitchen entry: ' + $entry.FullName)
                }
                $sourceHash = Get-EntryHash $entry
                $targetHash = Get-EntryHash $copied
                if ($sourceHash -ne $targetHash) { throw ('Changed retained Central Kitchen entry: ' + $entry.FullName) }
                $retained += [ordered]@{ entry = $entry.FullName; bytes = $entry.Length; source_sha256 = $sourceHash; fixture_sha256 = $targetHash }
            }
        } finally { $checkZip.Dispose() }
    } finally { $inputZip.Dispose() }
    if ($removed.Count -eq 0) { throw 'The expected Central Kitchen bundled test entries were not found.' }
    return [ordered]@{
        reason = 'Only bundled optional-integration GameTest classes are removed; NeoForge reflects absent optional API signatures before namespace filtering.'
        removed_test_only_entries = $removed
        retained_entry_count = $retained.Count
        all_retained_entry_bytes_match = $true
        retained_entries = $retained
    }
}

function Copy-RootConfigs([string]$SourceRoot, [string]$RunRoot) {
    $rows = @()
    foreach ($folder in @('config', 'defaultconfigs')) {
        $source = Join-Path $SourceRoot $folder
        if (-not (Test-Path -LiteralPath $source)) { continue }
        $folderItem = Get-Item -LiteralPath $source
        if ($folderItem.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw ('Refusing linked config directory: ' + $source) }
        $contents = @(Get-ChildItem -LiteralPath $source -Recurse -Force)
        if (@($contents | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -gt 0) {
            throw ('Refusing linked config content below: ' + $source)
        }
        Copy-Item -LiteralPath $source -Destination (Join-Path $RunRoot $folder) -Recurse
        foreach ($file in $contents | Where-Object { -not $_.PSIsContainer }) {
            $relative = $folder + '/' + $file.FullName.Substring($source.Length + 1).Replace('\', '/')
            $target = Join-Path $RunRoot $relative
            $sourceHash = Get-CachedHash $file.FullName
            $fixtureHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
            if ($sourceHash -ne $fixtureHash) { throw ('Config copy mismatch: ' + $relative) }
            $rows += [ordered]@{ file = $relative; source_sha256 = $sourceHash; copied_sha256 = $fixtureHash; copied_exactly = $true; fixture_override = $(if ($relative -eq 'config/fml.toml') { 'maxThreads = 1 (disposable runtime only)' } else { $null }) }
        }
    }
    $fmlPath = Join-Path $RunRoot 'config/fml.toml'
    [void][IO.Directory]::CreateDirectory((Split-Path $fmlPath -Parent))
    [IO.File]::WriteAllText($fmlPath, "maxThreads = 1`n", $utf8)
    return $rows
}

[void][IO.Directory]::CreateDirectory($fixtureRoot)
$rootManifest = [ordered]@{
    generated_at = (Get-Date -Format o)
    fixture_root = $fixtureRoot
    gameplay_selection = $selectionPath
    gameplay_selection_sha256 = Get-CachedHash $selectionPath
    installed_files_modified = $false
    original_saves_used_or_copied = $false
    copied_folders = @('config', 'defaultconfigs')
    native_library_source = $nativePath
    native_library_sha256 = $nativeExpected
    fixtures = @()
}
Write-Json (Join-Path $fixtureRoot 'manifest.json') $rootManifest

foreach ($spec in @(
    [ordered]@{ name = 'server'; source = $serverRoot; curated = $false },
    [ordered]@{ name = 'client-gameplay'; source = $clientRoot; curated = $true },
    [ordered]@{ name = 'client'; source = $clientRoot; curated = $false }
)) {
    $targetRoot = Join-Path $fixtureRoot $spec.name
    $modsRoot = Join-Path $targetRoot 'mods'
    $runRoot = Join-Path $targetRoot 'run'
    [void][IO.Directory]::CreateDirectory($modsRoot)
    [void][IO.Directory]::CreateDirectory($runRoot)
    $sourceMods = Join-Path $spec.source 'mods'
    $allJars = @(Get-ChildItem -LiteralPath $sourceMods -File -Filter '*.jar' | Sort-Object Name)
    if ($spec.curated) {
        $missing = @($clientGameplayNames | Where-Object { $_ -notin $allJars.Name })
        if ($missing.Count -gt 0) { throw ('Missing curated client JARs: ' + ($missing -join ', ')) }
    }
    $copiedRows = @()
    $omissions = @()
    foreach ($jarFile in $allJars) {
        $sourceHash = Get-CachedHash $jarFile.FullName
        if (Test-PlayerBlocksJar $jarFile) {
            $omissions += [ordered]@{ jar = $jarFile.Name; source_sha256 = $sourceHash; reason = 'Player Blocks is supplied by the current project outputs; exclude by filename or top-level mod metadata to prevent duplicate registration.' }
            continue
        }
        if ($spec.curated -and $jarFile.Name -notin $clientGameplayNames -and $jarFile.Name -notlike 'create_new_world_core-*.jar') {
            $omissions += [ordered]@{ jar = $jarFile.Name; source_sha256 = $sourceHash; reason = 'Omitted by the recorded client gameplay selection; included in the separate complete client fixture.' }
            continue
        }
        $targetJar = Join-Path $modsRoot $jarFile.Name
        $transformation = $null
        if ($jarFile.Name -like 'create-central-kitchen-*.jar') {
            $transformation = Copy-CentralKitchenFixture $jarFile.FullName $targetJar
        } else {
            Copy-Item -LiteralPath $jarFile.FullName -Destination $targetJar
        }
        $fixtureHash = (Get-FileHash -LiteralPath $targetJar -Algorithm SHA256).Hash
        if ($null -eq $transformation -and $fixtureHash -ne $sourceHash) { throw ('JAR copy mismatch: ' + $jarFile.Name) }
        $copiedRows += [ordered]@{
            jar = $jarFile.Name; source = $jarFile.FullName; fixture = $targetJar
            source_bytes = $jarFile.Length; fixture_bytes = (Get-Item -LiteralPath $targetJar).Length
            source_sha256 = $sourceHash; fixture_sha256 = $fixtureHash
            exact_original_jar = ($sourceHash -eq $fixtureHash)
            test_only_transformation = $transformation
        }
    }
    if (@($copiedRows | Where-Object { $_.jar -like 'create_new_world_core-*.jar' }).Count -ne 1) {
        throw ('Expected exactly one installed WorldCore JAR in ' + $spec.name)
    }
    $configRows = @(Copy-RootConfigs $spec.source $runRoot)
    $nativeTarget = Join-Path $runRoot '.pg-native/libpowergridNative7.dll'
    [void][IO.Directory]::CreateDirectory((Split-Path $nativeTarget -Parent))
    Copy-Item -LiteralPath $nativePath -Destination $nativeTarget
    if ((Get-FileHash -LiteralPath $nativeTarget -Algorithm SHA256).Hash -ne $nativeExpected) { throw 'Copied native library checksum mismatch.' }
    $manifest = [ordered]@{
        generated_at = (Get-Date -Format o); name = $spec.name; source_instance = $spec.source
        source_mods = $sourceMods; mods = $modsRoot; run = $runRoot
        installed_jar_count = $allJars.Count; fixture_jar_count = $copiedRows.Count
        exact_original_jar_count = @($copiedRows | Where-Object exact_original_jar).Count
        omitted_jar_count = $omissions.Count; omitted_jars = $omissions
        copied_jars = $copiedRows; copied_config_files = $configRows
        fml_override = [ordered]@{ path = 'config/fml.toml'; contents = "maxThreads = 1`n"; sha256 = (Get-FileHash -LiteralPath (Join-Path $runRoot 'config/fml.toml') -Algorithm SHA256).Hash }
        native_library = [ordered]@{ source = $nativePath; fixture = $nativeTarget; bytes = (Get-Item -LiteralPath $nativeTarget).Length; sha256 = $nativeExpected }
        original_saves_used_or_copied = $false; installed_files_modified = $false
        scope = 'Complete installed production JAR copies except explicitly recorded omissions and the disposable Central Kitchen test-class-only transformation. Root config/defaultconfigs copied; no saves or save-specific serverconfig copied. Player Blocks comes from current project outputs.'
    }
    $manifestPath = Join-Path $targetRoot 'manifest.json'
    Write-Json $manifestPath $manifest
    $rootManifest.fixtures += [ordered]@{ name = $spec.name; mods = $modsRoot; run = $runRoot; manifest = $manifestPath; source_jar_count = $allJars.Count; fixture_jar_count = $copiedRows.Count; omission_count = $omissions.Count; manifest_sha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash }
    Write-Json (Join-Path $fixtureRoot 'manifest.json') $rootManifest
    Write-Output ('PACK_CHECK_READY ' + $spec.name + ' mods=' + $modsRoot + ' run=' + $runRoot + ' jars=' + $copiedRows.Count)
}
