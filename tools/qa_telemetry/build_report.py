import argparse
import datetime as dt
import json
from pathlib import Path
from typing import Any


ALIASES = ("POCO_X7_PRO_QA", "SOV41_QA")
DISPLAY_NAMES = {
    "POCO_X7_PRO_QA": "poco_x7_pro",
    "SOV41_QA": "sov41",
}
UNREPORTED = "unreported_by_current_telemetry"


def as_number(value: Any) -> float | None:
    return value if isinstance(value, (int, float)) and not isinstance(value, bool) else None


def iso_epoch(value: Any) -> float | None:
    if not isinstance(value, str):
        return None
    try:
        return dt.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return None


def latest_record(records: list[dict[str, Any]]) -> dict[str, Any]:
    return max(records, key=lambda record: iso_epoch(
        record.get("server_received_at")
        or record.get("event_timestamp")
        or record.get("snapshot_timestamp")
        or ""
    ) or 0, default={})


def event_is_error(event: dict[str, Any]) -> bool:
    data = event.get("data", {})
    event_type = str(event.get("type", ""))
    return (
        event_type.endswith("ERROR")
        or bool(data.get("failure"))
        or bool(data.get("crash"))
        or data.get("listener_state") == "error"
        or str(data.get("error_category", "")).lower() not in ("", "none", "ok")
    )


def freshness(last_seen: str | None, generated_at: str) -> str:
    if not last_seen:
        return "NOT_RECEIVED"
    age_hours = max(0.0, (iso_epoch(generated_at) or 0) - (iso_epoch(last_seen) or 0)) / 3600
    if age_hours <= 2:
        return "FRESH"
    if age_hours <= 6:
        return "STALE"
    return "STOPPED"


def challenge_state(device: dict[str, Any]) -> dict[str, Any]:
    event = next((event for event in device.get("events", []) if event.get("type") == "CHALLENGE_STATE"), None)
    data = event.get("data", {}) if event else {}
    return {
        "status": data.get("challenge_state", "UNREPORTED"),
        "listener_state": data.get("listener_state", "UNREPORTED"),
        "self_competition_steps": data.get("self_competition_steps", UNREPORTED),
        "opponent_competition_steps": data.get("opponent_competition_steps", UNREPORTED),
        "self_reward_steps": data.get("self_reward_steps", UNREPORTED),
        "opponent_reward_steps": data.get("opponent_reward_steps", UNREPORTED),
        "started_at": data.get("challenge_started_at", UNREPORTED),
        "ends_at": data.get("challenge_ends_at", UNREPORTED),
        "source": "CHALLENGE_STATE telemetry" if event else UNREPORTED,
    }


def submit_result(device: dict[str, Any]) -> str:
    for event in device.get("events", []):
        data = event.get("data", {})
        for key in ("accepted", "duplicate", "stale", "failure"):
            if key in data:
                return str(data[key])
    return UNREPORTED


def device_last_seen(device: dict[str, Any]) -> str | None:
    records = device.get("events", []) + device.get("snapshots", [])
    latest = latest_record(records)
    return latest.get("server_received_at") or latest.get("event_timestamp") or latest.get("snapshot_timestamp")


def add_alert(alerts: list[dict[str, str]], alias: str, code: str, detail: str, severity: str = "warning") -> None:
    alerts.append({"alias": alias, "code": code, "severity": severity, "detail": detail})


def detect_alerts(alias: str, device: dict[str, Any], generated_at: str) -> list[dict[str, str]]:
    alerts: list[dict[str, str]] = []
    last_seen = device_last_seen(device)
    if last_seen and freshness(last_seen, generated_at) == "STOPPED":
        add_alert(alerts, alias, "TELEMETRY_STOPPED", f"last_seen={last_seen}", "critical")

    snapshots = sorted(device.get("snapshots", []), key=lambda record: iso_epoch(
        record.get("server_received_at") or record.get("snapshot_timestamp") or ""
    ) or 0)
    if len(snapshots) >= 2:
        previous = as_number(snapshots[-2].get("data", {}).get("daily_steps"))
        current = as_number(snapshots[-1].get("data", {}).get("daily_steps"))
        if previous is not None and current is not None and current < previous:
            add_alert(alerts, alias, "STEPS_DECREASE", f"daily_steps {previous} -> {current}")

    latest = latest_record(device.get("snapshots", []))
    data = latest.get("data", {})
    sensor_delta = as_number(data.get("sensor_delta"))
    if sensor_delta is not None and (sensor_delta < 0 or sensor_delta > 100000):
        add_alert(alerts, alias, "SENSOR_DELTA_ABNORMAL", f"sensor_delta={sensor_delta}")
    if data.get("counter_reset") is True:
        add_alert(alerts, alias, "COUNTER_RESET", "counter_reset=true")
    official = as_number(data.get("official_steps"))
    eligible = as_number(data.get("eligible"))
    if official is not None and eligible is not None and official != eligible:
        add_alert(alerts, alias, "OFFICIAL_ELIGIBLE_MISMATCH", f"official={official}, eligible={eligible}")

    long_gap_count = sum(
        1 for event in device.get("events", [])
        if "LONG_GAP" in str(event.get("type", "")) or as_number(event.get("data", {}).get("long_gap_increment"))
    )
    if long_gap_count >= 2:
        add_alert(alerts, alias, "LONG_GAP_REPEATED", f"count={long_gap_count}")

    challenge = challenge_state(device)
    if challenge["listener_state"] == "error":
        add_alert(alerts, alias, "PARTICIPANT_SYNC_MISMATCH", "challenge listener_state=error")

    error_events = [event for event in device.get("events", []) if event_is_error(event)]
    if error_events:
        add_alert(alerts, alias, "RECENT_ERRORS", f"count={len(error_events)}")
    for event in error_events:
        text = json.dumps(event.get("data", {}), ensure_ascii=False).lower()
        event_type = str(event.get("type", "")).lower()
        if "auth" in event_type or "auth" in text:
            add_alert(alerts, alias, "AUTH_FAILURE", "auth-related telemetry error")
        if "app_check" in event_type or "app check" in text or "appcheck" in text:
            add_alert(alerts, alias, "APP_CHECK_FAILURE", "App Check-related telemetry error")
        if bool(event.get("data", {}).get("crash")) or "fatal" in event_type or "crash" in event_type:
            add_alert(alerts, alias, "CRASH_FATAL", "crash/fatal telemetry reported", "critical")
    return alerts


def device_summary(alias: str, device: dict[str, Any], generated_at: str) -> dict[str, Any]:
    last_seen = device_last_seen(device)
    snapshot = latest_record(device.get("snapshots", []))
    data = snapshot.get("data", {})
    challenge = challenge_state(device)
    return {
        "alias": alias,
        "status": "received" if last_seen else "not_received",
        "last_seen": last_seen,
        "telemetry_freshness": freshness(last_seen, generated_at),
        "daily_steps": data.get("daily_steps", UNREPORTED),
        "sensor_counter": data.get("sensor_counter", UNREPORTED),
        "tracking_state": data.get("tracking_state", UNREPORTED),
        "unallocated_steps": data.get("unallocated_steps", UNREPORTED),
        "long_gap": data.get("long_gap_increment", UNREPORTED),
        "eligible": data.get("eligible", UNREPORTED),
        "restricted": data.get("restricted", UNREPORTED),
        "excluded": data.get("excluded", UNREPORTED),
        "official_steps": data.get("official_steps", UNREPORTED),
        "competition_steps": challenge["self_competition_steps"],
        "reward_steps": challenge["self_reward_steps"],
        "current_challenge": challenge["status"],
        "challenge_started_at": challenge["started_at"],
        "challenge_ends_at": challenge["ends_at"],
        "submit_official_progress_last_result": submit_result(device),
        "pending_telemetry_count": data.get("pending_telemetry_count", UNREPORTED),
        "recent_error_count": sum(1 for event in device.get("events", []) if event_is_error(event)),
        "challenge": challenge,
    }


def markdown_device(alias: str, summary: dict[str, Any]) -> str:
    lines = [f"# {alias}", "", f"- status: `{summary['status']}`", f"- last_seen: `{summary['last_seen'] or '未受信'}`"]
    for key in (
        "telemetry_freshness", "daily_steps", "sensor_counter", "tracking_state", "unallocated_steps",
        "long_gap", "eligible", "restricted", "excluded", "official_steps", "competition_steps",
        "reward_steps", "current_challenge", "challenge_started_at", "challenge_ends_at",
        "submit_official_progress_last_result", "pending_telemetry_count", "recent_error_count",
    ):
        lines.append(f"- {key}: `{summary[key]}`")
    return "\n".join(lines) + "\n"


def local_date(value: str | None) -> str | None:
    epoch = iso_epoch(value)
    if epoch is None:
        return None
    return dt.datetime.fromtimestamp(epoch, dt.timezone.utc).astimezone().date().isoformat()


def write_daily(output: Path, source: dict[str, Any], summaries: dict[str, dict[str, Any]]) -> None:
    dates = {local_date(source.get("generated_at"))} | {
        local_date(record.get("server_received_at") or record.get("event_timestamp") or record.get("snapshot_timestamp"))
        for device in source.get("devices", {}).values()
        for record in device.get("events", []) + device.get("snapshots", [])
    }
    for date in sorted(value for value in dates if value):
        target = output / "daily" / date
        target.mkdir(parents=True, exist_ok=True)
        daily_devices: dict[str, dict[str, Any]] = {}
        for alias in ALIASES:
            device = source.get("devices", {}).get(alias, {})
            daily_events = [event for event in device.get("events", []) if local_date(event.get("server_received_at") or event.get("event_timestamp")) == date]
            daily_snapshots = [snapshot for snapshot in device.get("snapshots", []) if local_date(snapshot.get("server_received_at") or snapshot.get("snapshot_timestamp")) == date]
            daily_devices[alias] = {"alias": alias, "events": daily_events, "snapshots": daily_snapshots}
            (target / f"{DISPLAY_NAMES[alias]}.json").write_text(json.dumps(daily_devices[alias], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        challenge_payload = {
            "date": date,
            "devices": {alias: summaries[alias]["challenge"] for alias in ALIASES},
        }
        (target / "challenges.json").write_text(json.dumps(challenge_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        summary_lines = [f"# QA telemetry summary {date}", "", f"generated_at: `{source.get('generated_at')}`", ""]
        for alias in ALIASES:
            summary_lines.append(f"## {alias}")
            summary_lines.append("")
            summary_lines.append(markdown_device(alias, summaries[alias]).split("\n", 1)[1])
        (target / "summary.md").write_text("\n".join(summary_lines).rstrip() + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    input_path = Path(args.input)
    output = Path(args.output)
    source = json.loads(input_path.read_text(encoding="utf-8"))
    generated_at = source.get("generated_at") or dt.datetime.now(dt.timezone.utc).isoformat()
    devices = source.setdefault("devices", {})
    alerts: list[dict[str, str]] = []
    summaries: dict[str, dict[str, Any]] = {}
    latest = output / "latest"
    latest.mkdir(parents=True, exist_ok=True)
    for alias in ALIASES:
        device = devices.setdefault(alias, {"alias": alias, "events": [], "snapshots": []})
        summaries[alias] = device_summary(alias, device, generated_at)
        alerts.extend(detect_alerts(alias, device, generated_at))
        (latest / f"{DISPLAY_NAMES[alias]}.md").write_text(markdown_device(alias, summaries[alias]), encoding="utf-8")

    source["alerts"] = alerts
    source["alert_status"] = "重大な異常なし" if not alerts else "異常候補あり"
    source["devices_summary"] = summaries
    (latest / "latest.json").write_text(json.dumps(source, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    overview_lines = ["# QA telemetry overview", "", f"- generated_at: `{generated_at}`", f"- project: `{source.get('project')}`", "", "## Devices", ""]
    for alias in ALIASES:
        overview_lines.append(f"### {alias}")
        overview_lines.append("")
        overview_lines.extend(markdown_device(alias, summaries[alias]).split("\n")[2:-1])
        overview_lines.append("")
    overview_lines.extend(["## Alerts", "", f"- status: `{source['alert_status']}`"])
    if alerts:
        overview_lines.extend(f"- `{item['severity']}` `{item['code']}` `{item['alias']}`: {item['detail']}" for item in alerts)
    else:
        overview_lines.append("- 重大な異常なし")
    (latest / "overview.md").write_text("\n".join(overview_lines).rstrip() + "\n", encoding="utf-8")

    challenge_payload = {"generated_at": generated_at, "devices": {alias: summaries[alias]["challenge"] for alias in ALIASES}}
    (latest / "active_challenge.md").write_text(
        "# Active challenge\n\n" + json.dumps(challenge_payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (latest / "alerts.md").write_text(
        "# Alerts\n\n" + ("重大な異常なし\n" if not alerts else "\n".join(
            f"- [{item['severity']}] {item['alias']}: {item['code']} - {item['detail']}" for item in alerts
        ) + "\n"),
        encoding="utf-8",
    )
    write_daily(output, source, summaries)
    (output / "README.md").write_text(
        "# StepArena QA Telemetry\n\n"
        "This private repository contains sanitized QA diagnostics read from `steparena-dev` using local Google ADC.\n\n"
        "Only the aliases `POCO_X7_PRO_QA` and `SOV41_QA` are exported. Raw UID, challenge ID, email, OAuth/App Check/refresh token, service-account key, verification code, Google account, Wi-Fi identifiers, and precise location are excluded.\n\n"
        "The repository must remain PRIVATE. The exporter refuses non-QA Firebase projects and uses a local lock so scheduled runs do not overlap.\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
