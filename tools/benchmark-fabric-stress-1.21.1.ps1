[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot',
    [string]$GradleUserHome = 'C:\gradle-cache-umce',
    [ValidateRange(1, 100)][int]$Players = 100,
    [ValidateRange(0, 10000)][int]$Entities = 10000,
    [ValidateRange(0, 500)][int]$Villagers = 500,
    [ValidateRange(10, 3600)][int]$MeasureSeconds = 60,
    [ValidateRange(1, 10)][int]$Repeats = 3,
    [string]$UmceJar = '',
    [string]$ResultTag = '',
    [switch]$ProfileOnly,
    [string]$ProfilerJar = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$benchmarkRoot = Join-Path $root 'build\benchmark-1.21.1'
$templateRoot = Join-Path $benchmarkRoot 'server-template'
$seedRoot = Join-Path $benchmarkRoot 'seed-world'
$runsRoot = Join-Path $benchmarkRoot 'runs'
$outputRoot = Join-Path $root 'benchmark-results'
$defaultArtifact = Join-Path $root 'platforms\fabric-1.21.1\build\libs\umce-fabric-1.21.1-0.1.0-alpha.2.jar'
$artifact = if ([string]::IsNullOrWhiteSpace($UmceJar)) { $defaultArtifact } else { (Resolve-Path -LiteralPath $UmceJar).Path }
$installer = Join-Path $benchmarkRoot 'fabric-installer-1.1.2.jar'
$java = Join-Path $JavaHome 'bin\java.exe'
$javaToolOptions = $env:JAVA_TOOL_OPTIONS
if ([string]::IsNullOrWhiteSpace($javaToolOptions)) {
    $javaToolOptions = '--patch-module=java.base=C:\Users\Public\valoria-jdk-patch'
}
$port = 25586
$clientScript = Join-Path $PSScriptRoot 'minecraft-stress-clients.cjs'
$entityCounter = Join-Path $PSScriptRoot 'count-minecraft-entities.py'
$clientDependencyRoot = Join-Path $benchmarkRoot 'client-deps'
$entitySpawnCount = if ($Entities -gt 0) { $Entities + [Math]::Max(25, [Math]::Ceiling($Entities * 0.01)) } else { 0 }
$culture = [Globalization.CultureInfo]::InvariantCulture
if (-not [string]::IsNullOrWhiteSpace($ResultTag) -and $ResultTag -notmatch '^[a-z0-9][a-z0-9_-]{0,39}$') {
    throw 'ResultTag must contain 1-40 lowercase letters, numbers, underscores, or hyphens.'
}

if (-not (Test-Path -LiteralPath $java)) { throw "Java 21 not found: $java" }
if (-not (Test-Path -LiteralPath $artifact)) { throw "Build the 1.21.1 adapter first: $artifact" }
$artifactSha256 = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
$artifactLabel = [System.IO.Path]::GetRelativePath($root, $artifact).Replace('\', '/')
$node = (Get-Command node.exe -ErrorAction Stop).Source
$python = (Get-Command python.exe -ErrorAction Stop).Source
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$apiRoot = Join-Path $GradleUserHome 'caches\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-api\0.116.17+1.21.1'
$apiJar = Get-ChildItem -LiteralPath $apiRoot -Recurse -Filter '*.jar' | Where-Object Length -gt 100000 | Select-Object -First 1 -ExpandProperty FullName
if (-not $apiJar) { throw "Fabric API 0.116.17+1.21.1 is not cached: $apiRoot" }

New-Item -ItemType Directory -Force $benchmarkRoot, $runsRoot, $outputRoot | Out-Null
if ($ProfileOnly) {
    if (-not [string]::IsNullOrWhiteSpace($ProfilerJar)) {
        $sparkJar = (Resolve-Path -LiteralPath $ProfilerJar).Path
    } else {
        $sparkCache = Join-Path $benchmarkRoot 'spark-fabric-1.21.1.jar'
        $versions = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/project/spark/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22fabric%22%5D' -Headers @{'User-Agent'='UMCE/0.1.0 (https://github.com/Lololegeek/UMCE)'}
        $sparkVersion = $versions | Select-Object -First 1
        if (-not $sparkVersion -or $sparkVersion.files.Count -lt 1) { throw 'Modrinth returned no Spark release for Fabric 1.21.1.' }
        $sparkFile = $sparkVersion.files | Where-Object primary | Select-Object -First 1
        if (-not $sparkFile) { $sparkFile = $sparkVersion.files | Select-Object -First 1 }
        if (-not $sparkFile.hashes.sha512) { throw 'The selected Spark release has no SHA-512 hash.' }
        if (-not (Test-Path -LiteralPath $sparkCache) -or
            (Get-FileHash -LiteralPath $sparkCache -Algorithm SHA512).Hash.ToLowerInvariant() -ne $sparkFile.hashes.sha512.ToLowerInvariant()) {
            Invoke-WebRequest -Uri $sparkFile.url -OutFile $sparkCache
        }
        if ((Get-FileHash -LiteralPath $sparkCache -Algorithm SHA512).Hash.ToLowerInvariant() -ne $sparkFile.hashes.sha512.ToLowerInvariant()) {
            throw 'Downloaded Spark artifact failed its Modrinth SHA-512 verification.'
        }
        $sparkJar = $sparkCache
        Write-Output "Using Spark $($sparkVersion.version_number) for the isolated profile run."
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $templateRoot 'fabric-server-launch.jar'))) {
    New-Item -ItemType Directory -Force $templateRoot | Out-Null
    if (-not (Test-Path -LiteralPath $installer)) {
        Invoke-WebRequest 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar' -OutFile $installer
    }
    & $java -jar $installer server -mcversion 1.21.1 -loader 0.16.14 -dir $templateRoot -downloadMinecraft
    if ($LASTEXITCODE -ne 0) { throw 'Fabric 1.21.1 server installation failed.' }
}

function Write-ServerConfig([string]$Directory) {
    $mods = Join-Path $Directory 'mods'
    New-Item -ItemType Directory -Force $mods | Out-Null
    Get-ChildItem -LiteralPath $mods -Filter '*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item -LiteralPath $apiJar -Destination (Join-Path $mods 'fabric-api.jar') -Force
    if ($ProfileOnly) { Copy-Item -LiteralPath $sparkJar -Destination (Join-Path $mods 'spark-profiler.jar') -Force }
    Set-Content -LiteralPath (Join-Path $Directory 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $Directory 'server.properties') -Encoding ascii -Value @(
        'server-ip=127.0.0.1', "server-port=$port", 'online-mode=false', 'max-players=128',
        'view-distance=8', 'simulation-distance=8', 'spawn-protection=0', 'level-name=world',
        'level-seed=21072121', 'motd=UMCE 1.21.1 stress comparison',
        'sync-chunk-writes=false', 'max-tick-time=120000'
    )
}

function Write-StressDatapack([string]$WorldDirectory) {
    $pack = Join-Path $WorldDirectory 'datapacks\umce-stress'
    $functions = Join-Path $pack 'data\umce\function'
    New-Item -ItemType Directory -Force $functions | Out-Null
    Set-Content -LiteralPath (Join-Path $pack 'pack.mcmeta') -Encoding utf8 -Value '{"pack":{"pack_format":48,"description":"UMCE reproducible stress scenario for 1.21.1"}}'
    $commands = [Collections.Generic.List[string]]::new()
    $commands.Add('gamerule doMobSpawning false')
    $commands.Add('gamerule doDaylightCycle false')
    $commands.Add('gamerule doWeatherCycle false')
    $commands.Add('execute positioned 0 320 0 positioned over motion_blocking_no_leaves run setworldspawn ~ ~1 ~')
    $commands.Add('forceload add -128 -128 127 127')
    for ($index = 0; $index -lt $entitySpawnCount; $index++) {
        $x = -100 + (($index % 100) * 2)
        $z = -100 + ([Math]::Floor($index / 100) * 2)
        $commands.Add("execute positioned $x 320 $z positioned over motion_blocking_no_leaves run summon minecraft:pig ~ ~1 ~ {PersistenceRequired:1b}")
    }
    for ($index = 0; $index -lt $Villagers; $index++) {
        $x = -96 + (($index % 25) * 4)
        $z = -96 + ([Math]::Floor($index / 25) * 4)
        $commands.Add("execute positioned $x 320 $z positioned over motion_blocking_no_leaves run summon minecraft:villager ~ ~1 ~ {PersistenceRequired:1b}")
    }

    # Sixteen blocked hopper lines keep item-transfer checks active during measurement.
    for ($row = 0; $row -lt 16; $row++) {
        $z = -8 + $row
        for ($column = 0; $column -lt 16; $column++) {
            $x = -8 + $column
            $commands.Add("setblock $x 79 $z minecraft:stone")
            $commands.Add("setblock $x 80 $z minecraft:hopper[facing=east]")
            $commands.Add("item replace block $x 80 $z container.0 with minecraft:stone 64")
        }
        $chestX = 8
        $commands.Add("setblock $chestX 79 $z minecraft:stone")
        $commands.Add("setblock $chestX 80 $z minecraft:chest")
        $items = [Collections.Generic.List[string]]::new()
        for ($slot = 0; $slot -lt 27; $slot++) { $items.Add('{Slot:' + $slot + 'b,id:"minecraft:stone",count:64}') }
        $commands.Add("data merge block $chestX 80 $z {Items:[$($items -join ',')]}")
    }

    # Paired observers act as self-running redstone clocks.
    for ($clock = 0; $clock -lt 8; $clock++) {
        $x = -96 + ($clock * 3)
        $commands.Add("setblock $x 79 24 minecraft:stone")
        $commands.Add("setblock $($x + 1) 79 24 minecraft:stone")
        $commands.Add("setblock $x 80 24 minecraft:observer[facing=east]")
        $commands.Add("setblock $($x + 1) 80 24 minecraft:observer[facing=west]")
    }
    $batchSize = 200
    $batchCount = [Math]::Ceiling($commands.Count / $batchSize)
    for ($batch = 0; $batch -lt $batchCount; $batch++) {
        $start = $batch * $batchSize
        $end = [Math]::Min($commands.Count - 1, $start + $batchSize - 1)
        $batchName = 'setup_' + $batch.ToString('D3', $culture)
        Set-Content -LiteralPath (Join-Path $functions "$batchName.mcfunction") -Encoding utf8 -Value $commands.GetRange($start, $end - $start + 1)
    }
    return [int]$batchCount
}

if (-not ('UMCE.OutputCapture' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace UMCE {
    public sealed class OutputCapture {
        private readonly Process process;
        private readonly string stdoutPath;
        private readonly string stderrPath;
        public ConcurrentQueue<string> Lines { get; } = new ConcurrentQueue<string>();

        public OutputCapture(Process process, string stdoutPath, string stderrPath) {
            this.process = process;
            this.stdoutPath = stdoutPath;
            this.stderrPath = stderrPath;
            process.OutputDataReceived += OnOutput;
            process.ErrorDataReceived += OnError;
        }

        public void Start() {
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
        }

        private void OnOutput(object sender, DataReceivedEventArgs args) {
            if (args.Data == null) return;
            Lines.Enqueue(args.Data);
            File.AppendAllText(stdoutPath, args.Data + Environment.NewLine, Encoding.UTF8);
        }

        private void OnError(object sender, DataReceivedEventArgs args) {
            if (args.Data == null) return;
            File.AppendAllText(stderrPath, args.Data + Environment.NewLine, Encoding.UTF8);
        }
    }
}
'@
}

function Start-Server([string]$Directory, [string]$Label) {
    $log = Join-Path $outputRoot "$Label.log"
    $stderrLog = Join-Path $outputRoot "$Label-stderr.log"
    Set-Content -LiteralPath $log -Encoding utf8 -Value ''
    Set-Content -LiteralPath $stderrLog -Encoding utf8 -Value ''
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java
    $info.Arguments = '-Xms8G -Xmx8G -jar fabric-server-launch.jar nogui'
    $info.WorkingDirectory = $Directory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.Environment['JAVA_HOME'] = $JavaHome
    if ($javaToolOptions) { $info.Environment['JAVA_TOOL_OPTIONS'] = $javaToolOptions }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw "Could not start $Label." }
    $capture = [UMCE.OutputCapture]::new($process, $log, $stderrLog)
    $capture.Start()
    $server = [pscustomobject]@{
        Process = $process; Log = $log; Lines = $capture.Lines; Capture = $capture
    }
    $ready = $false
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes(4)
    while (-not $ready -and [DateTimeOffset]::UtcNow -lt $deadline) {
        Read-ServerOutput $server
        $ready = @($server.Lines | Where-Object { $_ -match 'Done \([^)]*\)! For help' }).Count -gt 0
        if ($process.HasExited) { throw "Server $Label exited; inspect $($server.Log)." }
        Start-Sleep -Milliseconds 50
    }
    if (-not $ready) { throw "Server $Label did not reach readiness; inspect $($server.Log)." }
    return $server
}

function Read-ServerOutput($Server) {
    # Output is captured continuously by asynchronous process data handlers.
}

function Send-Command($Server, [string]$Command) {
    $Server.Process.StandardInput.WriteLine($Command)
    $Server.Process.StandardInput.Flush()
}

function Wait-Server($Server, [int]$Milliseconds) {
    $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($Milliseconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        Read-ServerOutput $Server
        if ($Server.Process.HasExited) { throw "Server stopped unexpectedly; inspect $($Server.Log)." }
        Start-Sleep -Milliseconds 50
    }
}

function Wait-ForLog([object]$Server, [string]$Pattern, [int]$TimeoutSeconds = 30) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        Read-ServerOutput $Server
        if (@($Server.Lines | Where-Object { $_ -match $Pattern }).Count -gt 0) { return }
        if ($Server.Process.HasExited) { throw "Server exited while waiting for '$Pattern'; inspect $($Server.Log)." }
        Start-Sleep -Milliseconds 50
    }
    throw "Timed out waiting for '$Pattern'; inspect $($Server.Log)."
}

function Read-ClientOutput($Client) {
    # Output is captured continuously by asynchronous process data handlers.
}

function Start-StressClients([string]$Label) {
    $mineflayerPath = Join-Path $clientDependencyRoot 'node_modules\mineflayer\package.json'
    if (-not (Test-Path -LiteralPath $mineflayerPath)) {
        New-Item -ItemType Directory -Force $clientDependencyRoot | Out-Null
        $npmOutput = & $npm install --prefix $clientDependencyRoot --no-audit --no-fund mineflayer 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Installing Mineflayer stress clients failed: $($npmOutput -join ' ')" }
    }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $node
    $info.Arguments = "`"$clientScript`" 127.0.0.1 $port $Players"
    $info.WorkingDirectory = $root
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.Environment['NODE_PATH'] = Join-Path $clientDependencyRoot 'node_modules'
    $info.Environment['UMCE_STRESS_LOGIN_INTERVAL_MS'] = '150'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw 'Could not start Mineflayer stress clients.' }
    $log = Join-Path $outputRoot "$Label-clients.log"
    $stderrLog = Join-Path $outputRoot "$Label-clients-stderr.log"
    Set-Content -LiteralPath $log -Encoding utf8 -Value ''
    Set-Content -LiteralPath $stderrLog -Encoding utf8 -Value ''
    $capture = [UMCE.OutputCapture]::new($process, $log, $stderrLog)
    $capture.Start()
    $client = [pscustomobject]@{
        Process = $process; Log = $log; Lines = $capture.Lines; Capture = $capture
    }
    return $client
}

function Wait-ForClient([object]$Client, [string]$Username, [int]$TimeoutSeconds = 120) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        Read-ClientOutput $Client
        if (@($Client.Lines | Where-Object { $_ -eq "READY:${Username}" }).Count -gt 0) { return }
        $failure = @($Client.Lines | Where-Object { $_ -like "FAIL:${Username}:*" } | Select-Object -Last 1)
        if ($failure.Count -gt 0) { throw "Stress client failed: $($failure[0]); inspect $($Client.Log)." }
        if ($Client.Process.HasExited) { throw "Stress client process exited; inspect $($Client.Log)." }
        Start-Sleep -Milliseconds 50
    }
    throw "Timed out waiting for Mineflayer client $Username; inspect $($Client.Log)."
}

function Stop-StressClients($Client) {
    if ($null -eq $Client) { return }
    try { $Client.Process.StandardInput.WriteLine('stop'); $Client.Process.StandardInput.Flush() } catch { }
    if (-not $Client.Process.WaitForExit(30000)) { $Client.Process.Kill($true) }
    $Client.Process.WaitForExit()
    $Client.Process.Dispose()
}

function Stop-Server($Server) {
    try { Send-Command $Server 'stop' } catch { }
    if (-not $Server.Process.WaitForExit(60000)) { $Server.Process.Kill($true) }
    $Server.Process.WaitForExit()
    $Server.Process.Dispose()
}

function Measure-Run([string]$Condition, [int]$Repeat, [string]$WorldPath) {
    $runTag = if ([string]::IsNullOrWhiteSpace($ResultTag)) { '' } else { "-$ResultTag" }
    $label = "$Condition$runTag-r$Repeat"
    $directory = Join-Path $runsRoot $label
    if (Test-Path -LiteralPath $directory) { Remove-Item -LiteralPath $directory -Recurse -Force }
    New-Item -ItemType Directory -Force $directory | Out-Null
    Copy-Item -Path (Join-Path $templateRoot '*') -Destination $directory -Recurse -Force
    Write-ServerConfig $directory
    $destinationWorld = Join-Path $directory 'world'
    if (Test-Path -LiteralPath $destinationWorld) { Remove-Item -LiteralPath $destinationWorld -Recurse -Force }
    New-Item -ItemType Directory -Force $destinationWorld | Out-Null
    Copy-Item -Path (Join-Path $WorldPath '*') -Destination $destinationWorld -Recurse -Force
    if ($Condition -eq 'with-umce') { Copy-Item -LiteralPath $artifact -Destination (Join-Path $directory 'mods\umce.jar') -Force }

    $server = Start-Server $directory $label
    $clients = $null
    try {
        $clients = Start-StressClients $label
        for ($index = 0; $index -lt $Players; $index++) {
            $name = 'stress' + $index.ToString('D3', $culture)
            Wait-ForClient $clients $name
        }
        Send-Command $server 'list'
        if ($Condition -eq 'with-umce') { Send-Command $server 'umce status' }
        Wait-Server $server 5000
        $playerLine = @($server.Lines | Where-Object { $_ -match 'There are ([0-9]+) of a max' } | Select-Object -Last 1)
        if ($playerLine.Count -eq 0) {
            throw "Could not verify online player count for $label; inspect $($server.Log)."
        }
        $observedPlayers = [int]([regex]::Match($playerLine[0], 'There are ([0-9]+) of a max').Groups[1].Value)
        if ($observedPlayers -lt $Players) {
            throw "Workload shortfall in ${label}: players $observedPlayers/$Players; inspect $($server.Log)."
        }
        $clients.Process.StandardInput.WriteLine('start')
        $clients.Process.StandardInput.Flush()
        Wait-Server $server 1000

        $warmupEnd = [DateTimeOffset]::UtcNow.AddSeconds(20)
        while ([DateTimeOffset]::UtcNow -lt $warmupEnd) { Wait-Server $server 500 }
        if ($ProfileOnly) {
            Send-Command $server 'spark profiler start --thread * --force-java-sampler'
            Wait-ForLog $server 'Profiler started|Profiler is now running' 30
            Wait-Server $server ($MeasureSeconds * 1000)
            $profileStart = $server.Lines.Count
            Send-Command $server 'spark profiler stop --save-to-file'
            $profileDeadline = [DateTimeOffset]::UtcNow.AddSeconds(60)
            do {
                Wait-Server $server 250
                $profileResponse = ($server.Lines | Select-Object -Skip $profileStart) -join ' '
            } while ($profileResponse -notmatch '(?i)(saved|written).*(profile|spark)|(?i)(profile|spark).*(saved|written)' -and [DateTimeOffset]::UtcNow -lt $profileDeadline)
            if ($profileResponse -notmatch '(?i)(saved|written).*(profile|spark)|(?i)(profile|spark).*(saved|written)') {
                throw "Spark did not confirm a local profile file; inspect $($server.Log)."
            }
            Write-Output "Spark profile: $profileResponse"
            return @()
        }
        $samples = [Collections.Generic.List[object]]::new()
        $measureEnd = [DateTimeOffset]::UtcNow.AddSeconds($MeasureSeconds)
        $previousCpu = $server.Process.TotalProcessorTime.TotalMilliseconds
        $previousAt = [DateTimeOffset]::UtcNow
        while ([DateTimeOffset]::UtcNow -lt $measureEnd) {
            $lineStart = $server.Lines.Count
            $sampleAt = [DateTimeOffset]::UtcNow
            Send-Command $server 'tick query'
            $queryDeadline = [DateTimeOffset]::UtcNow.AddSeconds(30)
            do {
                Wait-Server $server 100
                $response = ($server.Lines | Select-Object -Skip $lineStart) -join ' '
                $meanMatch = [regex]::Match($response, 'Average time per tick:\s*([0-9.,]+)ms')
            } while (-not $meanMatch.Success -and [DateTimeOffset]::UtcNow -lt $queryDeadline)
            if (-not $meanMatch.Success) { throw "Timed out waiting for /tick query output in $label; inspect $($server.Log)." }
            $meanMatch = [regex]::Match($response, 'Average time per tick:\s*([0-9.,]+)ms')
            $p50Match = [regex]::Match($response, 'P50:\s*([0-9.,]+)ms')
            $p95Match = [regex]::Match($response, 'P95:\s*([0-9.,]+)ms')
            $p99Match = [regex]::Match($response, 'P99:\s*([0-9.,]+)ms')
            $countMatch = [regex]::Match($response, 'sample:\s*([0-9]+)', 'IgnoreCase')
            $now = [DateTimeOffset]::UtcNow
            $cpu = $server.Process.TotalProcessorTime.TotalMilliseconds
            $wall = [Math]::Max(1.0, ($now - $previousAt).TotalMilliseconds)
            $samples.Add([pscustomobject]@{
                condition = $Condition; repeat = $Repeat; time_utc = $sampleAt.ToString('o')
                artifact_sha256 = if ($Condition -eq 'with-umce') { $artifactSha256 } else { $null }
                players_observed = $observedPlayers; pigs_in_seed_world = $entityCounts.pigs; villagers_in_seed_world = $entityCounts.villagers
                tick_mean_ms = if ($meanMatch.Success) { [double]::Parse($meanMatch.Groups[1].Value.Replace(',', '.'), $culture) } else { $null }
                tick_p50_ms = if ($p50Match.Success) { [double]::Parse($p50Match.Groups[1].Value.Replace(',', '.'), $culture) } else { $null }
                tick_p95_ms = if ($p95Match.Success) { [double]::Parse($p95Match.Groups[1].Value.Replace(',', '.'), $culture) } else { $null }
                tick_p99_ms = if ($p99Match.Success) { [double]::Parse($p99Match.Groups[1].Value.Replace(',', '.'), $culture) } else { $null }
                tick_sample_count = if ($countMatch.Success) { [int]$countMatch.Groups[1].Value } else { $null }
                process_cpu_percent_one_core = (($cpu - $previousCpu) / $wall) * 100.0
                working_set_bytes = $server.Process.WorkingSet64
                response = $response
            })
            $previousCpu = $cpu
            $previousAt = $now
        }
        if ($Condition -eq 'with-umce') { Send-Command $server 'umce status' }
        Wait-Server $server 1000
        $samples
    } finally { Stop-StressClients $clients; Stop-Server $server }
}

function Get-Median([double[]]$Values) {
    if ($Values.Count -eq 0) { return [double]::NaN }
    $sorted = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

function Get-StandardDeviation([double[]]$Values) {
    if ($Values.Count -lt 2) { return [double]::NaN }
    $average = ($Values | Measure-Object -Average).Average
    $sumSquares = 0.0
    foreach ($value in $Values) { $sumSquares += [Math]::Pow($value - $average, 2) }
    return [Math]::Sqrt($sumSquares / ($Values.Count - 1))
}

# Create one immutable world with preloaded chunks, villagers, pigs, hoppers and redstone.
if (Test-Path -LiteralPath $seedRoot) { Remove-Item -LiteralPath $seedRoot -Recurse -Force }
New-Item -ItemType Directory -Force $seedRoot | Out-Null
Copy-Item -Path (Join-Path $templateRoot '*') -Destination $seedRoot -Recurse -Force
Write-ServerConfig $seedRoot
$worldPath = Join-Path $seedRoot 'world'
if (Test-Path -LiteralPath $worldPath) { Remove-Item -LiteralPath $worldPath -Recurse -Force }
New-Item -ItemType Directory -Force $worldPath | Out-Null
$batchCount = Write-StressDatapack $worldPath
$setupServer = Start-Server $seedRoot 'stress-world-setup'
try {
    for ($batch = 0; $batch -lt $batchCount; $batch++) {
        $batchName = 'setup_' + $batch.ToString('D3', $culture)
        Send-Command $setupServer "function umce:$batchName"
        Wait-Server $setupServer 1000
        Write-Progress -Activity 'Creating shared Minecraft stress world' -Status "Function $($batch + 1) / $batchCount" -PercentComplete (100 * ($batch + 1) / $batchCount)
    }
    Send-Command $setupServer 'save-all flush'
    Wait-Server $setupServer 10000
} finally { Stop-Server $setupServer }
if (-not (Test-Path -LiteralPath (Join-Path $worldPath 'level.dat'))) { throw 'Stress setup did not save a Minecraft world.' }
$entityCounts = (& $python $entityCounter $worldPath | ConvertFrom-Json)
if ($LASTEXITCODE -ne 0 -or $entityCounts.pigs -lt $Entities -or $entityCounts.villagers -lt $Villagers) {
    throw "Saved workload is short: pigs $($entityCounts.pigs)/$Entities, villagers $($entityCounts.villagers)/$Villagers."
}
$seedChunkCounts = (& $python (Join-Path $PSScriptRoot 'count-minecraft-chunks.py') $worldPath | ConvertFrom-Json)
if ($LASTEXITCODE -ne 0) { throw 'Could not count the preloaded overworld chunks.' }

$all = [Collections.Generic.List[object]]::new()
if ($ProfileOnly) {
    Measure-Run 'with-umce' 1 $worldPath | Out-Null
} else {
    for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
        $order = if ($repeat % 2 -eq 1) { @('without-umce', 'with-umce') } else { @('with-umce', 'without-umce') }
        foreach ($condition in $order) {
            foreach ($sample in (Measure-Run $condition $repeat $worldPath)) { $all.Add($sample) }
        }
    }
}
if ($ProfileOnly) { return }
$runTag = if ([string]::IsNullOrWhiteSpace($ResultTag)) { '' } else { "-$ResultTag" }
$withoutWorld = Join-Path (Join-Path $runsRoot "without-umce$runTag-r$Repeats") 'world'
$withWorld = Join-Path (Join-Path $runsRoot "with-umce$runTag-r$Repeats") 'world'
$withoutChunkCounts = (& $python (Join-Path $PSScriptRoot 'count-minecraft-chunks.py') $withoutWorld | ConvertFrom-Json)
$withChunkCounts = (& $python (Join-Path $PSScriptRoot 'count-minecraft-chunks.py') $withWorld | ConvertFrom-Json)
if ($LASTEXITCODE -ne 0) { throw 'Could not count saved overworld chunks after the paired runs.' }

$stamp = Get-Date -Format 'yyyy-MM-dd'
$resultSuffix = if ([string]::IsNullOrWhiteSpace($ResultTag)) { '' } else { "-$ResultTag" }
$csv = Join-Path $outputRoot "$stamp-1.21.1-stress$resultSuffix-samples.csv"
$report = Join-Path $outputRoot "$stamp-1.21.1-stress$resultSuffix-comparison.md"
$all | Export-Csv -LiteralPath $csv -NoTypeInformation -Encoding utf8
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# UMCE Minecraft 1.21.1 stress comparison')
$lines.Add('')
$lines.Add("Captured: $([DateTimeOffset]::UtcNow.ToString('u'))")
$lines.Add('')
$lines.Add("Minecraft 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.17+1.21.1, Java 21, fixed seed 21072121, normal terrain. Each run starts from an identical saved world. Both conditions include Fabric API and identical 1.21.1 Mineflayer clients; only UMCE differs. UMCE artifact: $artifactLabel (SHA-256 $artifactSha256). Workload: $Players real TCP/protocol clients (online count checked with /list), $($entityCounts.pigs) pigs, $($entityCounts.villagers) villagers verified from saved Anvil entity data, 256 filled hoppers with blocked destination chests, 16 paired observer blocks, 8-chunk view/simulation distances, and clients continuously walking into new chunks. A 16 x 16 chunk region is force-loaded and remains active while clients explore beyond it. Each run warms up 20 seconds, then measures $MeasureSeconds seconds; $Repeats paired repeat(s). No Create factory is included: the available Create release for 1.21.1 targets NeoForge, while this adapter and test target Fabric.")
$lines.Add('')
$lines.Add('| Condition | Samples | Mean tick ms | Mean P95 ms | Mean P99 ms | Mean process CPU (% of one core) | Mean working set MiB |')
$lines.Add('|---|---:|---:|---:|---:|---:|---:|')
foreach ($condition in @('without-umce', 'with-umce')) {
    $rows = @($all | Where-Object { $_.condition -eq $condition -and $null -ne $_.tick_mean_ms })
    $mean = ($rows | Measure-Object tick_mean_ms -Average).Average
    $p95 = ($rows | Measure-Object tick_p95_ms -Average).Average
    $p99 = ($rows | Measure-Object tick_p99_ms -Average).Average
    $cpu = ($rows | Measure-Object process_cpu_percent_one_core -Average).Average
    $memory = (($rows | Measure-Object working_set_bytes -Average).Average / 1MB)
    $lines.Add("| $condition | $($rows.Count) | $($mean.ToString('F3', $culture)) | $($p95.ToString('F3', $culture)) | $($p99.ToString('F3', $culture)) | $($cpu.ToString('F1', $culture)) | $($memory.ToString('F1', $culture)) |")
}
$runMedians = @{}
$lines.Add('')
$lines.Add('## Paired run medians')
$lines.Add('')
$lines.Add('Each cell is the median of completed rolling `/tick query` windows from one run. The window values overlap, so they are descriptive and the independent comparison unit is the paired run.')
$lines.Add('')
$lines.Add('| Pair | First condition | without-UMCE median MSPT | with-UMCE median MSPT | CPU without / with (% one core) | Working set without / with (MiB) |')
$lines.Add('|---:|---|---:|---:|---:|---:|')
for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
    $pair = @($all | Where-Object { $_.repeat -eq $repeat })
    $without = @($pair | Where-Object condition -eq 'without-umce')
    $with = @($pair | Where-Object condition -eq 'with-umce')
    $withoutMedian = Get-Median ([double[]]@($without | Where-Object { $null -ne $_.tick_mean_ms } | ForEach-Object { $_.tick_mean_ms }))
    $withMedian = Get-Median ([double[]]@($with | Where-Object { $null -ne $_.tick_mean_ms } | ForEach-Object { $_.tick_mean_ms }))
    $withoutCpu = Get-Median ([double[]]@($without | ForEach-Object { $_.process_cpu_percent_one_core }))
    $withCpu = Get-Median ([double[]]@($with | ForEach-Object { $_.process_cpu_percent_one_core }))
    $withoutMemoryBytes = Get-Median ([double[]]@($without | ForEach-Object { $_.working_set_bytes }))
    $withMemoryBytes = Get-Median ([double[]]@($with | ForEach-Object { $_.working_set_bytes }))
    $withoutMemory = $withoutMemoryBytes / 1MB
    $withMemory = $withMemoryBytes / 1MB
    $firstCondition = if ($repeat % 2 -eq 1) { 'without-umce' } else { 'with-umce' }
    $runMedians[$repeat] = @{ without = $withoutMedian; with = $withMedian }
    $lines.Add("| $repeat | $firstCondition | $($withoutMedian.ToString('F3', $culture)) | $($withMedian.ToString('F3', $culture)) | $($withoutCpu.ToString('F1', $culture)) / $($withCpu.ToString('F1', $culture)) | $($withoutMemory.ToString('F1', $culture)) / $($withMemory.ToString('F1', $culture)) |")
}
$pairedPercentChanges = [Collections.Generic.List[double]]::new()
for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
    $baseline = $runMedians[$repeat].without
    $candidate = $runMedians[$repeat].with
    if (-not [double]::IsNaN($baseline) -and -not [double]::IsNaN($candidate) -and $baseline -gt 0) {
        $pairedPercentChanges.Add((($candidate - $baseline) / $baseline) * 100.0)
    }
}
$changeMedian = Get-Median ([double[]]$pairedPercentChanges.ToArray())
$changeStddev = Get-StandardDeviation ([double[]]$pairedPercentChanges.ToArray())
$lines.Add('')
$lines.Add("Median paired change in rolling MSPT windows (UMCE vs baseline): $($changeMedian.ToString('F2', $culture))%; sample standard deviation across pairs: $($changeStddev.ToString('F2', $culture)) percentage points; valid pairs: $($pairedPercentChanges.Count)/$Repeats. Positive values are slower with UMCE. This is instrumentation overhead, not an optimization comparison.")
$lines.Add('')
$lines.Add("Saved overworld chunk records: $($seedChunkCounts.saved_overworld_chunks) in the seed world, $($withoutChunkCounts.saved_overworld_chunks) after baseline (+$($withoutChunkCounts.saved_overworld_chunks - $seedChunkCounts.saved_overworld_chunks)), and $($withChunkCounts.saved_overworld_chunks) after UMCE (+$($withChunkCounts.saved_overworld_chunks - $seedChunkCounts.saved_overworld_chunks)).")
$csvRelative = [System.IO.Path]::GetRelativePath($root, $csv).Replace('\', '/')
$lines.Add("Per-window data is in [$csvRelative]($csvRelative); only completed /tick query responses are included in the summary. The adjacent run logs show each connected bot, observed online player count, and server overload messages. Entity totals are verified from the generated world files before either test condition starts.")
$lines.Add('')
$lines.Add("UMCE currently records timings but enables no gameplay optimizations. Conditions alternate order across pairs ($Repeats pair(s)); both start from copies of the same saved seed world. Differences are descriptive measurements of diagnostics overhead, not optimization gains. At least three valid pairs are recommended before interpreting small differences.")
$lines | Set-Content -LiteralPath $report -Encoding utf8
Write-Output "Stress comparison saved: $report"
