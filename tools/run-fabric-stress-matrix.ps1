[CmdletBinding()]
param(
    [ValidateRange(10, 3600)][int]$MeasureSeconds = 30,
    [ValidateRange(1, 10)][int]$Repeats = 2,
    [string]$InitialHeap = '1G',
    [string]$MaximumHeap = '4G',
    [string[]]$Only = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$matrix = @(
    @{ Name = 'entity-heavy'; Players = 0; Entities = 10000; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'villager-heavy'; Players = 0; Entities = 0; Villagers = 500; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'ai-heavy'; Players = 0; Entities = 2000; Villagers = 500; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'hopper-heavy'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 16; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'redstone-heavy'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 16; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'worldgen-heavy'; Players = 20; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 0 },
    @{ Name = 'chunk-io-heavy'; Players = 10; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 15 },
    @{ Name = 'network-heavy'; Players = 100; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'collision-heavy'; Players = 50; Entities = 10000; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 0 },
    @{ Name = 'explosion-100'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 100; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'explosion-1000'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 1000; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'explosion-10000'; Players = 0; Entities = 0; Villagers = 0; HopperRows = 0; RedstoneClockPairs = 0; TntCount = 10000; IdleClients = $true; SaveAllIntervalSeconds = 0 },
    @{ Name = 'mixed-heavy'; Players = 100; Entities = 10000; Villagers = 500; HopperRows = 16; RedstoneClockPairs = 8; TntCount = 0; IdleClients = $false; SaveAllIntervalSeconds = 0 }
)

if ($Only.Count -gt 0) {
    $unknown = @($Only | Where-Object { $_ -notin @($matrix | ForEach-Object Name) })
    if ($unknown.Count -gt 0) { throw "Unknown scenario(s): $($unknown -join ', ')" }
    $matrix = @($matrix | Where-Object { $_.Name -in $Only })
}

$benchmarkScript = Join-Path $PSScriptRoot 'benchmark-fabric-stress-1.21.1.ps1'
$outputRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'benchmark-results'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$rows = [Collections.Generic.List[object]]::new()

foreach ($scenario in $matrix) {
    Write-Host "=== $($scenario.Name): $Repeats paired run(s), $MeasureSeconds seconds per condition ==="
    $parameters = @{
        Players = $scenario.Players
        Entities = $scenario.Entities
        Villagers = $scenario.Villagers
        HopperRows = $scenario.HopperRows
        RedstoneClockPairs = $scenario.RedstoneClockPairs
        TntCount = $scenario.TntCount
        IdleClients = $scenario.IdleClients
        SaveAllIntervalSeconds = $scenario.SaveAllIntervalSeconds
        MeasureSeconds = $MeasureSeconds
        Repeats = $Repeats
        InitialHeap = $InitialHeap
        MaximumHeap = $MaximumHeap
        ResultTag = $scenario.Name
    }
    try {
        & $benchmarkScript @parameters
        $rows.Add([pscustomobject]@{ Scenario = $scenario.Name; Status = 'completed'; Report = "$stamp-1.21.1-stress-$($scenario.Name)-comparison.md" })
    } catch {
        $rows.Add([pscustomobject]@{ Scenario = $scenario.Name; Status = "failed: $($_.Exception.Message)"; Report = "$stamp-1.21.1-stress-$($scenario.Name)-comparison.md" })
        Write-Warning "Scenario $($scenario.Name) failed; continuing. $($_.Exception.Message)"
    }
}

$reportName = if ($Only.Count -gt 0) {
    "$stamp-1.21.1-stress-matrix-selected.md"
} else {
    "$stamp-1.21.1-stress-matrix.md"
}
$report = Join-Path $outputRoot $reportName
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# UMCE Fabric 1.21.1 stress matrix')
$lines.Add('')
$lines.Add("Captured: $([DateTimeOffset]::UtcNow.ToString('u'))")
$lines.Add("Heap: -Xms$InitialHeap -Xmx$MaximumHeap. Warmup: 20 seconds per server. Measured interval: $MeasureSeconds seconds per condition. Repeats: $Repeats paired run(s), alternating run order. Each scenario runs without UMCE and with UMCE; Fabric API, Java, server properties, seed, clients and scenario are otherwise held constant.")
$lines.Add('')
$lines.Add('| Scenario | Status | Per-scenario report |')
$lines.Add('|---|---|---|')
foreach ($row in $rows) {
    $lines.Add("| $($row.Scenario) | $($row.Status) | [$($row.Report)]($($row.Report)) |")
}
$lines.Add('')
$lines.Add('These runs benchmark the current diagnostics-only adapter; UMCE does not currently ship gameplay optimization patches. Results therefore measure overhead, not optimization gains. Tick and CPU/RAM metrics are reported by each per-scenario report. Allocation rate, GC pause totals, and network byte counts are not available from this harness and are left unclaimed.')
$lines | Set-Content -LiteralPath $report -Encoding utf8
Write-Output "Stress matrix summary saved: $report"
