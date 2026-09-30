# Optional VM diagnostics for paired benchmarks

Normal timing screens run without these diagnostics. `-CaptureVmMetrics` adds the same external Java agent to baseline, passive UMCE, and patch-enabled servers. It is a benchmark tool, not part of the mod, and installs no class transformer. Its one-second sampler reads HotSpot allocation and GC MXBeans, then writes CSV. The agent tracks only currently live thread IDs, with an 8,192-thread diagnostic limit.

Build the Java 21 agent before benchmarking:

```powershell
.\tools\build-vm-metrics-agent.ps1
```

Pass `-CaptureVmMetrics` to `benchmark-fabric-stress-1.21.1.ps1`. Timing sample CSVs record explicit measurement start/end times, instrumentation status, and the artifact hash. Each successful run preserves a separate `<condition>-<tag>-r<repeat>-vm-metrics.csv` capture. Missing captures fail rather than becoming zero-allocation results.

```powershell
python tools/summarize_vm_metrics.py <capture.csv> `
  --samples <timing-samples.csv> --condition patch-enabled --repeat 1 `
  --output <summary.json>
```

The summary uses only counters inside the measured window and reports its actual sub-window duration. Server-thread allocated bytes are counter differences for that sub-window. The all-thread observed total can miss threads born and terminated between samples. GC collection time is an MXBean total, not a precise measurement of individual stop-the-world pauses. Unsupported counters remain null. Monitoring overhead is present in every instrumented condition; compare instrumented conditions with each other, not directly against uninstrumented screens.

## JFR limitation observed on this host

The optional `-RecordJfr` mode starts a profile recording in each measured server and explicitly dumps it after timing. It requires a nonempty output. On the September 29 JDK 21 hopper diagnostic attempts, startup reported an active recording but both exit dumping and explicit `jcmd JFR.dump` produced zero bytes. The runs were rejected and their partial timing samples are retained. There are no valid JFR allocation-class or pause results from those attempts.

`summarize_jfr.py` is available for future valid recordings. It excludes startup using measured CSV windows, labels allocation weights as estimates, and clips GC pause events to the window. It does not manufacture a zero when allocation samples are absent. Its parser tests passed, but this host has not produced a usable recording for live validation.

## Interruption handling

Samples are checkpointed after completed conditions. A partial CSV from an interrupted cycle is not a completed paired comparison. Preserve it and start a new tagged batch; do not count a reused condition as another independent pair.
