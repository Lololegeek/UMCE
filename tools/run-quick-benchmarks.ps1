[CmdletBinding()]
param(
    [ValidateRange(1, 10)][int]$MaxMinutes = 10,
    [ValidateRange(1, 30)][int]$MeasureSeconds = 5,
    [ValidateRange(0, 30)][int]$WarmupSeconds = 2,
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot',
    [string]$GradleUserHome = 'C:\gradle-cache-umce',
    [string]$UmceJar = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$benchmarkScript = Join-Path $PSScriptRoot 'benchmark-fabric-stress-1.21.1.ps1'
$outputRoot = Join-Path $root 'benchmark-results'
$benchmarkRoot = Join-Path $root 'build\benchmark-1.21.1'
$tag = Get-Date -Format 'yyyyMMdd-HHmmss'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$budgetSeconds = $MaxMinutes * 60
$timer = [Diagnostics.Stopwatch]::StartNew()
$measureBudget = [Math]::Max(3, $MeasureSeconds)
$warmupBudget = $WarmupSeconds
$jar = if ([string]::IsNullOrWhiteSpace($UmceJar)) {
    Join-Path $root 'platforms\fabric-1.21.1\build\libs\umce-fabric-1.21.1-0.1.0-alpha.2.jar'
} else {
    (Resolve-Path -LiteralPath $UmceJar).Path
}
$serverTemplate = Join-Path $benchmarkRoot 'server-template\fabric-server-launch.jar'
$apiRoot = Join-Path $GradleUserHome 'caches\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-api\0.116.17+1.21.1'
$apiCached = Test-Path -LiteralPath $apiRoot
$mineflayerCached = Test-Path -LiteralPath (Join-Path $benchmarkRoot 'client-deps\node_modules\mineflayer\package.json')

if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw "Java 21 introuvable: $JavaHome" }
if (-not (Test-Path -LiteralPath $jar)) { throw "JAR UMCE absent: $jar. Compile d'abord :platforms:fabric-1.21.1:jar." }
if (-not (Test-Path -LiteralPath $serverTemplate)) { throw "Serveur Fabric de bench absent: $serverTemplate. Lance d'abord un bench complet pour préparer le cache." }
if (-not $apiCached) { throw "Fabric API non en cache Gradle: $apiRoot. Lance d'abord un bench complet pour préparer le cache." }
if (-not $mineflayerCached -and -not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'npm.cmd est nécessaire pour installer les clients Mineflayer.' }
if (-not (Get-Command node.exe -ErrorAction SilentlyContinue)) { throw 'node.exe est nécessaire pour les clients Minecraft simulés.' }
if (-not (Get-Command python.exe -ErrorAction SilentlyContinue)) { throw 'python.exe est nécessaire pour valider les mondes du bench.' }
New-Item -ItemType Directory -Force $outputRoot | Out-Null

# This is a rapid, scaled comparison of every workload family in the full matrix.
# It is a smoke/performance screen, not a replacement for the 10k-entity full-fidelity runs.
$scenarios = @(
    @{ Name = 'entity'; Players = 0; Entities = 2000; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $true },
    @{ Name = 'villagers'; Players = 0; Entities = 0; Villagers = 150; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'ai-mix'; Players = 0; Entities = 500; Villagers = 100; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $true },
    @{ Name = 'hoppers'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 4; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'redstone'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 4; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'worldgen'; Players = 4; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'chunk-io'; Players = 2; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 4; Patch = $false },
    @{ Name = 'network'; Players = 20; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'collision'; Players = 10; Entities = 2000; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 0; Patch = $true },
    @{ Name = 'tnt-100'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 100; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'tnt-1000'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 1000; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'tnt-quick-large'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 2000; IdleClients = $true; SaveAllIntervalSeconds = 0; Patch = $false },
    @{ Name = 'mixed'; Players = 20; Entities = 2000; Villagers = 150; HopperRows = 4; RedstoneClockPairs = 2; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 0; Patch = $true }
)
$scenarioOrder = @('entity', 'collision', 'network', 'mixed', 'worldgen', 'chunk-io', 'ai-mix', 'villagers', 'hoppers', 'redstone', 'tnt-1000', 'tnt-quick-large', 'tnt-100')
$scenarios = @($scenarioOrder | ForEach-Object { $name = $_; $scenarios | Where-Object Name -eq $name })

function Quote-ProcessArgument([string]$Value) {
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Get-Median([double[]]$Values) {
    if ($Values.Count -eq 0) { return [double]::NaN }
    $sorted = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

function Get-Mean($Rows, [string]$Property) {
    $values = @($Rows | ForEach-Object { ConvertTo-BenchNumber $_.$Property })
    if ($values.Count -eq 0) { return [double]::NaN }
    return ($values | Measure-Object -Average).Average
}

function ConvertTo-BenchNumber([object]$Value) {
    if ($null -eq $Value -or "$Value".Trim() -eq '') { return [double]::NaN }
    $normalized = "$Value".Trim().Replace(',', '.')
    $parsed = 0.0
    if ([double]::TryParse($normalized, [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) { return $parsed }
    return [double]::NaN
}

function Format-Number([double]$Value, [int]$Digits = 2) {
    if ([double]::IsNaN($Value) -or [double]::IsInfinity($Value)) { return 'n/a' }
    return $Value.ToString("F$Digits", [Globalization.CultureInfo]::InvariantCulture)
}

function New-PartialRow($Scenario, [string]$Workload, [string]$CsvPath, [string]$FallbackStatus) {
    $samples = @()
    if (Test-Path -LiteralPath $CsvPath) { $samples = @(Import-Csv -LiteralPath $CsvPath) }
    $base = @($samples | Where-Object condition -eq 'without-umce')
    $with = @($samples | Where-Object condition -eq 'with-umce')
    $baseMspt = Get-Median ([double[]]@($base | ForEach-Object { ConvertTo-BenchNumber $_.tick_mean_ms }))
    $withMspt = Get-Median ([double[]]@($with | ForEach-Object { ConvertTo-BenchNumber $_.tick_mean_ms }))
    $delta = if ($baseMspt -gt 0 -and -not [double]::IsNaN($withMspt)) { (($withMspt - $baseMspt) / $baseMspt) * 100.0 } else { [double]::NaN }
    $status = if ($samples.Count -gt 0) { "partial; $($base.Count) baseline / $($with.Count) UMCE samples" } else { $FallbackStatus }
    return [pscustomobject]@{
        Scenario = $Scenario.Name; Workload = $Workload; Status = $status
        BaselineMspt = Format-Number $baseMspt 3; UmceMspt = Format-Number $withMspt 3; Delta = if ([double]::IsNaN($delta)) { 'n/a' } else { "$(Format-Number $delta 2)%" }
        BaselineP95 = Format-Number (Get-Mean $base 'tick_p95_ms') 3; UmceP95 = Format-Number (Get-Mean $with 'tick_p95_ms') 3
        BaselineP99 = Format-Number (Get-Mean $base 'tick_p99_ms') 3; UmceP99 = Format-Number (Get-Mean $with 'tick_p99_ms') 3
        BaselineCpu = Format-Number (Get-Mean $base 'process_cpu_percent_one_core') 1; UmceCpu = Format-Number (Get-Mean $with 'process_cpu_percent_one_core') 1
        BaselineRam = Format-Number ((Get-Mean $base 'working_set_bytes') / 1MB) 1; UmceRam = Format-Number ((Get-Mean $with 'working_set_bytes') / 1MB) 1
    }
}

$rows = [Collections.Generic.List[object]]::new()
foreach ($scenario in $scenarios) {
    $rows.Add([pscustomobject]@{ Scenario = $scenario.Name; Workload = ''; Status = 'pending'; BaselineMspt = 'n/a'; UmceMspt = 'n/a'; Delta = 'n/a'; BaselineP95 = 'n/a'; UmceP95 = 'n/a'; BaselineP99 = 'n/a'; UmceP99 = 'n/a'; BaselineCpu = 'n/a'; UmceCpu = 'n/a'; BaselineRam = 'n/a'; UmceRam = 'n/a' })
}
$reportPath = Join-Path $outputRoot "$stamp-1.21.1-quick-bench-$tag.md"

function Write-QuickSummary {
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('# UMCE quick benchmark matrix — Fabric 1.21.1')
    $lines.Add('')
    $lines.Add("Updated: $([DateTimeOffset]::UtcNow.ToString('u')); elapsed: $([Math]::Round($timer.Elapsed.TotalSeconds, 1))s / limit $budgetSeconds s.")
    $lines.Add("Rapid scaled screen. Each completed row uses one baseline/UMCE pair, $warmupBudget s warmup, $measureBudget s measurement, 1G/2G JVM heap, and identical copied seed worlds. Entity, player, villager, hopper, redstone, chunk I/O, worldgen, networking, collision, TNT, and mixed workload families are represented; exact dimensions are in the workload column. Rows with an enabled entity patch explicitly say so; other UMCE runs use SAFE mode.")
    $lines.Add('')
    $lines.Add('| Workload | Scenario size | Result | Baseline MSPT | UMCE MSPT | Change | Baseline P95 | UMCE P95 | Baseline P99 | UMCE P99 | CPU baseline / UMCE (% one core) | RAM baseline / UMCE (MiB) |')
    $lines.Add('|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|')
    foreach ($row in $rows) {
        $lines.Add("| $($row.Scenario) | $($row.Workload) | $($row.Status) | $($row.BaselineMspt) | $($row.UmceMspt) | $($row.Delta) | $($row.BaselineP95) | $($row.UmceP95) | $($row.BaselineP99) | $($row.UmceP99) | $($row.BaselineCpu) / $($row.UmceCpu) | $($row.BaselineRam) / $($row.UmceRam) |")
    }
    $lines.Add('')
    $lines.Add('Positive MSPT change means slower with UMCE. This one-pair, shortened run is a screening result, not statistically strong evidence; use the full-fidelity paired matrix before accepting or rejecting a patch. The harness does not measure allocation rate. Individual logs and raw CSV files are saved beside this report.')
    $lines | Set-Content -LiteralPath $reportPath -Encoding utf8
}

$powershell = (Get-Process -Id $PID).Path
$culture = [Globalization.CultureInfo]::InvariantCulture
Write-QuickSummary

for ($index = 0; $index -lt $scenarios.Count; $index++) {
    $scenario = $scenarios[$index]
    $remaining = [Math]::Floor($budgetSeconds - $timer.Elapsed.TotalSeconds - 12)
    $scenariosLeft = $scenarios.Count - $index
    if ($remaining -lt 8) {
        for ($skip = $index; $skip -lt $scenarios.Count; $skip++) {
            $rows[$skip] = [pscustomobject]@{ Scenario = $scenarios[$skip].Name; Workload = ''; Status = "not run: $MaxMinutes min limit"; BaselineMspt = 'n/a'; UmceMspt = 'n/a'; Delta = 'n/a'; BaselineP95 = 'n/a'; UmceP95 = 'n/a'; BaselineP99 = 'n/a'; UmceP99 = 'n/a'; BaselineCpu = 'n/a'; UmceCpu = 'n/a'; BaselineRam = 'n/a'; UmceRam = 'n/a' }
        }
        Write-QuickSummary
        break
    }

    $scenarioBudget = [Math]::Min(110, [Math]::Max(20, [Math]::Floor($remaining / [Math]::Min(5, $scenariosLeft))))
    $resultTag = "quick-$tag-$($scenario.Name)"
    $csvPath = Join-Path $outputRoot "$stamp-1.21.1-stress-$resultTag-samples.csv"
    $outLog = Join-Path $outputRoot "$stamp-1.21.1-stress-$resultTag-run.log"
    $errLog = Join-Path $outputRoot "$stamp-1.21.1-stress-$resultTag-error.log"
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $benchmarkScript,
        '-JavaHome', $JavaHome, '-GradleUserHome', $GradleUserHome,
        '-Players', "$($scenario.Players)", '-Entities', "$($scenario.Entities)",
        '-Villagers', "$($scenario.Villagers)", '-HopperRows', "$($scenario.HopperRows)",
        '-RedstoneClockPairs', "$($scenario.RedstoneClockPairs)", '-TntCount', "$($scenario.TntCount)",
        '-SaveAllIntervalSeconds', "$($scenario.SaveAllIntervalSeconds)",
        '-MeasureSeconds', "$measureBudget", '-WarmupSeconds', "$warmupBudget",
        '-Repeats', '1', '-InitialHeap', '1G', '-MaximumHeap', '2G',
        '-UmceJar', $jar, '-ResultTag', $resultTag, '-QuickStartup'
    )
    if ($scenario.IdleClients) { $arguments += '-IdleClients' }
    if ($scenario.Patch) { $arguments += '-EnableSmallBoxSectionProbe' }
    $quotedArguments = ($arguments | ForEach-Object { Quote-ProcessArgument ([string]$_) }) -join ' '
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $powershell
    $startInfo.Arguments = $quotedArguments
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment['TEMP'] = 'C:\Temp\umce-gradle-lpt'
    $startInfo.Environment['TMP'] = 'C:\Temp\umce-gradle-lpt'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $scenarioTimer = [Diagnostics.Stopwatch]::StartNew()
    Write-Host ("[{0}/{1}] {2}: baseline vs UMCE{3}; budget {4}s" -f ($index + 1), $scenarios.Count, $scenario.Name, $(if ($scenario.Patch) { ' + entity patch' } else { ' SAFE' }), $scenarioBudget)
    while (-not $process.HasExited -and $scenarioTimer.Elapsed.TotalSeconds -lt $scenarioBudget -and $timer.Elapsed.TotalSeconds -lt ($budgetSeconds - 8)) {
        Start-Sleep -Milliseconds 200
        $process.Refresh()
    }
    $timedOut = -not $process.HasExited
    if ($timedOut) {
        try { $process.Kill($true) } catch { try { $process.Kill() } catch { } }
        $process.WaitForExit()
    }
    $stdoutTask.Wait()
    $stderrTask.Wait()
    [System.IO.File]::WriteAllText($outLog, $stdoutTask.Result)
    [System.IO.File]::WriteAllText($errLog, $stderrTask.Result)
    $exitCode = if ($timedOut) { -1 } else { $process.ExitCode }
    $process.Dispose()

    $workload = "players=$($scenario.Players), entities=$($scenario.Entities), villagers=$($scenario.Villagers), hopperRows=$($scenario.HopperRows), redstone=$($scenario.RedstoneClockPairs), TNT=$($scenario.TntCount)"
    if ($timedOut) {
        $rows[$index] = New-PartialRow $scenario $workload $csvPath "timed out (${scenarioBudget}s)"
        Write-QuickSummary
        Write-Warning "Scenario $($scenario.Name) stopped at its time budget; remaining rows will be marked not run if the global limit is reached."
        continue
    }
    if ($exitCode -ne 0 -or -not (Test-Path -LiteralPath $csvPath)) {
        $rows[$index] = New-PartialRow $scenario $workload $csvPath "failed (exit $exitCode); see run log"
        Write-QuickSummary
        Write-Warning "Scenario $($scenario.Name) failed; see $outLog and $errLog."
        continue
    }

    $samples = @(Import-Csv -LiteralPath $csvPath)
    $base = @($samples | Where-Object condition -eq 'without-umce')
    $with = @($samples | Where-Object condition -eq 'with-umce')
    $baseMspt = Get-Median ([double[]]@($base | ForEach-Object { ConvertTo-BenchNumber $_.tick_mean_ms }))
    $withMspt = Get-Median ([double[]]@($with | ForEach-Object { ConvertTo-BenchNumber $_.tick_mean_ms }))
    $delta = if ($baseMspt -gt 0 -and -not [double]::IsNaN($withMspt)) { (($withMspt - $baseMspt) / $baseMspt) * 100.0 } else { [double]::NaN }
    $condition = if ($scenario.Patch) { 'UMCE + small-box-section-probe' } else { 'UMCE SAFE (no gameplay patch)' }
    $rows[$index] = [pscustomobject]@{
        Scenario = $scenario.Name; Workload = $workload; Status = "completed; $condition"
        BaselineMspt = Format-Number $baseMspt 3; UmceMspt = Format-Number $withMspt 3; Delta = "$(Format-Number $delta 2)%"
        BaselineP95 = Format-Number (Get-Mean $base 'tick_p95_ms') 3; UmceP95 = Format-Number (Get-Mean $with 'tick_p95_ms') 3
        BaselineP99 = Format-Number (Get-Mean $base 'tick_p99_ms') 3; UmceP99 = Format-Number (Get-Mean $with 'tick_p99_ms') 3
        BaselineCpu = Format-Number (Get-Mean $base 'process_cpu_percent_one_core') 1; UmceCpu = Format-Number (Get-Mean $with 'process_cpu_percent_one_core') 1
        BaselineRam = Format-Number ((Get-Mean $base 'working_set_bytes') / 1MB) 1; UmceRam = Format-Number ((Get-Mean $with 'working_set_bytes') / 1MB) 1
    }
    Write-QuickSummary
}

$timer.Stop()
$rows | Format-Table Scenario, Status, BaselineMspt, UmceMspt, Delta -AutoSize | Out-Host
Write-Output "Quick benchmark report: $reportPath"
