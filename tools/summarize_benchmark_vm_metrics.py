"""Combine complete three-condition paired runs with their measured VM-counter captures."""
import argparse
import csv
import json
import re
import statistics
from collections import defaultdict
from pathlib import Path
from summarize_jfr import timestamp
from summarize_vm_metrics import summarize


def number(value):
    return float(value.replace(",", "."))


def median(values):
    return None if any(value is None for value in values) else statistics.median(values)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--samples", type=Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repeats", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,39}", args.tag) or not 1 <= args.repeats <= 10:
        raise ValueError("A valid benchmark tag and expected repeat count are required")
    conditions = ("without-umce", "umce-passive", "patch-enabled")
    groups = defaultdict(list)
    with args.samples.open(encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            groups[(row["condition"], int(row["repeat"]))].append(row)
    expected = {(condition, repeat) for condition in conditions for repeat in range(1, args.repeats + 1)}
    if set(groups) != expected:
        raise ValueError("Incomplete or unexpected paired conditions; partial captures are not a complete benchmark")
    hashes = {row["artifact_sha256"] for rows in groups.values() for row in rows if row["artifact_sha256"]}
    if len(hashes) != 1:
        raise ValueError("One immutable UMCE artifact is required")
    runs = []
    for condition in conditions:
        for repeat in range(1, args.repeats + 1):
            rows = groups[(condition, repeat)]
            if any(row.get("vm_metrics_enabled", "").lower() != "true" for row in rows):
                raise ValueError("Every compared condition must use the VM diagnostics")
            starts = {row["measurement_start_utc"] for row in rows}
            ends = {row["measurement_end_utc"] for row in rows}
            if len(starts) != 1 or len(ends) != 1:
                raise ValueError("Each run must identify a single measured window")
            capture = args.samples.parent / f"{condition}-{args.tag}-r{repeat}-vm-metrics.csv"
            with capture.open(encoding="utf-8-sig", newline="") as source:
                result = summarize(list(csv.DictReader(source)), timestamp(starts.pop()), timestamp(ends.pop()))
            result.update({"condition": condition, "repeat": repeat, "capture": capture.name,
                           "median_rolling_mspt": statistics.median(number(row["tick_mean_ms"]) for row in rows),
                           "mean_rolling_p95_ms": statistics.mean(number(row["tick_p95_ms"]) for row in rows),
                           "mean_rolling_p99_ms": statistics.mean(number(row["tick_p99_ms"]) for row in rows),
                           "cpu_percent_one_core": statistics.mean(number(row["process_cpu_percent_one_core"]) for row in rows),
                           "working_set_mib": statistics.mean(int(row["working_set_bytes"]) / 1024**2 for row in rows)})
            runs.append(result)
    keys = ("median_rolling_mspt", "mean_rolling_p95_ms", "mean_rolling_p99_ms", "cpu_percent_one_core",
            "working_set_mib", "server_allocation_mib_per_second", "gc_collections", "gc_collection_time_ms")
    aggregates = {condition: {key: median([run[key] for run in runs if run["condition"] == condition])
                              for key in keys} for condition in conditions}
    pairs = []
    for repeat in range(1, args.repeats + 1):
        passive = next(run for run in runs if run["condition"] == "umce-passive" and run["repeat"] == repeat)
        patch = next(run for run in runs if run["condition"] == "patch-enabled" and run["repeat"] == repeat)
        pairs.append({"repeat": repeat, "mspt_change_percent": (patch["median_rolling_mspt"] / passive["median_rolling_mspt"] - 1) * 100,
                      "server_allocation_change_mib_per_second": None if patch["server_allocation_mib_per_second"] is None
                      or passive["server_allocation_mib_per_second"] is None else
                      patch["server_allocation_mib_per_second"] - passive["server_allocation_mib_per_second"]})
    data = {"artifact_sha256": hashes.pop(), "repeats": args.repeats, "tag": args.tag,
            "aggregates_median_of_runs": aggregates, "paired_changes": pairs, "runs": runs,
            "limits": "Rolling tick windows overlap. Runs and paired cycles are the units of comparison. VM counters cover explicit sub-windows and monitoring is active in all conditions. Short screens do not establish significance or general compatibility."}
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    lines = ["# Timing and VM counters", "", f"Tag: `{args.tag}`; complete paired cycles: {args.repeats}.",
             f"UMCE SHA-256: `{data['artifact_sha256']}`", "",
             "Values are medians of per-run values. P95/P99 columns are medians of per-run rolling means.", "",
             "| Condition | MSPT ms | P95 ms | P99 ms | CPU % one core | Working set MiB | Server alloc MiB/s | GC collections | GC collection ms |",
             "|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for condition, values in aggregates.items():
        cells = ["unknown" if values[key] is None else f"{values[key]:.3f}" for key in keys]
        lines.append("| " + condition + " | " + " | ".join(cells) + " |")
    lines += ["", "## Paired changes against passive", "", "| Cycle | MSPT change % | Server allocation change MiB/s |",
              "|---:|---:|---:|"]
    for pair in pairs:
        allocation = pair["server_allocation_change_mib_per_second"]
        lines.append(f"| {pair['repeat']} | {pair['mspt_change_percent']:+.3f} | "
                     + ("unknown" if allocation is None else f"{allocation:+.3f}") + " |")
    lines += ["", data["limits"], "", "GC collection time is an MXBean total, not individual pause latency."
              " Full counter sub-window bounds and raw capture filenames are recorded in the adjacent JSON.", ""]
    args.output.with_suffix(".md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Complete VM comparison saved: {args.output}")


if __name__ == "__main__":
    main()
