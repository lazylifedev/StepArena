import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import build_report  # noqa: E402


def source(devices=None):
    return {
        "schema_version": 1,
        "generated_at": "2026-08-09T00:00:00Z",
        "project": "steparena-dev",
        "since_hours": 168,
        "devices": devices or {},
    }


class QaTelemetryReportTests(unittest.TestCase):
    def run_builder(self, data):
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        input_path = root / "latest.json"
        output = root / "repo"
        input_path.write_text(json.dumps(data), encoding="utf-8")
        subprocess.run(
            [sys.executable, str(ROOT / "build_report.py"), "--input", str(input_path), "--output", str(output)],
            check=True,
        )
        return temp, output

    def test_empty_telemetry_and_poco_not_yet_seen(self):
        temp, output = self.run_builder(source({"SOV41_QA": {"events": [], "snapshots": []}}))
        try:
            report = json.loads((output / "latest" / "latest.json").read_text(encoding="utf-8"))
            self.assertEqual(report["devices_summary"]["POCO_X7_PRO_QA"]["status"], "not_received")
            self.assertEqual(report["devices_summary"]["POCO_X7_PRO_QA"]["telemetry_freshness"], "NOT_RECEIVED")
            self.assertEqual(report["alert_status"], "重大な異常なし")
            self.assertTrue((output / "latest" / "poco_x7_pro.md").exists())
            self.assertTrue((output / "daily" / "2026-08-09" / "poco_x7_pro.json").exists())
        finally:
            temp.cleanup()

    def test_sov41_alerts_and_required_fields(self):
        snapshots = [
            {"server_received_at": "2026-08-08T22:00:00Z", "data": {"daily_steps": 100, "official_steps": 10, "eligible": 20}},
            {"server_received_at": "2026-08-08T23:00:00Z", "data": {"daily_steps": 90, "official_steps": 10, "eligible": 20, "counter_reset": True, "sensor_delta": 200001}},
        ]
        events = [
            {"type": "LONG_GAP", "event_timestamp": "2026-08-08T21:00:00Z", "data": {}},
            {"type": "LONG_GAP", "event_timestamp": "2026-08-08T22:00:00Z", "data": {}},
            {"type": "SYNC_ERROR", "event_timestamp": "2026-08-08T23:00:00Z", "data": {"failure": True}},
        ]
        temp, output = self.run_builder(source({"SOV41_QA": {"events": events, "snapshots": snapshots}}))
        try:
            report = json.loads((output / "latest" / "latest.json").read_text(encoding="utf-8"))
            codes = {item["code"] for item in report["alerts"]}
            self.assertTrue({"STEPS_DECREASE", "OFFICIAL_ELIGIBLE_MISMATCH", "COUNTER_RESET", "SENSOR_DELTA_ABNORMAL", "LONG_GAP_REPEATED"}.issubset(codes))
            overview = (output / "latest" / "overview.md").read_text(encoding="utf-8")
            for field in ("generated_at", "last_seen", "official_steps", "competition_steps", "reward_steps", "pending_telemetry_count", "recent_error_count"):
                self.assertIn(field, overview)
        finally:
            temp.cleanup()

    def test_duplicate_build_is_byte_stable(self):
        temp = tempfile.TemporaryDirectory()
        try:
            root = Path(temp.name)
            input_path = root / "latest.json"
            output = root / "repo"
            input_path.write_text(json.dumps(source()), encoding="utf-8")
            command = [sys.executable, str(ROOT / "build_report.py"), "--input", str(input_path), "--output", str(output)]
            subprocess.run(command, check=True)
            first = {path.relative_to(output).as_posix(): path.read_bytes() for path in output.rglob("*") if path.is_file()}
            subprocess.run(command, check=True)
            second = {path.relative_to(output).as_posix(): path.read_bytes() for path in output.rglob("*") if path.is_file()}
            self.assertEqual(first, second)
        finally:
            temp.cleanup()

    def test_exporter_guard_and_explicit_staging_contract(self):
        script = (ROOT / "export_qa_telemetry.ps1").read_text(encoding="utf-8")
        self.assertIn("Global\\StepArena_QA_Telemetry_Export", script)
        self.assertIn("visibility is $visibility", script)
        self.assertIn("git -C $repo add -- $files", script)
        self.assertNotIn("git add .", script)
        self.assertNotIn("git add -A", script)
        self.assertIn("No telemetry change; no commit or push required.", script)


if __name__ == "__main__":
    unittest.main(verbosity=2)
