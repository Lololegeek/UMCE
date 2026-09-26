[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = 'C:\gradle-cache-umce',
    [ValidateRange(1, 10)][int]$Repeats = 3,
    [ValidateRange(5, 600)][int]$WarmupSeconds = 20,
    [ValidateRange(10, 3600)][int]$MeasureSeconds = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$benchmarkRoot = Join-Path $repositoryRoot 'build\benchmark-26.3'
$templateRoot = Join-Path $benchmarkRoot 'server-template'
$installerPath = Join-Path $benchmarkRoot 'fabric-installer-1.1.2.jar'
$artifact = Join-Path $repositoryRoot 'platforms\fabric-26.3\build\libs\umce-fabric-26.3-0.1.0-alpha.1.jar'
$apiRoot = Join-Path $GradleUserHome 'caches\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-api\0.161.0+26.3'
$apiJar = Get-ChildItem -LiteralPath $apiRoot -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$outputRoot = Join-Path $repositoryRoot 'benchmark-results'
$runRoot = Join-Path $benchmarkRoot 'runs'
$java = Join-Path $JavaHome 'bin\java.exe'
$serverPort = 25585

if (-not (Test-Path -LiteralPath $java)) { throw "JDK 25+ not found: $java" }
if (-not (Test-Path -LiteralPath $apiJar)) { throw "Fabric API 0.161.0+26.3 not found in Gradle cache: $apiRoot" }
if (-not (Test-Path -LiteralPath $artifact)) { throw "Build the UMCE artifact first: $artifact" }
New-Item -ItemType Directory -Force $outputRoot, $runRoot | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $templateRoot 'fabric-server-launch.jar'))) {
    New-Item -ItemType Directory -Force $templateRoot | Out-Null
    if (-not (Test-Path -LiteralPath $installerPath)) {
        Invoke-WebRequest 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar' -OutFile $installerPath
    }
    & $java -jar $installerPath server -mcversion 26.3 -loader 0.19.5 -dir $templateRoot -downloadMinecraft
    if ($LASTEXITCODE -ne 0) { throw 'Fabric 26.3 server installation failed.' }
}

function Write-ServerFiles([string]$Directory) {
    New-Item -ItemType Directory -Force (Join-Path $Directory 'mods') | Out-Null
    Copy-Item -LiteralPath $apiJar -Destination (Join-Path $Directory 'mods\fabric-api.jar') -Force
    Set-Content -LiteralPath (Join-Path $Directory 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $Directory 'server.properties') -Encoding ascii -Value @(
        'server-ip=127.0.0.1', "server-port=$serverPort", 'online-mode=false', 'max-players=1',
        'view-distance=3', 'simulation-distance=3', 'spawn-protection=0', 'level-name=world',
        'level-seed=1234567890123', 'motd=UMCE repeatable benchmark',
        'sync-chunk-writes=false'
    )
}

function Start-Server([string]$Directory, [string]$Label) {
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java
    $info.Arguments = '-Xms2G -Xmx2G -jar fabric-server-launch.jar nogui'
    $info.WorkingDirectory = $Directory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.Environment['JAVA_HOME'] = $JavaHome
    if ($env:JAVA_TOOL_OPTIONS) { $info.Environment['JAVA_TOOL_OPTIONS'] = $env:JAVA_TOOL_OPTIONS }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw "Could not start server ($Label)." }
    $outTask = $process.StandardOutput.ReadLineAsync()
    $errTask = $process.StandardError.ReadLineAsync()
    $logPath = Join-Path $outputRoot "$Label.log"
    Set-Content -LiteralPath $logPath -Value '' -Encoding utf8
    $ready = $false
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while (-not $ready -and $clock.Elapsed.TotalSeconds -lt 240) {
        foreach ($stream in @(@{ task = $outTask; kind = 'out' }, @{ task = $errTask; kind = 'err' })) {
            if ($stream.task.IsCompleted) {
                $line = $stream.task.GetAwaiter().GetResult()
                if ($null -ne $line) {
                    Add-Content -LiteralPath $logPath -Value $line -Encoding utf8
                    if ($line -match 'Done \([^)]*\)! For help') { $ready = $true }
                }
                if ($stream.kind -eq 'out') { $outTask = $process.StandardOutput.ReadLineAsync() }
                else { $errTask = $process.StandardError.ReadLineAsync() }
            }
        }
        if ($process.HasExited) { throw "Server $Label exited early. See $logPath" }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) { throw "Server $Label did not reach readiness. See $logPath" }
    return @{ process = $process; outTask = $outTask; errTask = $errTask; log = $logPath }
}

function Read-ServerOutput($Server, [int]$Milliseconds = 100) {
    foreach ($stream in @(@{ task = $Server.outTask; kind = 'out' }, @{ task = $Server.errTask; kind = 'err' })) {
        if ($stream.task.IsCompleted) {
            $line = $stream.task.GetAwaiter().GetResult()
            if ($null -ne $line) { Add-Content -LiteralPath $Server.log -Value $line -Encoding utf8 }
            if ($stream.kind -eq 'out') { $Server.outTask = $Server.process.StandardOutput.ReadLineAsync() }
            else { $Server.errTask = $Server.process.StandardError.ReadLineAsync() }
        }
    }
    Start-Sleep -Milliseconds $Milliseconds
}

function Stop-Server($Server) {
    try { $Server.process.StandardInput.WriteLine('stop'); $Server.process.StandardInput.Flush() } catch { }
    if (-not $Server.process.WaitForExit(30000)) { $Server.process.Kill($true) }
    while (-not $Server.outTask.IsCompleted -or -not $Server.errTask.IsCompleted) { Read-ServerOutput $Server 50 }
    $Server.process.Dispose()
}

function Measure-Condition([string]$Condition, [int]$Repeat, [string]$PristineWorld) {
    $label = "${Condition}-r$Repeat"
    $directory = Join-Path $runRoot $label
    if (Test-Path -LiteralPath $directory) { Remove-Item -LiteralPath $directory -Recurse -Force }
    New-Item -ItemType Directory -Force $directory | Out-Null
    Copy-Item -Path (Join-Path $templateRoot '*') -Destination $directory -Recurse -Force
    Write-ServerFiles $directory
    Copy-Item -LiteralPath $PristineWorld -Destination (Join-Path $directory 'world') -Recurse -Force
    if ($Condition -eq 'with-umce') { Copy-Item -LiteralPath $artifact -Destination (Join-Path $directory 'mods\umce.jar') }
    $server = Start-Server $directory $label
    try {
        $stopWarmup = [DateTimeOffset]::UtcNow.AddSeconds($WarmupSeconds)
        while ([DateTimeOffset]::UtcNow -lt $stopWarmup) { Read-ServerOutput $server 100 }
        $samples = [System.Collections.Generic.List[object]]::new()
        $stopMeasure = [DateTimeOffset]::UtcNow.AddSeconds($MeasureSeconds)
        $previousCpuMs = $server.process.TotalProcessorTime.TotalMilliseconds
        $previousSampleAt = [DateTimeOffset]::UtcNow
        while ([DateTimeOffset]::UtcNow -lt $stopMeasure) {
            $started = [DateTimeOffset]::UtcNow
            $lineCountBefore = @(Get-Content -LiteralPath $server.log).Count
            $server.process.StandardInput.WriteLine('tick query')
            $server.process.StandardInput.Flush()
            $responseDeadline = [DateTimeOffset]::UtcNow.AddSeconds(2)
            while ([DateTimeOffset]::UtcNow -lt $responseDeadline) { Read-ServerOutput $server 100 }
            $allLines = @(Get-Content -LiteralPath $server.log)
            $response = ($allLines | Select-Object -Skip $lineCountBefore) -join ' '
            $meanMatch = [regex]::Match($response, 'Average time per tick:\s*([0-9]+(?:\.[0-9]+)?)ms')
            $p50Match = [regex]::Match($response, 'P50:\s*([0-9]+(?:\.[0-9]+)?)ms')
            $p95Match = [regex]::Match($response, 'P95:\s*([0-9]+(?:\.[0-9]+)?)ms')
            $p99Match = [regex]::Match($response, 'P99:\s*([0-9]+(?:\.[0-9]+)?)ms')
            $countMatch = [regex]::Match($response, 'Sample:\s*([0-9]+)')
            $samples.Add([pscustomobject]@{
                condition = $Condition; repeat = $Repeat; time_utc = $started.ToString('o'); response = $response
                tick_mean_ms = if ($meanMatch.Success) { [double]$meanMatch.Groups[1].Value } else { $null }
                tick_p50_ms = if ($p50Match.Success) { [double]$p50Match.Groups[1].Value } else { $null }
                tick_p95_ms = if ($p95Match.Success) { [double]$p95Match.Groups[1].Value } else { $null }
                tick_p99_ms = if ($p99Match.Success) { [double]$p99Match.Groups[1].Value } else { $null }
                tick_sample_count = if ($countMatch.Success) { [int]$countMatch.Groups[1].Value } else { $null }
            })
            $cpu = $server.process.TotalProcessorTime.TotalMilliseconds
            $workingSet = $server.process.WorkingSet64
            $measuredAt = [DateTimeOffset]::UtcNow
            $wallDelta = [Math]::Max(1.0, ($measuredAt - $previousSampleAt).TotalMilliseconds)
            $samples[$samples.Count - 1] | Add-Member -NotePropertyName process_cpu_percent -NotePropertyValue ((($cpu - $previousCpuMs) / $wallDelta) * 100.0)
            $samples[$samples.Count - 1] | Add-Member -NotePropertyName working_set_bytes -NotePropertyValue $workingSet
            $previousCpuMs = $cpu
            $previousSampleAt = $measuredAt
            Read-ServerOutput $server 2800
        }
        $samples
    } finally { Stop-Server $server }
}

# Create one saved world once; each measured run starts from an identical copy.
$seedDirectory = Join-Path $runRoot 'seed-world'
if (Test-Path -LiteralPath $seedDirectory) { Remove-Item -LiteralPath $seedDirectory -Recurse -Force }
New-Item -ItemType Directory -Force $seedDirectory | Out-Null
Copy-Item -Path (Join-Path $templateRoot '*') -Destination $seedDirectory -Recurse -Force
Write-ServerFiles $seedDirectory
$seedServer = Start-Server $seedDirectory 'world-generation'
try {
    $seedEnd = [DateTimeOffset]::UtcNow.AddSeconds($WarmupSeconds)
    while ([DateTimeOffset]::UtcNow -lt $seedEnd) { Read-ServerOutput $seedServer 100 }
} finally { Stop-Server $seedServer }
$pristineWorld = Join-Path $seedDirectory 'world'
if (-not (Test-Path -LiteralPath $pristineWorld)) { throw 'Minecraft did not create the benchmark world.' }

$all = [System.Collections.Generic.List[object]]::new()
for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
    $order = if ($repeat % 2 -eq 1) { @('without-umce', 'with-umce') } else { @('with-umce', 'without-umce') }
    foreach ($condition in $order) {
        foreach ($sample in (Measure-Condition $condition $repeat $pristineWorld)) { $all.Add($sample) }
    }
}

$stamp = Get-Date -Format 'yyyy-MM-dd'
$csvPath = Join-Path $outputRoot "$stamp-server-tick-samples.csv"
$invariantCulture = [Globalization.CultureInfo]::InvariantCulture
$csvRows = foreach ($row in $all) {
    [pscustomobject][ordered]@{
        condition = $row.condition
        repeat = $row.repeat
        time_utc = $row.time_utc
        response = $row.response
        tick_mean_ms = if ($null -ne $row.tick_mean_ms) { ([double]$row.tick_mean_ms).ToString('R', $invariantCulture) } else { '' }
        tick_p50_ms = if ($null -ne $row.tick_p50_ms) { ([double]$row.tick_p50_ms).ToString('R', $invariantCulture) } else { '' }
        tick_p95_ms = if ($null -ne $row.tick_p95_ms) { ([double]$row.tick_p95_ms).ToString('R', $invariantCulture) } else { '' }
        tick_p99_ms = if ($null -ne $row.tick_p99_ms) { ([double]$row.tick_p99_ms).ToString('R', $invariantCulture) } else { '' }
        tick_sample_count = $row.tick_sample_count
        process_cpu_percent = ([double]$row.process_cpu_percent).ToString('R', $invariantCulture)
        working_set_bytes = $row.working_set_bytes
    }
}
$csvRows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8
$reportPath = Join-Path $outputRoot "$stamp-server-tick-comparison.md"
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# UMCE server comparison')
$lines.Add('')
$lines.Add("Captured: $([DateTimeOffset]::UtcNow.ToString('u'))")
$lines.Add('')
$lines.Add("Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25; fixed seed and a pristine identical save per run. The only mod difference is UMCE. Each run warms for $WarmupSeconds s and measures for $MeasureSeconds s; order alternates across $Repeats repeats. No player is connected, so this is an idle spawn-chunk server benchmark, not a simulated gameplay workload or a claim of optimization gains.")
$lines.Add('')
$lines.Add('| Condition | Queries (ticks) | Mean tick time ms | Mean P50 ms | Mean P95 ms | Mean P99 ms | Mean process CPU % | Mean working set MiB |')
$lines.Add('|---|---:|---:|---:|---:|---:|---:|---:|')
$summary = @{}
foreach ($condition in @('without-umce', 'with-umce')) {
    $rows = @($all | Where-Object condition -eq $condition)
    $meanTick = ($rows | Measure-Object -Property tick_mean_ms -Average).Average
    $p50Tick = ($rows | Measure-Object -Property tick_p50_ms -Average).Average
    $p95Tick = ($rows | Measure-Object -Property tick_p95_ms -Average).Average
    $p99Tick = ($rows | Measure-Object -Property tick_p99_ms -Average).Average
    $measuredTicks = ($rows | Measure-Object -Property tick_sample_count -Sum).Sum
    $cpuPct = ($rows | Measure-Object -Property process_cpu_percent -Average).Average
    $memoryMiB = (($rows | Measure-Object -Property working_set_bytes -Average).Average / 1MB)
    $summary[$condition] = @{ mean = $meanTick; p95 = $p95Tick; p99 = $p99Tick; cpu = $cpuPct; memory = $memoryMiB }
    $meanText = $meanTick.ToString('F3', $invariantCulture)
    $p50Text = $p50Tick.ToString('F3', $invariantCulture)
    $p95Text = $p95Tick.ToString('F3', $invariantCulture)
    $p99Text = $p99Tick.ToString('F3', $invariantCulture)
    $cpuText = $cpuPct.ToString('F2', $invariantCulture)
    $memoryText = $memoryMiB.ToString('F1', $invariantCulture)
    $lines.Add("| $condition | $($rows.Count) ($measuredTicks ticks) | $meanText | $p50Text | $p95Text | $p99Text | $cpuText | $memoryText |")
}
$lines.Add('')
$tickDelta = if ($summary['without-umce'].mean -gt 0) { 100.0 * ($summary['with-umce'].mean - $summary['without-umce'].mean) / $summary['without-umce'].mean } else { 0 }
$cpuDelta = if ($summary['without-umce'].cpu -gt 0) { 100.0 * ($summary['with-umce'].cpu - $summary['without-umce'].cpu) / $summary['without-umce'].cpu } else { 0 }
$memoryDelta = $summary['with-umce'].memory - $summary['without-umce'].memory
$tickDeltaText = $tickDelta.ToString('+0.0;-0.0;0.0', $invariantCulture)
$cpuDeltaText = $cpuDelta.ToString('+0.0;-0.0;0.0', $invariantCulture)
$memoryDeltaText = $memoryDelta.ToString('+0.0;-0.0;0.0', $invariantCulture)
$lines.Add("Change with UMCE: mean tick time ${tickDeltaText}%, process CPU ${cpuDeltaText}%, working set ${memoryDeltaText} MiB.")
$lines.Add('')
$lines.Add('Minecraft rounds each /tick query metric to 0.1 ms per 100-tick window. Differences below that display resolution are measurement noise, not evidence of an optimization.')
$lines.Add('')
$lines.Add('Raw tick query replies, timestamps, process CPU time, and process working-set readings are in the CSV. The command output format is retained verbatim so the aggregate parser can be audited.')
$lines | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Host "Benchmark complete: $reportPath"
Write-Host "Raw samples: $csvPath"
