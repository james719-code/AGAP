import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from analyze_bluetooth_log import analyze


class BluetoothLogAnalysisTest(unittest.TestCase):
    def test_no_peer_log_does_not_report_zero_latency_as_a_measurement(self):
        result = analyze(["10-28 17:48:11.394 3107 3107 E AGAP_BLE: event=SCAN_FAILED code=1 reason=ALREADY_STARTED"])
        self.assertEqual(0, result["completed_transfers"])
        self.assertIsNone(result["transfer_timing_ms"]["GATT_RELAY_SUCCESS"])
        self.assertEqual(1, result["failures"]["SCAN_FAILED: ALREADY_STARTED"])

    def test_stage_timing_and_delivery_are_distinct_from_transport_ack(self):
        result = analyze([
            "D AGAP_BLE: event=GATT_TRANSFER_STAGE stage=DISCOVERING previousStage=CONNECTING elapsedMs=120 previousStageMs=120",
            "I AGAP_BLE: event=NEARBY_APP_ACK queueId=7 elapsedMs=80",
            "I AGAP_BLE: event=NEARBY_RELAY_SUCCESS queueId=7 elapsedMs=85 persistenceMs=5",
        ])
        self.assertEqual(1, result["completed_transfers"])
        self.assertEqual(85, result["transfer_timing_ms"]["NEARBY_RELAY_SUCCESS"]["median_ms"])
        self.assertEqual(120, result["gatt_stage_timing_ms"]["CONNECTING"]["median_ms"])
        self.assertEqual(5, result["other_timing_ms"]["NEARBY_RELAY_SUCCESS.persistenceMs"]["median_ms"])

    def test_missing_legacy_and_invalid_timing_values_are_not_invented(self):
        result = analyze([
            "I AGAP_BLE: event=GATT_RELAY_SUCCESS queueId=1",
            "I AGAP_BLE: event=GATT_RELAY_SUCCESS elapsedMs=NaN",
            "I AGAP_BLE: event=GATT_RELAY_SUCCESS elapsedMs=-1",
            "W OtherApp: event=GATT_RELAY_SUCCESS elapsedMs=10",
        ])
        self.assertEqual(3, result["completed_transfers"])
        self.assertIsNone(result["transfer_timing_ms"]["GATT_RELAY_SUCCESS"])


if __name__ == "__main__":
    unittest.main()
