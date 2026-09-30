"""Summarize JFR allocation samples and GC pauses inside a benchmark's measured window."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import subprocess
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path


def timestamp(value: str) -> datetime:
    # Python 3.10 accepts only selected fractional lengths; PowerShell uses 7 and JFR up to 9.
    normalized = re.sub(r"(\d{2}:\d{2}:\d{2})\.(\d+)",
                        lambda match: match[1] + "." + match[2][:6].ljust(6, "0"), value)
    parsed = datetime.fromisoformat(normalized.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("A timezone is required for measurement timestamps")
    return parsed


def duration_seconds(value: str) -> float:
    match = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if not match or not any(match.groups()):
        raise ValueError(f"Unsupported JFR duration: {value}")
    hours, minutes, seconds = (float(part or 0) for part in match.groups())
    return hours * 3600 + minutes * 60 + seconds


def summarize_events(events: list[dict], start: datetime, end: datetime) -> dict:
    if end <= start:
        raise ValueError("Measurement end must be after start")
    weights: dict[str, int] = defaultdict(int)
    allocations = cycles = pauses = 0
    pause_seconds = 0.0
    for event in events:
        kind = event["type"]
        values = event["values"]
        at = timestamp(values["startTime"])
        if kind == "jdk.ObjectAllocationSample" and start <= at < end:
            weight = values.get("weight")
            if not isinstance(weight, int) or weight < 0:
                raise ValueError("Invalid allocation sample weight")
            class_name = (values.get("objectClass") or {}).get("name", "unknown")
            weights[class_name] += weight
            allocations += 1
        elif kind == "jdk.GarbageCollection" and start <= at < end:
            cycles += 1
        elif kind == "jdk.GCPhasePause":
            stop = at + timedelta(seconds=duration_seconds(values["duration"]))
            overlap = (min(end, stop) - max(start, at)).total_seconds()
            if overlap > 0:
                pause_seconds += overlap
                pauses += 1
    estimated_bytes = sum(weights.values()) if allocations else None
    return {
        "measurement_start_utc": start.isoformat(),
        "measurement_end_utc": end.isoformat(),
        "measurement_seconds": (end - start).total_seconds(),
        "allocation_sample_events": allocations,
        "allocation_sample_weight_bytes": estimated_bytes,
        "allocation_sample_weight_mib_per_second": None if estimated_bytes is None else
            estimated_bytes / 1024**2 / (end - start).total_seconds(),
        "gc_cycles_started_in_window": cycles,
        "gc_pause_events_overlapping_window": pauses,
        "gc_pause_ms_inside_window": pause_seconds * 1000,
        "largest_sampled_allocation_classes": [
            {"class": name, "sample_weight_bytes": weight}
            for name, weight in sorted(weights.items(), key=lambda entry: entry[1], reverse=True)[:15]
        ],
        "limits": "Allocation weights are sampled estimates, not exact allocated bytes. Missing allocation events are unknown, not zero. GC pause overlap is clipped to the measured window; zero means no recorded pause in that window.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recording", type=Path)
    parser.add_argument("--samples", required=True, type=Path)
    parser.add_argument("--condition", required=True)
    parser.add_argument("--repeat", required=True, type=int)
    parser.add_argument("--jfr", default=r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\jfr.exe")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.recording.stat().st_size <= 68:
        raise ValueError("JFR recording is empty or truncated")
    with args.recording.open("rb") as recording:
        if recording.read(4) != b"FLR\x00":
            raise ValueError("Not a JFR recording")
    with args.samples.open(encoding="utf-8-sig", newline="") as source:
        rows = [row for row in csv.DictReader(source)
                if row["condition"] == args.condition and int(row["repeat"]) == args.repeat]
    if not rows or any(row.get("jfr_recording_enabled", "").lower() != "true" for row in rows):
        raise ValueError("Matching rows from a JFR-enabled benchmark are required")
    starts = {row.get("measurement_start_utc") for row in rows}
    ends = {row.get("measurement_end_utc") for row in rows}
    if len(starts) != 1 or len(ends) != 1 or not next(iter(starts)) or not next(iter(ends)):
        raise ValueError("A single explicit measurement window is required")
    result = subprocess.run([args.jfr, "print", "--json", "--events",
                             "jdk.ObjectAllocationSample,jdk.GarbageCollection,jdk.GCPhasePause",
                             str(args.recording)], check=True, capture_output=True, text=True, encoding="utf-8")
    events = json.loads(result.stdout)["recording"]["events"]
    summary = summarize_events(events, timestamp(next(iter(starts))), timestamp(next(iter(ends))))
    summary.update({"condition": args.condition, "repeat": args.repeat,
                    "recording": args.recording.name,
                    "recording_sha256": hashlib.sha256(args.recording.read_bytes()).hexdigest()})
    args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in summary.items()
                      if key not in {"largest_sampled_allocation_classes", "limits"}}))


if __name__ == "__main__":
    main()
