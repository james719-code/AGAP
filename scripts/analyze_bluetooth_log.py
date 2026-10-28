"""Summarize structured AGAP_BLE events, leaving missing timings unknown."""
import argparse
from collections import Counter, defaultdict
import json
import math
from pathlib import Path
import re
import statistics


EVENT = re.compile(r"\bevent=([A-Z][A-Z0-9_]*)\b(.*)")
FIELD = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*)=([^\s]+)")


def numeric(value):
    try:
        number = float(value)
        return number if math.isfinite(number) and number >= 0 else None
    except (TypeError, ValueError):
        return None


def distribution(values):
    if not values:
        return None
    ordered = sorted(values)
    return {
        "samples": len(ordered),
        "min_ms": ordered[0],
        "median_ms": statistics.median(ordered),
        "p95_ms": ordered[math.ceil(0.95 * len(ordered)) - 1],
        "max_ms": ordered[-1],
    }


def analyze(lines):
    counts = Counter()
    failures = Counter()
    stages = defaultdict(list)
    durations = defaultdict(list)
    for line in lines:
        # Stack traces and unstructured Android/vendor messages are not events.
        if "AGAP_BLE" not in line:
            continue
        match = EVENT.search(line)
        if not match:
            continue
        event, rest = match.groups()
        fields = dict(FIELD.findall(rest))
        counts[event] += 1
        if event == "SCAN_FAILED" or event.endswith(("_FAILED", "_FAILURE", "_TIMEOUT", "_RETRY", "_FALLBACK")):
            failures[f"{event}: {fields.get('reason', fields.get('code', fields.get('errorType', 'unspecified')))}"] += 1
        elapsed = numeric(fields.get("elapsedMs"))
        if elapsed is not None and event in {"GATT_RELAY_SUCCESS", "NEARBY_RELAY_SUCCESS", "NEARBY_APP_ACK", "NEARBY_TX_FAILED", "GATT_RELAY_RETRY"}:
            durations[event].append(elapsed)
        if event == "GATT_TRANSFER_STAGE":
            stage_ms = numeric(fields.get("previousStageMs"))
            if stage_ms is not None:
                stages[fields.get("previousStage", "UNKNOWN")].append(stage_ms)
        for field in ("queueWaitMs", "processingMs", "queueAgeMs", "persistenceMs"):
            sample = numeric(fields.get(field))
            if sample is not None:
                durations[f"{event}.{field}"].append(sample)
    success_count = counts["GATT_RELAY_SUCCESS"] + counts["NEARBY_RELAY_SUCCESS"]
    return {
        "structured_events": sum(counts.values()),
        "completed_transfers": success_count,
        "real_speed_comparison_available": False,
        "note": "A before/after comparison requires matched real peer transfers; missing timing samples are unknown, not zero.",
        "events": dict(sorted(counts.items())),
        "failures": dict(failures.most_common()),
        "transfer_timing_ms": {name: distribution(durations.get(name, [])) for name in ("GATT_RELAY_SUCCESS", "NEARBY_RELAY_SUCCESS")},
        "other_timing_ms": {name: distribution(values) for name, values in sorted(durations.items()) if name not in {"GATT_RELAY_SUCCESS", "NEARBY_RELAY_SUCCESS"}},
        "gatt_stage_timing_ms": {name: distribution(values) for name, values in sorted(stages.items())},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    args = parser.parse_args()
    lines = (line for path in args.logs for line in path.read_text(encoding="utf-8-sig", errors="replace").splitlines())
    result = analyze(lines)
    result["files"] = [str(path) for path in args.logs]
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
