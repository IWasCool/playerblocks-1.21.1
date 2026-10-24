param(
    [string]$Jar,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string]$ExpectedSha256,
    [string]$Evidence,
    [Parameter(Mandatory = $true)]
    [string]$ClientInstance,
    [Parameter(Mandatory = $true)]
    [string]$ServerInstance,
    [string]$BackupRoot
)

# This installs a new mod for the next startup. It never replaces an existing
# different JAR or touches a running game's world, configuration or other mods.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$properties = ConvertFrom-StringData (Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') -Raw)
$version = $properties.mod_version
$fileName = 'player_blocks-' + $version + '.jar'
if (-not $Jar) { $Jar = Join-Path $projectRoot ('build/libs/' + $fileName) }
if (-not $Evidence) { $Evidence = Join-Path $projectRoot ('build/release/' + $version + '/deployment.json') }
if (-not $BackupRoot) { $BackupRoot = Join-Path $projectRoot 'build/backups/player-blocks' }
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ExpectedSha256 = $ExpectedSha256.ToUpperInvariant()
$started = [DateTimeOffset]::Now.ToString('o')
$transaction = 'player-blocks-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N')
$backupDirectory = [IO.Path]::GetFullPath((Join-Path $BackupRoot $transaction))
$evidencePath = [IO.Path]::GetFullPath($Evidence)
$createdTargets = [Collections.Generic.List[string]]::new()
$createdStages = [Collections.Generic.List[string]]::new()

function Get-Hash([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Assert-ChildPath([string]$Path, [string]$Directory) {
    if (-not $Path.StartsWith($Directory.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetDirectoryName($Path) -ne $Directory.TrimEnd('\')) {
        throw "Expected a direct child of '$Directory': $Path"
    }
}

function Read-Entry([IO.Compression.ZipArchiveEntry]$Entry) {
    $reader = [IO.StreamReader]::new($Entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-ArchiveMods([IO.Compression.ZipArchive]$Archive, [string]$OuterPath, [string]$ArchivePath = '') {
    foreach ($metadataName in @('META-INF/neoforge.mods.toml', 'META-INF/mods.toml')) {
        $entry = $Archive.GetEntry($metadataName)
        if ($null -eq $entry) { continue }
        $metadata = Read-Entry $entry
        foreach ($block in [regex]::Matches($metadata, '(?ms)^[ \t]*\[\[mods\]\][ \t]*(?:#[^\r\n]*)?\r?\n(.*?)(?=^[ \t]*\[|\z)')) {
            $id = [regex]::Match($block.Groups[1].Value, '(?m)^\s*modId\s*=\s*["'']([^"'']+)["'']')
            $modVersion = [regex]::Match($block.Groups[1].Value, '(?m)^\s*version\s*=\s*["'']([^"'']+)["'']')
            if ($id.Success) {
                [pscustomobject]@{
                    Id = $id.Groups[1].Value
                    Version = $modVersion.Groups[1].Value
                    OuterPath = $OuterPath
                    ArchivePath = $ArchivePath
                    Metadata = $metadataName
                }
            }
        }
    }
    $fabric = $Archive.GetEntry('fabric.mod.json')
    if ($null -ne $fabric) {
        $metadata = (Read-Entry $fabric) | ConvertFrom-Json
        $fabricVersion = ''
        if ($null -ne $metadata.PSObject.Properties['version']) { $fabricVersion = [string]$metadata.version }
        [pscustomobject]@{
            Id = $metadata.id
            Version = $fabricVersion
            OuterPath = $OuterPath
            ArchivePath = $ArchivePath
            Metadata = 'fabric.mod.json'
        }
    }
    foreach ($entry in $Archive.Entries) {
        if (-not $entry.FullName.EndsWith('.jar', [StringComparison]::OrdinalIgnoreCase)) { continue }
        $memory = [IO.MemoryStream]::new()
        $stream = $entry.Open()
        try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
        $memory.Position = 0
        $nested = $null
        try {
            $nested = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read, $true)
            Get-ArchiveMods $nested $OuterPath ($ArchivePath + '!/' + $entry.FullName)
        } finally {
            if ($null -ne $nested) { $nested.Dispose() }
            $memory.Dispose()
        }
    }
}

function Get-JarMods([string]$Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try { Get-ArchiveMods $archive $Path } finally { $archive.Dispose() }
}

function Get-InstanceState([string]$Name, [string]$InstancePath) {
    $instanceRoot = (Resolve-Path -LiteralPath $InstancePath).ProviderPath.TrimEnd('\')
    $modRoot = (Resolve-Path -LiteralPath (Join-Path $instanceRoot 'mods')).ProviderPath.TrimEnd('\')
    if (-not (Get-Item -LiteralPath $modRoot).PSIsContainer) { throw "Missing mods directory: $modRoot" }
    $target = [IO.Path]::GetFullPath((Join-Path $modRoot $fileName))
    $stage = [IO.Path]::GetFullPath((Join-Path $modRoot ($transaction + '.installing')))
    Assert-ChildPath $target $modRoot
    Assert-ChildPath $stage $modRoot
    $jars = @(Get-ChildItem -LiteralPath $modRoot -Filter '*.jar' -File)
    $playerMods = @()
    $coreJars = @()
    foreach ($installedJar in $jars) {
        $mods = @(Get-JarMods $installedJar.FullName)
        $playerMods += @($mods | Where-Object { $_.Id -eq 'player_blocks' })
        if (@($mods | Where-Object { $_.Id -eq 'create_new_world_core' }).Count -gt 0 -or
            $installedJar.Name -like 'create_new_world_core-*.jar') {
            $coreJars += [pscustomobject]@{ Path = $installedJar.FullName; Sha256 = (Get-Hash $installedJar.FullName) }
        }
    }
    [pscustomobject]@{
        Name = $Name
        Instance = $instanceRoot
        Mods = $modRoot
        Target = $target
        Stage = $stage
        TopLevelJarCount = $jars.Count
        InitialPlayerBlocksAbsent = ($playerMods.Count -eq 0)
        PlayerBlocks = @($playerMods)
        CoreJars = @($coreJars)
    }
}

function Assert-Installable($State) {
    if ($State.PlayerBlocks.Count -eq 0) {
        if (Test-Path -LiteralPath $State.Target) { throw "Target exists without the expected mod metadata: $($State.Target)" }
        return
    }
    if ($State.PlayerBlocks.Count -ne 1) { throw "Duplicate Player Blocks mod declarations in $($State.Mods)" }
    $installed = $State.PlayerBlocks[0]
    if ($installed.OuterPath -ne $State.Target -or $installed.ArchivePath -ne '' -or
        $installed.Version -ne $version -or (Get-Hash $installed.OuterPath) -ne $ExpectedSha256) {
        throw "A different Player Blocks installation already exists in $($State.Mods); additive installer will not replace it."
    }
}

function Assert-CoreUnchanged($Before, $After) {
    if ($Before.CoreJars.Count -ne $After.CoreJars.Count) { throw "Core JAR set changed in $($Before.Mods)" }
    foreach ($core in $Before.CoreJars) {
        $matching = @($After.CoreJars | Where-Object { $_.Path -eq $core.Path -and $_.Sha256 -eq $core.Sha256 })
        if ($matching.Count -ne 1) { throw "Core JAR changed in $($Before.Mods): $($core.Path)" }
    }
}

function Write-Json([string]$Path, $Value) {
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path))
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
}

$source = (Resolve-Path -LiteralPath $Jar).ProviderPath
if ($evidencePath -eq $source -or [IO.Path]::GetExtension($evidencePath) -ne '.json') {
    throw 'Deployment evidence must be a separate .json file.'
}
if ((Get-Hash $source) -ne $ExpectedSha256) { throw 'Source JAR does not match ExpectedSha256.' }
$archive = [IO.Compression.ZipFile]::OpenRead($source)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName.EndsWith('/')) { continue }
        if ($entry.FullName -match '(?i)(?:Tests?(?:\$[^/]*)?|AuditEnvironment(?:\$[^/]*)?|BlockVisualCheck(?:\$[^/]*)?|PlayerTextureCacheCheck(?:\$[^/]*)?)\.class$') {
            throw "Release JAR contains a test class: $($entry.FullName)"
        }
        $stream = $entry.Open()
        try { $stream.CopyTo([IO.Stream]::Null) } finally { $stream.Dispose() }
    }
    if ($null -eq $archive.GetEntry('com/ourcreation/playerblocks/PlayerBlocks.class') -or
        $null -eq $archive.GetEntry('player_blocks.mixins.json')) { throw 'Source archive is not the Player Blocks release mod.' }
    $sourceMods = @(Get-ArchiveMods $archive $source)
    if ($sourceMods.Count -ne 1 -or $sourceMods[0].Id -ne 'player_blocks' -or
        $sourceMods[0].Version -ne $version -or $sourceMods[0].Metadata -ne 'META-INF/neoforge.mods.toml') {
        throw "Source mod metadata must declare exactly Player Blocks $version for NeoForge."
    }
} finally { $archive.Dispose() }

$before = @((Get-InstanceState 'client' $ClientInstance), (Get-InstanceState 'server' $ServerInstance))
if ($before[0].Mods -eq $before[1].Mods) { throw 'Client and server targets must be different directories.' }
foreach ($state in $before) { Assert-Installable $state }
foreach ($state in $before) {
    if ($source -eq $state.Target) { throw 'Source and installed JAR must be different files.' }
    if ($evidencePath.StartsWith($state.Instance + '\', [StringComparison]::OrdinalIgnoreCase) -or
        $backupDirectory.StartsWith($state.Instance + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Evidence and backup manifests must be stored outside the game instances.'
    }
}
[void][IO.Directory]::CreateDirectory($backupDirectory)
$beforeManifest = Join-Path $backupDirectory 'before.json'
Write-Json $beforeManifest ([ordered]@{
    Transaction = $transaction; StartedAt = $started; SourceJar = $source; ExpectedSha256 = $ExpectedSha256
    Targets = $before; Rollback = 'Before first startup, remove only newly created paths listed in deployment evidence. Preserve the mod after worlds begin using it.'
})

try {
    # Prepare both copies before making either visible to a future game startup.
    foreach ($state in $before) {
        if (-not $state.InitialPlayerBlocksAbsent) { continue }
        if (Test-Path -LiteralPath $state.Stage) { throw "Staging path already exists: $($state.Stage)" }
        $inputStream = [IO.File]::OpenRead($source)
        $outputStream = $null
        try {
            $outputStream = [IO.File]::Open($state.Stage, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
            $createdStages.Add($state.Stage)
            $inputStream.CopyTo($outputStream)
        } finally {
            if ($null -ne $outputStream) { $outputStream.Dispose() }
            $inputStream.Dispose()
        }
        if ((Get-Hash $state.Stage) -ne $ExpectedSha256) { throw "Staged JAR verification failed: $($state.Stage)" }
    }
    # Rescan after staging so a concurrent installation cannot introduce duplicates.
    foreach ($state in $before) {
        $current = Get-InstanceState $state.Name $state.Instance
        Assert-Installable $current
        Assert-CoreUnchanged $state $current
        if ($current.InitialPlayerBlocksAbsent -ne $state.InitialPlayerBlocksAbsent -or
            $current.TopLevelJarCount -ne $state.TopLevelJarCount) { throw "Installed mod set changed during staging: $($state.Mods)" }
    }
    foreach ($state in $before) {
        if (-not $state.InitialPlayerBlocksAbsent) { continue }
        [IO.File]::Move($state.Stage, $state.Target)
        $createdTargets.Add($state.Target)
        if ((Get-Hash $state.Target) -ne $ExpectedSha256) { throw "Installed JAR verification failed: $($state.Target)" }
    }
    $after = @()
    foreach ($state in $before) {
        $current = Get-InstanceState $state.Name $state.Instance
        Assert-Installable $current
        Assert-CoreUnchanged $state $current
        $expectedCount = $state.TopLevelJarCount + [int]$state.InitialPlayerBlocksAbsent
        if ($current.InitialPlayerBlocksAbsent -or $current.TopLevelJarCount -ne $expectedCount) {
            throw "Installed mod count or Player Blocks verification failed: $($state.Mods)"
        }
        $after += [pscustomobject]@{
            Name = $state.Name; Instance = $state.Instance; InstalledJar = $state.Target
            InstalledSha256 = (Get-Hash $state.Target); InitialPlayerBlocksAbsent = $state.InitialPlayerBlocksAbsent
            BeforeTopLevelJarCount = $state.TopLevelJarCount; AfterTopLevelJarCount = $current.TopLevelJarCount
            PlayerBlocksDeclarations = $current.PlayerBlocks.Count; CoreSha256Unchanged = $true; CoreJars = $current.CoreJars
            CreatedByThisInvocation = $createdTargets.Contains($state.Target)
        }
    }
    $result = [ordered]@{
        Status = 'installed'; Version = $version; Transaction = $transaction
        StartedAt = $started; CompletedAt = [DateTimeOffset]::Now.ToString('o')
        SourceJar = $source; ExpectedSha256 = $ExpectedSha256; BeforeManifest = $beforeManifest
        Targets = $after; RollbackExactPaths = @($createdTargets.ToArray())
        RollbackInstructions = 'Before the next startup, remove only RollbackExactPaths. No previous Player Blocks files were replaced. Preserve the JAR after worlds begin using its content.'
        RestartRequired = $true; RunningGameAndWorldsModified = $false
    }
    Write-Json (Join-Path $backupDirectory 'deployment.json') $result
    Write-Json $evidencePath $result
} catch {
    $failure = $_
    $rollbackErrors = @()
    # Never remove a pre-existing path or a file changed by another writer.
    foreach ($path in $createdTargets) {
        try {
            if (Test-Path -LiteralPath $path) {
                if ((Get-Hash $path) -ne $ExpectedSha256) { throw "Refusing rollback of changed file: $path" }
                [IO.File]::Delete($path)
            }
        } catch { $rollbackErrors += $_.Exception.Message }
    }
    foreach ($path in $createdStages) {
        try { if (Test-Path -LiteralPath $path) { [IO.File]::Delete($path) } } catch { $rollbackErrors += $_.Exception.Message }
    }
    Write-Json (Join-Path $backupDirectory 'failure.json') ([ordered]@{
        Status = 'failed'; Transaction = $transaction; FailedAt = [DateTimeOffset]::Now.ToString('o')
        Error = $failure.Exception.Message; BeforeManifest = $beforeManifest
        CreatedTargets = @($createdTargets.ToArray()); RollbackErrors = $rollbackErrors
    })
    if ($rollbackErrors.Count -gt 0) { throw ($failure.Exception.Message + ' Rollback errors: ' + ($rollbackErrors -join '; ')) }
    throw $failure
}

Write-Output "Installed verified Player Blocks $version on both sides. Restart Minecraft to load the new mod."
Write-Output "SHA-256: $ExpectedSha256"
Write-Output "Evidence: $evidencePath"
Write-Output "Before-state / rollback manifest: $beforeManifest"
