import unittest
from audit_benchmark_world import nbt


class BenchmarkNbtTest(unittest.TestCase):
    def test_byte_payload_preserves_slot_numbers_and_signed_values(self):
        for raw, expected in ((0, 0), (1, 1), (4, 4), (127, 127), (255, -1), (128, -128)):
            value, offset = nbt.read_payload(bytes([raw]), 0, 1)
            self.assertEqual(expected, value)
            self.assertEqual(1, offset)


if __name__ == "__main__":
    unittest.main()
