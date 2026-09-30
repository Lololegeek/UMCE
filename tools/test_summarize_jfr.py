import unittest
from summarize_jfr import timestamp, duration_seconds, summarize_events


class JfrSummaryTest(unittest.TestCase):
    def test_filters_startup_allocations_and_clips_pause_overlap_without_double_counting_gc_duration(self):
        events = [
            {"type": "jdk.ObjectAllocationSample", "values": {
                "startTime": "2026-09-29T00:00:01Z", "weight": 99999}},
            {"type": "jdk.ObjectAllocationSample", "values": {
                "startTime": "2026-09-29T00:00:12Z", "weight": 1024,
                "objectClass": {"name": "example/List"}}},
            {"type": "jdk.GarbageCollection", "values": {
                "startTime": "2026-09-29T00:00:12Z", "duration": "PT5S"}},
            {"type": "jdk.GCPhasePause", "values": {
                "startTime": "2026-09-29T00:00:09.999Z", "duration": "PT0.003S"}},
        ]
        actual = summarize_events(events, timestamp("2026-09-29T00:00:10Z"), timestamp("2026-09-29T00:00:20Z"))
        self.assertEqual(1, actual["allocation_sample_events"])
        self.assertEqual(1024, actual["allocation_sample_weight_bytes"])
        self.assertEqual(1, actual["gc_cycles_started_in_window"])
        self.assertAlmostEqual(2.0, actual["gc_pause_ms_inside_window"])

    def test_no_samples_means_unknown_allocation_and_timezones_are_normalized(self):
        actual = summarize_events([], timestamp("2026-09-29T02:00:10+02:00"), timestamp("2026-09-29T00:00:20Z"))
        self.assertIsNone(actual["allocation_sample_weight_bytes"])
        self.assertEqual(10.0, actual["measurement_seconds"])
        self.assertAlmostEqual(3723.5, duration_seconds("PT1H2M3.5S"))
        with self.assertRaises(ValueError):
            timestamp("2026-09-29T00:00:10")
        with self.assertRaises(ValueError):
            duration_seconds("not a duration")


if __name__ == "__main__":
    unittest.main()
