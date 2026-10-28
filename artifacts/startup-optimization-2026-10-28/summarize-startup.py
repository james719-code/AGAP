"""Compare the saved debug launch samples; these are not release benchmarks."""
import datetime as dt
import json
from pathlib import Path
import re
import statistics

root = Path(__file__).parent
timestamp = re.compile(r"^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})")


def read_sample(phase, number):
    path = root / f"{phase}-{number}.log"
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    first = route = main = None
    for line in text.splitlines():
        match = timestamp.match(line)
        if not match:
            continue
        when = dt.datetime.strptime("2026-" + match[1], "%Y-%m-%d %H:%M:%S.%f")
        first = first or when
        if "Splash resolved route=" in line:
            route = when
        if "MainActivity immediately rendering:" in line:
            main = when
    launch = (root / f"{phase}-{number}-launch.txt").read_text(encoding="utf-8-sig")
    android_launch = re.search(r"TotalTime:\s*(\d+)", launch)
    return {
        "sample": number,
        "process_log_to_main_render_request_ms": round((main - first).total_seconds() * 1000) if main and first else None,
        "route_resolved_to_main_render_request_ms": round((main - route).total_seconds() * 1000) if main and route else None,
        "android_splash_activity_launch_ms": int(android_launch[1]) if android_launch else None,
        "skipped_frame_warning_counts": [int(value) for value in re.findall(r"Skipped (\d+) frames", text)],
        "hardware_initializations": text.count("event=HARDWARE_CHECK"),
        "new_uplink_enqueues": text.count("event=UPLINK_WORK_ENQUEUE "),
        "signed_out_worker_skips": text.count("event=SYNC_WORKER_SKIPPED_SIGNED_OUT"),
        "fatal_exceptions": text.count("FATAL EXCEPTION"),
    }


result = {
    "scope": "Three sequential cold process launches per APK on one Android 15 phone; guest state retained, debug builds, six-second observation window after each SplashActivity launch.",
    "metric_limit": "Main render request is a log marker before Compose draws; Android TotalTime measures SplashActivity only. Neither is time until fully usable. Frame warnings are sparse and cannot establish a jank improvement.",
}
for phase in ("before", "after"):
    samples = [read_sample(phase, number) for number in range(1, 4)]
    result[phase] = {
        "samples": samples,
        "median_process_log_to_main_render_request_ms": statistics.median(
            sample["process_log_to_main_render_request_ms"] for sample in samples
            if sample["process_log_to_main_render_request_ms"] is not None
        ),
        "median_route_resolved_to_main_render_request_ms": statistics.median(
            sample["route_resolved_to_main_render_request_ms"] for sample in samples
            if sample["route_resolved_to_main_render_request_ms"] is not None
        ),
    }
print(json.dumps(result, indent=2))
