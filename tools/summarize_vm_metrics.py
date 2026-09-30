"""Summarize bounded-interval VM counters captured equally in all benchmark conditions."""
import argparse
import csv
import json
from pathlib import Path
from summarize_jfr import timestamp


def summarize(rows, start, end):
    selected = [row for row in rows if start <= timestamp(row["time_utc"]) <= end]
    if len(selected) < 2:
        raise ValueError("At least two VM samples inside the measurement window are required")
    first, last = selected[0], selected[-1]
    elapsed = (timestamp(last["time_utc"]) - timestamp(first["time_utc"])).total_seconds()
    if elapsed <= 0:
        raise ValueError("VM samples must advance in time")

    def delta(key):
        values = [int(row[key]) for row in selected]
        if any(value < 0 for value in values):
            return None
        if any(after < before for before, after in zip(values, values[1:])):
            raise ValueError(f"Counter reset in measured window: {key}")
        return values[-1] - values[0]

    server = delta("server_thread_allocated_bytes")
    observed = delta("observed_allocated_bytes")
    return {
        "counter_samples": len(selected), "counter_interval_seconds": elapsed,
        "counter_start_utc": first["time_utc"], "counter_end_utc": last["time_utc"],
        "server_allocated_bytes": server,
        "server_allocation_mib_per_second": None if server is None else server / 1024**2 / elapsed,
        "observed_all_threads_allocation_mib_per_second": None if observed is None else observed / 1024**2 / elapsed,
        "gc_collections": delta("gc_collections"), "gc_collection_time_ms": delta("gc_collection_time_ms"),
        "limits": "Counters span only the reported sub-window. All-thread allocations can miss threads born and terminated between samples. GC collection time is the MXBean counter, not individual pause latency. Diagnostic agent adds equal monitoring work to each condition.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--samples", type=Path, required=True)
    parser.add_argument("--condition", required=True)
    parser.add_argument("--repeat", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with args.samples.open(encoding="utf-8-sig", newline="") as source:
        samples = [row for row in csv.DictReader(source) if row["condition"] == args.condition and int(row["repeat"]) == args.repeat]
    if not samples or any(row.get("vm_metrics_enabled", "").lower() != "true" for row in samples):
        raise ValueError("Matching instrumented benchmark samples are required")
    starts = {row["measurement_start_utc"] for row in samples}
    ends = {row["measurement_end_utc"] for row in samples}
    if len(starts) != 1 or len(ends) != 1:
        raise ValueError("Benchmark rows must identify one measurement interval")
    with args.capture.open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source))
    result = summarize(rows, timestamp(starts.pop()), timestamp(ends.pop()))
    result.update({"condition": args.condition, "repeat": args.repeat, "capture": args.capture.name})
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "limits"}))


if __name__ == "__main__":
    main()
