"""Count entities in Minecraft Anvil entity region files using only Python stdlib."""

from __future__ import annotations

import argparse
import collections
import gzip
import json
import struct
import zlib
from pathlib import Path


def read_string(data: bytes, offset: int) -> tuple[str, int]:
    length = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    return data[offset : offset + length].decode("utf-8"), offset + length


def read_payload(data: bytes, offset: int, tag: int):
    if tag == 1:
        return struct.unpack_from(">b", data, offset)[0], offset + 1
    if tag == 2:
        return struct.unpack_from(">h", data, offset)[0], offset + 2
    if tag == 3:
        return struct.unpack_from(">i", data, offset)[0], offset + 4
    if tag == 4:
        return struct.unpack_from(">q", data, offset)[0], offset + 8
    if tag == 5:
        return struct.unpack_from(">f", data, offset)[0], offset + 4
    if tag == 6:
        return struct.unpack_from(">d", data, offset)[0], offset + 8
    if tag == 7:
        length = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        return None, offset + length
    if tag == 8:
        return read_string(data, offset)
    if tag == 9:
        element_tag = data[offset]
        length = struct.unpack_from(">i", data, offset + 1)[0]
        offset += 5
        values = []
        for _ in range(length):
            value, offset = read_payload(data, offset, element_tag)
            values.append(value)
        return values, offset
    if tag == 10:
        values = {}
        while data[offset] != 0:
            element_tag = data[offset]
            offset += 1
            name, offset = read_string(data, offset)
            value, offset = read_payload(data, offset, element_tag)
            values[name] = value
        return values, offset + 1
    if tag in (11, 12):
        length = struct.unpack_from(">i", data, offset)[0]
        width = 4 if tag == 11 else 8
        return None, offset + 4 + length * width
    raise ValueError(f"Unsupported NBT tag type: {tag}")


def entity_counts(world: Path) -> collections.Counter[str]:
    counts: collections.Counter[str] = collections.Counter()
    entity_directory = world / "entities"
    for region in entity_directory.glob("r.*.mca"):
        raw = region.read_bytes()
        for slot in range(1024):
            location = int.from_bytes(raw[slot * 4 : slot * 4 + 3], "big")
            if location == 0:
                continue
            start = location * 4096
            length = struct.unpack_from(">I", raw, start)[0]
            compression = raw[start + 4]
            chunk = raw[start + 5 : start + 4 + length]
            if compression == 1:
                chunk = gzip.decompress(chunk)
            elif compression == 2:
                chunk = zlib.decompress(chunk)
            elif compression != 3:
                raise ValueError(f"Unknown MCA compression type {compression} in {region}")
            _, offset = read_string(chunk, 1)
            root, _ = read_payload(chunk, offset, 10)
            for entity in root.get("Entities", []):
                counts[entity.get("id", "unknown")] += 1
    return counts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("world", type=Path)
    args = parser.parse_args()
    counts = entity_counts(args.world)
    pigs = counts["minecraft:pig"]
    villagers = counts["minecraft:villager"]
    tnt = counts["minecraft:tnt"]
    print(json.dumps({"pigs": pigs, "villagers": villagers, "tnt": tnt, "total": sum(counts.values())}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
