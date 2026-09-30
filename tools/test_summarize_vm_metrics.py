import unittest
from summarize_jfr import timestamp
from summarize_vm_metrics import summarize


class VmSummaryTest(unittest.TestCase):
    def rows(self):
        return [{"time_utc": f"2026-09-29T00:00:{second:02d}Z", "observed_allocated_bytes": str(second * 10000),
                 "server_thread_allocated_bytes": str(second * 100), "gc_collections": str(second // 10),
                 "gc_collection_time_ms": str(second // 10 * 8)} for second in range(1, 21)]

    def test_excludes_startup_and_uses_actual_counter_subwindow(self):
        result = summarize(self.rows(), timestamp("2026-09-29T00:00:10.5Z"), timestamp("2026-09-29T00:00:19.5Z"))
        self.assertEqual(8, result["counter_interval_seconds"])
        self.assertEqual(800, result["server_allocated_bytes"])
        self.assertEqual(0, result["gc_collections"])
        self.assertEqual(0, result["gc_collection_time_ms"])

    def test_missing_counters_are_unknown_and_resets_or_short_captures_are_rejected(self):
        rows = self.rows()
        rows[-1]["server_thread_allocated_bytes"] = "-1"
        result = summarize(rows, timestamp("2026-09-29T00:00:10Z"), timestamp("2026-09-29T00:00:20Z"))
        self.assertIsNone(result["server_allocated_bytes"])
        rows[-1]["gc_collections"] = "0"
        with self.assertRaises(ValueError):
            summarize(rows, timestamp("2026-09-29T00:00:10Z"), timestamp("2026-09-29T00:00:20Z"))
        with self.assertRaises(ValueError):
            summarize(rows[:1], timestamp("2026-09-29T00:00:00Z"), timestamp("2026-09-29T00:00:20Z"))


if __name__ == "__main__":
    unittest.main()
