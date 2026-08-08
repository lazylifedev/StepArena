import argparse
import datetime as dt
import json
from pathlib import Path


def latest_snapshot(device):
    snapshots = device.get("snapshots", [])
    return snapshots[0] if snapshots else {}


def latest_error(device):
    for event in device.get("events", []):
        if event.get("type", "").endswith("ERROR") or event.get("data", {}).get("failure"):
            return event
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    source = json.loads(Path(args.input).read_text(encoding="utf-8"))
    output = Path(args.output)
    latest = output / "latest"
    latest.mkdir(parents=True, exist_ok=True)
    devices = source.get("devices", {})
    rows = []
    alerts = []
    for alias, device in sorted(devices.items()):
        snapshot = latest_snapshot(device)
        data = snapshot.get("data", snapshot)
        rows.append(
            f"- {alias}: steps={data.get('daily_steps', 'unknown')}, "
            f"eligible={data.get('eligible', 'unknown')}, "
            f"tracking={data.get('tracking_state', 'unknown')}"
        )
        error = latest_error(device)
        if error:
            alerts.append(f"- {alias}: latest error event={error.get('type', 'unknown')}")
        (latest / f"{alias.lower()}.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    generated = source.get("generatedAt", dt.datetime.now(dt.timezone.utc).isoformat())
    (latest / "overview.md").write_text(
        "# QA telemetry overview\n\n"
        f"Generated: {generated}\n\n"
        "## Devices\n" + ("\n".join(rows) or "- No device data") + "\n\n"
        "## Alerts\n" + ("\n".join(alerts) or "- No current alert candidates") + "\n",
        encoding="utf-8",
    )
    (latest / "alerts.md").write_text("# Alerts\n\n" + ("\n".join(alerts) or "- No current alert candidates") + "\n", encoding="utf-8")
    (latest / "active_challenge.md").write_text("# Active challenge\n\nChallenge details are only reported when QA telemetry provides a sanitized state event.\n", encoding="utf-8")
    (output / "README.md").write_text(
        "# StepArena QA Telemetry\n\n"
        "This private repository contains sanitized QA diagnostics from `steparena-dev`.\n"
        "It must remain private. Raw UID, email, challenge ID, tokens, credentials, location, and Wi-Fi identifiers are excluded.\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
